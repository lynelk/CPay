package net.citotech.cito;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.GatewayChargeDetails;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.api.v2.dto.PaymentChannelResponse;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.billing.integration.cpay.PaymentUsageOutboxHook;
import net.citotech.cito.compliance.RiskDecisionService;
import net.citotech.cito.gateway.GatewayCapabilities;
import net.citotech.cito.gateway.LegacyGatewayAdapter;
import net.citotech.cito.gateway.PaymentChannelAdapter;
import net.citotech.cito.gateway.PaymentChannelRegistry;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import net.citotech.cito.metrics.GatewayMetrics;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PaymentOrchestrationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final PaymentChannelRegistry paymentChannelRegistry;
    private final RiskDecisionService riskDecisionService;
    private final DoubleEntryLedgerService ledgerService;
    private final MerchantWebhookService webhookService;
    private final GatewayMetrics gatewayMetrics;
    private final PaymentUsageOutboxHook paymentUsageOutboxHook;

    public PaymentOrchestrationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            PaymentChannelRegistry paymentChannelRegistry,
            RiskDecisionService riskDecisionService,
            DoubleEntryLedgerService ledgerService,
            MerchantWebhookService webhookService,
            GatewayMetrics gatewayMetrics,
            PaymentUsageOutboxHook paymentUsageOutboxHook) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.paymentChannelRegistry = paymentChannelRegistry;
        this.riskDecisionService = riskDecisionService;
        this.ledgerService = ledgerService;
        this.webhookService = webhookService;
        this.gatewayMetrics = gatewayMetrics;
        this.paymentUsageOutboxHook = paymentUsageOutboxHook;
    }

    public PaymentResult collect(
            PaymentRequest request, Merchant verifiedMerchant, String originateIp) {
        validatePaymentRequest(request, true);
        Merchant merchant =
                validateMerchant(
                        request.getMerchantNumber(),
                        verifiedMerchant,
                        Common.API_MOBILE_MONEY_PAYIN);
        riskDecisionService.authorizePayment(merchant, request, "COLLECT");
        String accountIdentifier = request.getPayer().getValue();
        String gatewayId = resolveLegacyGatewayId(request, accountIdentifier);
        PaymentChannelAdapter adapter = resolveAdapter(request, accountIdentifier, gatewayId);
        GatewayChargeDetails chargeDetails = getChargeDetails(gatewayId, merchant);
        gatewayMetrics.incrementTransactionInitiated(gatewayId, Transaction.TX_TYPE_PAYIN);

        MoneyAmount amountMoney = parseMoneyAmount(request.getAmount());
        Double amount = amountMoney.asLegacyDouble();
        Transaction tx = baseTransaction(request, merchant, gatewayId, originateIp, amount);
        tx.setPayer_number(accountIdentifier);
        tx.setTx_type(Transaction.TX_TYPE_PAYIN);
        tx.setCharging_method(chargeDetails.getCustomerInboundChargeMethod());
        tx.setCharges(DoPayGateway.getCustomerInboundCharges(amount, chargeDetails));
        tx.setTx_cost(DoPayGateway.getCostOfInboundCharges(amount, chargeDetails));

        String legacyResult;
        try {
            // Risk is authorized exactly once at the orchestration boundary. The legacy engine is
            // now an execution compatibility seam, not a second policy engine.
            legacyResult = Common.doPayIn(tx, merchant, jdbcTemplate, transactionManager, true);
        } catch (RuntimeException ex) {
            gatewayMetrics.incrementGatewayError(gatewayId);
            throw ex;
        }
        postLedgerEntries(
                "COLLECT",
                request,
                merchant,
                gatewayId,
                tx,
                amountMoney.asBigDecimal(),
                legacyMoney(tx.getCharges()));
        PaymentResult result = resultFromLegacy(request, tx, adapter, legacyResult);
        gatewayMetrics.incrementTransactionCompleted(
                gatewayId, Transaction.TX_TYPE_PAYIN, tx.getStatus());
        queueWebhook(merchant, "payment.pending", request, result);
        paymentUsageOutboxHook.recordPaymentCollected(merchant, request, tx);
        return result;
    }

    public PaymentResult payout(
            PaymentRequest request, Merchant verifiedMerchant, String originateIp) {
        validatePaymentRequest(request, false);
        Merchant merchant =
                validateMerchant(
                        request.getMerchantNumber(),
                        verifiedMerchant,
                        Common.API_MOBILE_MONEY_PAYOUT);
        riskDecisionService.authorizePayment(merchant, request, "PAYOUT");
        String accountIdentifier = request.getPayee().getValue();
        String gatewayId = resolveLegacyGatewayId(request, accountIdentifier);
        PaymentChannelAdapter adapter = resolveAdapter(request, accountIdentifier, gatewayId);
        GatewayChargeDetails chargeDetails = getChargeDetails(gatewayId, merchant);
        gatewayMetrics.incrementTransactionInitiated(gatewayId, Transaction.TX_TYPE_PAYOUT);

        MoneyAmount amountMoney = parseMoneyAmount(request.getAmount());
        Double amount = amountMoney.asLegacyDouble();
        Double legacyCharges = DoPayGateway.getCustomerOutboundCharges(amount, chargeDetails);
        BigDecimal chargeAmount = legacyMoney(legacyCharges);
        BigDecimal requiredAmount = amountMoney.asBigDecimal().add(chargeAmount);
        ensureMerchantHasAvailableBalance(merchant, gatewayId, requiredAmount);

        Transaction tx = baseTransaction(request, merchant, gatewayId, originateIp, amount);
        tx.setPayer_number(accountIdentifier);
        tx.setTx_type(Transaction.TX_TYPE_PAYOUT);
        tx.setCharging_method(chargeDetails.getCustomerOutboundChargeMethod());
        tx.setCharges(legacyCharges);
        tx.setTx_cost(DoPayGateway.getCostOfOutboundCharges(amount, chargeDetails));

        String reservationReference =
                "payout-reserve:" + merchant.getAccount_number() + ":" + request.getReference();
        ledgerService.reserve(
                reservationReference,
                merchant.getId(),
                request.getReference(),
                requiredAmount,
                request.getCurrency());
        try {
            String legacyResult =
                    Common.doPayOut(tx, merchant, jdbcTemplate, transactionManager, true);
            postLedgerEntries(
                    "PAYOUT",
                    request,
                    merchant,
                    gatewayId,
                    tx,
                    amountMoney.asBigDecimal(),
                    chargeAmount);
            ledgerService.captureReservation(reservationReference);
            PaymentResult result = resultFromLegacy(request, tx, adapter, legacyResult);
            gatewayMetrics.incrementTransactionCompleted(
                    gatewayId, Transaction.TX_TYPE_PAYOUT, tx.getStatus());
            queueWebhook(merchant, "payout.pending", request, result);
            paymentUsageOutboxHook.recordPaymentPayoutSubmitted(merchant, request, tx);
            return result;
        } catch (RuntimeException ex) {
            ledgerService.releaseReservation(reservationReference);
            gatewayMetrics.incrementGatewayError(gatewayId);
            throw ex;
        }
    }

    public List<PaymentChannelResponse> listChannels() {
        List<PaymentChannelResponse> responses = new ArrayList<>();
        for (PaymentChannelAdapter adapter : paymentChannelRegistry.getAdapters()) {
            GatewayCapabilities capabilities = adapter.capabilities();
            PaymentChannelResponse response = new PaymentChannelResponse();
            response.setChannelCode(adapter.channelCode());
            response.setDisplayName(adapter.displayName());
            response.setCountryCode(adapter.countryCode());
            response.setCurrencyCode(adapter.currencyCode());
            response.setCollections(capabilities.supportsCollections());
            response.setPayouts(capabilities.supportsPayouts());
            response.setBalanceCheck(capabilities.supportsBalanceCheck());
            response.setStatusCheck(capabilities.supportsStatusCheck());
            response.setRefunds(capabilities.supportsRefunds());
            response.setCallbacks(capabilities.supportsCallbacks());
            responses.add(response);
        }
        return responses;
    }

    public List<Balance> balances(String merchantNumber, Merchant verifiedMerchant) {
        Merchant merchant =
                validateMerchant(merchantNumber, verifiedMerchant, Common.API_BALANCE_CHECK);
        return Common.getMerchantBalances(String.valueOf(merchant.getId()), jdbcTemplate);
    }

    private Transaction baseTransaction(
            PaymentRequest request,
            Merchant merchant,
            String gatewayId,
            String originateIp,
            Double amount) {
        Transaction tx = new Transaction();
        tx.setGateway_id(gatewayId);
        tx.setOriginal_amount(amount);
        tx.setStatus("PENDING");
        tx.setMerchant_id(merchant.getId() + "");
        tx.setTx_description(merchant.getShort_name());
        tx.setTx_merchant_description(request.getDescription());
        tx.setTx_unique_id(Common.generateUuid());
        tx.setTx_merchant_ref(request.getReference());
        tx.setCallback_url(request.getCallbackUrl());
        tx.setOriginate_ip(originateIp);
        tx.setTx_request_trace("");
        tx.setTx_update_trace("");
        tx.setTx_gateway_ref("");
        return tx;
    }

    private PaymentResult resultFromLegacy(
            PaymentRequest request,
            Transaction tx,
            PaymentChannelAdapter adapter,
            String legacyResult) {
        PaymentResult result = new PaymentResult();
        result.setReference(request.getReference());
        result.setTransactionId(tx.getTx_unique_id());
        result.setStatus("SUBMITTED");
        result.setChannel(adapter.channelCode());
        result.setCurrency(request.getCurrency());
        result.setMessage("Transaction submitted through compatibility payment engine");
        result.setProviderResponse(legacyResult);
        return result;
    }

    private void queueWebhook(
            Merchant merchant, String eventType, PaymentRequest request, PaymentResult result) {
        try {
            String payload =
                    "{"
                            + "\"eventType\":\""
                            + json(eventType)
                            + "\","
                            + "\"merchantNumber\":\""
                            + json(merchant.getAccount_number())
                            + "\","
                            + "\"reference\":\""
                            + json(request.getReference())
                            + "\","
                            + "\"transactionId\":\""
                            + json(result.getTransactionId())
                            + "\","
                            + "\"status\":\""
                            + json(result.getStatus())
                            + "\","
                            + "\"amount\":\""
                            + json(request.getAmount())
                            + "\","
                            + "\"currency\":\""
                            + json(request.getCurrency())
                            + "\""
                            + "}";
            webhookService.enqueue(merchant.getId(), eventType, result.getTransactionId(), payload);
        } catch (Exception ignored) {
            // Payment submission remains authoritative; webhook delivery is retried separately.
        }
    }

    private void postLedgerEntries(
            String direction,
            PaymentRequest request,
            Merchant merchant,
            String gatewayId,
            Transaction tx,
            BigDecimal txAmount,
            BigDecimal feeAmount) {
        String currency = request.getCurrency().trim().toUpperCase();
        String providerAccount = "provider:" + gatewayId + ":" + currency + ":float";
        String merchantAccount =
                "merchant:"
                        + merchant.getId()
                        + ":"
                        + currency
                        + ":"
                        + ("PAYOUT".equals(direction) ? "payouts_payable" : "collections_payable");
        String feeExpenseAccount = "merchant:" + merchant.getId() + ":" + currency + ":fees";
        String feeRevenueAccount = "cpay:" + currency + ":fee_revenue";

        List<LedgerEntryCommand> entries = new ArrayList<>();
        if ("PAYOUT".equals(direction)) {
            entries.add(
                    ledgerEntry(
                            merchantAccount,
                            "Merchant payout payable",
                            "MERCHANT_LIABILITY",
                            "MERCHANT",
                            merchant.getId(),
                            "DR",
                            txAmount,
                            currency,
                            tx.getTx_merchant_ref()));
            entries.add(
                    ledgerEntry(
                            providerAccount,
                            "Provider float",
                            "PROVIDER_FLOAT",
                            "PROVIDER",
                            null,
                            "CR",
                            txAmount,
                            currency,
                            tx.getTx_merchant_ref()));
            if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                entries.add(
                        ledgerEntry(
                                feeExpenseAccount,
                                "Merchant transaction fees",
                                "MERCHANT_EXPENSE",
                                "MERCHANT",
                                merchant.getId(),
                                "DR",
                                feeAmount,
                                currency,
                                tx.getTx_merchant_ref()));
                entries.add(
                        ledgerEntry(
                                feeRevenueAccount,
                                "CPay fee revenue",
                                "REVENUE",
                                "SYSTEM",
                                null,
                                "CR",
                                feeAmount,
                                currency,
                                tx.getTx_merchant_ref()));
            }
        } else {
            entries.add(
                    ledgerEntry(
                            providerAccount,
                            "Provider float",
                            "PROVIDER_FLOAT",
                            "PROVIDER",
                            null,
                            "DR",
                            txAmount,
                            currency,
                            tx.getTx_merchant_ref()));
            entries.add(
                    ledgerEntry(
                            merchantAccount,
                            "Merchant collection payable",
                            "MERCHANT_LIABILITY",
                            "MERCHANT",
                            merchant.getId(),
                            "CR",
                            txAmount,
                            currency,
                            tx.getTx_merchant_ref()));
        }

        ledgerService.post(
                "payment:" + tx.getTx_unique_id(),
                "PAYMENT",
                tx.getTx_unique_id(),
                direction + " " + request.getReference(),
                entries);
    }

    private LedgerEntryCommand ledgerEntry(
            String accountCode,
            String accountName,
            String accountType,
            String ownerType,
            Long ownerId,
            String direction,
            BigDecimal amount,
            String currency,
            String memo) {
        return new LedgerEntryCommand(
                accountCode,
                accountName,
                accountType,
                ownerType,
                ownerId,
                direction,
                amount,
                currency,
                memo);
    }

    private Merchant validateMerchant(
            String merchantNumber, Merchant verifiedMerchant, String requiredApi) {
        if (verifiedMerchant == null
                || !verifiedMerchant.getAccount_number().equals(merchantNumber)) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        if (!"ACTIVE".equalsIgnoreCase(verifiedMerchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
        String[] allowedApis = verifiedMerchant.getAllowed_apis();
        boolean allowed = false;
        if (allowedApis != null) {
            for (String api : allowedApis) {
                if (requiredApi.equals(api)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (!allowed) {
            throw new PaymentGatewayException("Merchant is not allowed to access " + requiredApi);
        }
        ensureCoreAccountsConfigured();
        return verifiedMerchant;
    }

    private void ensureCoreAccountsConfigured() {
        Setting stockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
        Setting revenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
        if (stockAccount == null || stockAccount.getSetting_value().isEmpty()) {
            throw new PaymentGatewayException("Float stock account is not configured");
        }
        if (revenueAccount == null || revenueAccount.getSetting_value().isEmpty()) {
            throw new PaymentGatewayException("Revenue account is not configured");
        }
    }

    private GatewayChargeDetails getChargeDetails(String gatewayId, Merchant merchant) {
        GatewayChargeDetails chargeDetails =
                DoPayGateway.getGatewayChargeDetailsById(jdbcTemplate, gatewayId, merchant.getId());
        if (chargeDetails == null) {
            throw new PaymentGatewayException(
                    "Gateway charge details are not configured for " + gatewayId);
        }
        return chargeDetails;
    }

    private void ensureMerchantHasAvailableBalance(
            Merchant merchant, String gatewayId, BigDecimal requiredAmount) {
        List<Balance> balances =
                Common.getMerchantBalances(String.valueOf(merchant.getId()), jdbcTemplate);
        for (Balance balance : balances) {
            if (gatewayId.equals(balance.getGateway_id())) {
                BigDecimal available = BigDecimal.valueOf(balance.getAmount());
                if (requiredAmount.compareTo(available) > 0) {
                    throw new PaymentGatewayException(
                            "Insufficient balance for gateway " + gatewayId);
                }
            }
        }
    }

    private PaymentChannelAdapter resolveAdapter(
            PaymentRequest request, String accountIdentifier, String gatewayId) {
        if (request.getChannel() != null && !request.getChannel().trim().isEmpty()) {
            return paymentChannelRegistry
                    .findByChannelCode(request.getChannel())
                    .orElseThrow(
                            () ->
                                    new PaymentGatewayException(
                                            "Unsupported channel: " + request.getChannel()));
        }
        return paymentChannelRegistry
                .findByLegacyGatewayId(gatewayId)
                .orElseGet(
                        () ->
                                paymentChannelRegistry
                                        .findByAccountIdentifier(accountIdentifier)
                                        .orElseThrow(
                                                () ->
                                                        new PaymentGatewayException(
                                                                "Unable to resolve channel for account")));
    }

    private String resolveLegacyGatewayId(PaymentRequest request, String accountIdentifier) {
        if (request.getChannel() != null && !request.getChannel().trim().isEmpty()) {
            PaymentChannelAdapter adapter =
                    paymentChannelRegistry
                            .findByChannelCode(request.getChannel())
                            .orElseThrow(
                                    () ->
                                            new PaymentGatewayException(
                                                    "Unsupported channel: "
                                                            + request.getChannel()));
            if (adapter instanceof LegacyGatewayAdapter) {
                return ((LegacyGatewayAdapter) adapter).legacyGatewayId();
            }
        }
        String gatewayId = DoPayGateway.getGatewayIdByMsisdn(accountIdentifier, jdbcTemplate);
        if (gatewayId == null) {
            throw new PaymentGatewayException("Unable to resolve legacy gateway for account");
        }
        return gatewayId;
    }

    private void validatePaymentRequest(PaymentRequest request, boolean collect) {
        if (request == null) {
            throw new PaymentGatewayException("Request body is required");
        }
        require(request.getMerchantNumber(), "merchantNumber");
        require(request.getAmount(), "amount");
        require(request.getCurrency(), "currency");
        require(request.getCountry(), "country");
        require(request.getReference(), "reference");
        require(request.getDescription(), "description");
        require(request.getCallbackUrl(), "callbackUrl");
        if (collect && (request.getPayer() == null || isBlank(request.getPayer().getValue()))) {
            throw new PaymentGatewayException("payer.value is required");
        }
        if (!collect && (request.getPayee() == null || isBlank(request.getPayee().getValue()))) {
            throw new PaymentGatewayException("payee.value is required");
        }
    }

    private MoneyAmount parseMoneyAmount(String amount) {
        try {
            return MoneyAmount.of(amount);
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException(e.getMessage());
        }
    }

    private BigDecimal legacyMoney(Double amount) {
        if (amount == null || amount <= 0d) {
            return BigDecimal.ZERO.setScale(MoneyAmount.SCALE, MoneyAmount.ROUNDING_MODE);
        }
        return MoneyAmount.of(BigDecimal.valueOf(amount)).asBigDecimal();
    }

    private void require(String value, String field) {
        if (isBlank(value)) {
            throw new PaymentGatewayException(field + " is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
