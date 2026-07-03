package net.citotech.cito;

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
import net.citotech.cito.gateway.GatewayCapabilities;
import net.citotech.cito.gateway.LegacyGatewayAdapter;
import net.citotech.cito.gateway.PaymentChannelAdapter;
import net.citotech.cito.gateway.PaymentChannelRegistry;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PaymentOrchestrationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final PaymentChannelRegistry paymentChannelRegistry;

    public PaymentOrchestrationService(NamedParameterJdbcTemplate jdbcTemplate,
                                       PlatformTransactionManager transactionManager,
                                       PaymentChannelRegistry paymentChannelRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.paymentChannelRegistry = paymentChannelRegistry;
    }

    public PaymentResult collect(PaymentRequest request, Merchant verifiedMerchant, String originateIp) {
        validatePaymentRequest(request, true);
        Merchant merchant = validateMerchant(request.getMerchantNumber(), verifiedMerchant, Common.API_MOBILE_MONEY_PAYIN);
        String accountIdentifier = request.getPayer().getValue();
        PaymentChannelAdapter adapter = resolveAdapter(request, accountIdentifier);
        String gatewayId = resolveLegacyGatewayId(adapter, accountIdentifier);
        GatewayChargeDetails chargeDetails = DoPayGateway.getGatewayChargeDetailsById(jdbcTemplate, gatewayId, merchant.getId());
        if (chargeDetails == null) {
            throw new PaymentGatewayException("Gateway charge details are not configured for " + gatewayId);
        }

        Double amount = parseAmount(request.getAmount());
        Transaction tx = baseTransaction(request, merchant, gatewayId, originateIp, amount);
        tx.setPayer_number(accountIdentifier);
        tx.setTx_type(Transaction.TX_TYPE_PAYIN);
        tx.setCharging_method(chargeDetails.getCustomerInboundChargeMethod());
        tx.setCharges(DoPayGateway.getCustomerInboundCharges(amount, chargeDetails));
        tx.setTx_cost(DoPayGateway.getCostOfInboundCharges(amount, chargeDetails));

        String legacyResult = Common.doPayIn(tx, merchant, jdbcTemplate, transactionManager);
        return resultFromLegacy(request, tx, adapter, legacyResult);
    }

    public PaymentResult payout(PaymentRequest request, Merchant verifiedMerchant, String originateIp) {
        validatePaymentRequest(request, false);
        Merchant merchant = validateMerchant(request.getMerchantNumber(), verifiedMerchant, Common.API_MOBILE_MONEY_PAYOUT);
        String accountIdentifier = request.getPayee().getValue();
        PaymentChannelAdapter adapter = resolveAdapter(request, accountIdentifier);
        String gatewayId = resolveLegacyGatewayId(adapter, accountIdentifier);
        GatewayChargeDetails chargeDetails = DoPayGateway.getGatewayChargeDetailsById(jdbcTemplate, gatewayId, merchant.getId());
        if (chargeDetails == null) {
            throw new PaymentGatewayException("Gateway charge details are not configured for " + gatewayId);
        }

        Double amount = parseAmount(request.getAmount());
        Transaction tx = baseTransaction(request, merchant, gatewayId, originateIp, amount);
        tx.setPayer_number(accountIdentifier);
        tx.setTx_type(Transaction.TX_TYPE_PAYOUT);
        tx.setCharging_method(chargeDetails.getCustomerOutboundChargeMethod());
        tx.setCharges(DoPayGateway.getCustomerOutboundCharges(amount, chargeDetails));
        tx.setTx_cost(DoPayGateway.getCostOfOutboundCharges(amount, chargeDetails));

        String legacyResult = Common.doPayOut(tx, merchant, jdbcTemplate, transactionManager);
        return resultFromLegacy(request, tx, adapter, legacyResult);
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
        Merchant merchant = validateMerchant(merchantNumber, verifiedMerchant, Common.API_GET_BALANCES);
        return Common.getMerchantBalances(String.valueOf(merchant.getId()), jdbcTemplate);
    }

    private Transaction baseTransaction(PaymentRequest request,
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

    private PaymentResult resultFromLegacy(PaymentRequest request,
                                           Transaction tx,
                                           PaymentChannelAdapter adapter,
                                           String legacyResult) {
        PaymentResult result = new PaymentResult();
        result.setReference(request.getReference());
        result.setTransactionId(tx.getTx_unique_id());
        result.setStatus("SUBMITTED");
        result.setChannel(adapter.channelCode());
        result.setCurrency(request.getCurrency());
        result.setMessage("Transaction submitted to legacy payment engine");
        result.setProviderResponse(legacyResult);
        return result;
    }

    private Merchant validateMerchant(String merchantNumber, Merchant verifiedMerchant, String requiredApi) {
        if (verifiedMerchant == null || !verifiedMerchant.getAccount_number().equals(merchantNumber)) {
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

    private PaymentChannelAdapter resolveAdapter(PaymentRequest request, String accountIdentifier) {
        if (request.getChannel() != null && !request.getChannel().trim().isEmpty()) {
            return paymentChannelRegistry.findByChannelCode(request.getChannel())
                    .orElseThrow(() -> new PaymentGatewayException("Unsupported channel: " + request.getChannel()));
        }
        return paymentChannelRegistry.findByAccountIdentifier(accountIdentifier)
                .orElseThrow(() -> new PaymentGatewayException("Unable to resolve channel for account"));
    }

    private String resolveLegacyGatewayId(PaymentChannelAdapter adapter, String accountIdentifier) {
        if (adapter instanceof LegacyGatewayAdapter) {
            String legacyGatewayId = ((LegacyGatewayAdapter) adapter).legacyGatewayId();
            if (legacyGatewayId != null && !legacyGatewayId.trim().isEmpty()) {
                return legacyGatewayId;
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

    private Double parseAmount(String amount) {
        try {
            Double parsed = Double.parseDouble(amount);
            if (parsed <= 0) {
                throw new PaymentGatewayException("Amount must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Invalid amount");
        }
    }

    private void require(String value, String field) {
        if (isBlank(value)) {
            throw new PaymentGatewayException(field + " is required");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
