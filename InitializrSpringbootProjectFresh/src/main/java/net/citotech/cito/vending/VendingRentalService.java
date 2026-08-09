package net.citotech.cito.vending;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.admin.FeatureKeys;
import net.citotech.cito.admin.FeatureRegistryService;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.repository.TransactionRepository;
import net.citotech.cito.vending.VendingCustomerIdentityService.CustomerIdentity;
import net.citotech.cito.vending.VendingPricingEngine.Rating;
import net.citotech.cito.vending.connector.VendingConnectorAdapter;
import net.citotech.cito.vending.connector.VendingConnectorRegistry;
import org.springframework.stereotype.Service;

/**
 * State machine for generic vending rentals.
 *
 * <p>Power-bank rental is the first profile, but the service intentionally talks in devices,
 * assets, pricing policies and commands. Payment collection/refund goes through CPay's payment
 * orchestration; physical release goes through a manufacturer connector adapter.
 */
@Service
public class VendingRentalService {
    private final VendingRepository repository;
    private final VendingCustomerIdentityService identities;
    private final VendingPricingEngine pricingEngine;
    private final VendingPaymentService paymentService;
    private final TransactionRepository transactionRepository;
    private final VendingConnectorRegistry connectorRegistry;
    private final FeatureRegistryService features;

    public VendingRentalService(
            VendingRepository repository,
            VendingCustomerIdentityService identities,
            VendingPricingEngine pricingEngine,
            VendingPaymentService paymentService,
            TransactionRepository transactionRepository,
            VendingConnectorRegistry connectorRegistry,
            FeatureRegistryService features) {
        this.repository = repository;
        this.identities = identities;
        this.pricingEngine = pricingEngine;
        this.paymentService = paymentService;
        this.transactionRepository = transactionRepository;
        this.connectorRegistry = connectorRegistry;
        this.features = features;
    }

    public Map<String, Object> startRental(
            long merchantId,
            String deviceCode,
            String customerMsisdn,
            String channelCode,
            String requestedReference,
            String actor) {
        requireEnabled(merchantId);
        Map<String, Object> device = repository.deviceByCode(merchantId, deviceCode);
        if (!"ONLINE".equalsIgnoreCase(VendingRepository.string(device.get("status")))) {
            throw new PaymentGatewayException("Vending device is not online");
        }
        long policyId = VendingRepository.number(device.get("pricing_policy_id"));
        VendingPricingPolicy policy = repository.pricingPolicy(merchantId, policyId);
        CustomerIdentity identity = identities.protect(merchantId, customerMsisdn);

        if (repository.activeRentalForCustomer(merchantId, identity.hash()).isPresent()) {
            throw new PaymentGatewayException("Customer already has an open vending rental");
        }
        Map<String, Object> balance =
                repository.customerBalance(merchantId, identity.hash(), policy.currency());
        if ("YES".equalsIgnoreCase(VendingRepository.string(balance.get("blocked_flag")))) {
            throw new PaymentGatewayException("Customer is blocked from vending rentals");
        }

        BigDecimal surchargeBalance =
                VendingRepository.decimal(balance.get("surcharge_balance"));
        BigDecimal deposit = policy.depositAmount();
        BigDecimal surchargePlanned = deposit.min(surchargeBalance).max(BigDecimal.ZERO);
        BigDecimal escrow = deposit.subtract(surchargePlanned);
        String rentalReference =
                requestedReference == null || requestedReference.isBlank()
                        ? "VEND-" + UUID.randomUUID()
                        : requestedReference.trim();
        String collectReference = "VEND-COLLECT-" + rentalReference;

        repository.createRental(
                merchantId,
                rentalReference,
                VendingRepository.number(device.get("id")),
                policyId,
                identity.hash(),
                identity.mask(),
                identity.cipherText(),
                channelCode,
                policy,
                surchargePlanned,
                escrow,
                collectReference);
        repository.event(
                merchantId,
                "RENTAL_CREATED",
                "RENTAL",
                rentalReference,
                actor,
                deposit,
                policy.currency(),
                "{\"deviceCode\":\"" + json(deviceCode) + "\"}");

        try {
            PaymentResult result =
                    paymentService.collectDeposit(
                            merchantId,
                            identities.normalize(customerMsisdn),
                            deposit.toPlainString(),
                            policy.currency(),
                            channelCode,
                            collectReference);
            repository.setCollectTransaction(
                    merchantId, rentalReference, result.getTransactionId());
            repository.event(
                    merchantId,
                    "DEPOSIT_SUBMITTED",
                    "RENTAL",
                    rentalReference,
                    actor,
                    deposit,
                    policy.currency(),
                    "{\"transactionId\":\"" + json(result.getTransactionId()) + "\"}");
        } catch (RuntimeException e) {
            repository.setRentalStatus(merchantId, rentalReference, "PAYMENT_FAILED");
            repository.event(
                    merchantId,
                    "DEPOSIT_SUBMISSION_FAILED",
                    "RENTAL",
                    rentalReference,
                    actor,
                    deposit,
                    policy.currency(),
                    null);
            throw e;
        }
        return rentalView(merchantId, rentalReference);
    }

    /**
     * Reconciles asynchronous CPay transaction state into the vending state machine. A
     * RELEASE_PENDING rental deliberately does not dispatch again: physical command idempotency is
     * guarded by the unique command claim and final release is confirmed by the OEM callback.
     */
    public Map<String, Object> sync(long merchantId, String rentalReference, String actor) {
        requireEnabled(merchantId);
        Map<String, Object> rental = requireRental(merchantId, rentalReference);
        String status = VendingRepository.string(rental.get("status"));
        if ("PAYMENT_PENDING".equals(status)) {
            syncCollection(merchantId, rental, actor);
        } else if ("READY_TO_RELEASE".equals(status) || "RELEASE_FAILED".equals(status)) {
            release(merchantId, rentalReference, actor);
        } else if ("REFUND_PENDING".equals(status)) {
            syncRefund(merchantId, rental, actor);
        }
        return rentalView(merchantId, rentalReference);
    }

    public Map<String, Object> release(long merchantId, String rentalReference, String actor) {
        requireEnabled(merchantId);
        Map<String, Object> rental = requireRental(merchantId, rentalReference);
        String status = VendingRepository.string(rental.get("status"));
        if (!"READY_TO_RELEASE".equals(status) && !"RELEASE_FAILED".equals(status)) {
            if ("ACTIVE".equals(status) || "RELEASE_PENDING".equals(status)) {
                return rentalView(merchantId, rentalReference);
            }
            throw new PaymentGatewayException("Rental is not ready for device release");
        }

        Map<String, Object> device =
                repository.devices(merchantId).stream()
                        .filter(
                                d ->
                                        VendingRepository.number(d.get("id"))
                                                == VendingRepository.number(
                                                        rental.get("device_id")))
                        .findFirst()
                        .orElseThrow(
                                () -> new PaymentGatewayException("Vending device was not found"));
        String connectorCode = VendingRepository.string(device.get("connector_code"));
        String commandReference = "VEND-RELEASE-" + rentalReference;
        long deviceId = VendingRepository.number(device.get("id"));
        long rentalId = VendingRepository.number(rental.get("id"));
        String requestEvidence =
                "{\"rentalReference\":\""
                        + json(rentalReference)
                        + "\",\"externalDeviceId\":\""
                        + json(VendingRepository.string(device.get("external_device_id")))
                        + "\"}";

        boolean claimed =
                repository.claimCommand(
                        merchantId,
                        deviceId,
                        rentalId,
                        commandReference,
                        "RELEASE_ASSET",
                        connectorCode,
                        requestEvidence);
        if (!claimed) {
            // Another CPay instance has already claimed this deterministic physical command. Never
            // race it with a second eject call merely because two browsers happened to poll at once.
            return rentalView(merchantId, rentalReference);
        }
        repository.setRentalStatus(merchantId, rentalReference, "RELEASE_PENDING");

        VendingConnectorAdapter adapter = connectorRegistry.require(connectorCode);
        try {
            VendingConnectorAdapter.VendingCommandResult result =
                    adapter.execute(
                            new VendingConnectorAdapter.VendingCommand(
                                    merchantId,
                                    deviceId,
                                    VendingRepository.string(device.get("external_device_id")),
                                    commandReference,
                                    "RELEASE_ASSET",
                                    Map.of("rentalReference", rentalReference)));
            repository.completeCommand(
                    merchantId,
                    commandReference,
                    result.status(),
                    result.providerReference(),
                    "{\"message\":\"" + json(result.message()) + "\"}");

            if (!result.success()) {
                repository.setRentalStatus(merchantId, rentalReference, "RELEASE_FAILED");
            } else if ("COMPLETED".equalsIgnoreCase(result.status())) {
                // Simulators or OEM operations explicitly certified as IMMEDIATE may assert that
                // the response itself confirms ejection. Normal ChargeNow release uses CALLBACK.
                repository.markRentalActive(merchantId, rentalReference);
            }

            String eventType =
                    !result.success()
                            ? "DEVICE_RELEASE_FAILED"
                            : "COMPLETED".equalsIgnoreCase(result.status())
                                    ? "DEVICE_RELEASE_CONFIRMED"
                                    : "DEVICE_RELEASE_COMMAND_ACCEPTED";
            repository.event(
                    merchantId,
                    eventType,
                    "RENTAL",
                    rentalReference,
                    actor,
                    null,
                    VendingRepository.string(rental.get("currency")),
                    "{\"connector\":\""
                            + json(connectorCode)
                            + "\",\"providerReference\":\""
                            + json(result.providerReference())
                            + "\"}");
            return rentalView(merchantId, rentalReference);
        } catch (RuntimeException e) {
            repository.completeCommand(
                    merchantId,
                    commandReference,
                    "FAILED",
                    null,
                    "{\"message\":\"" + json(safeMessage(e)) + "\"}");
            repository.setRentalStatus(merchantId, rentalReference, "RELEASE_FAILED");
            repository.event(
                    merchantId,
                    "DEVICE_RELEASE_FAILED",
                    "RENTAL",
                    rentalReference,
                    actor,
                    null,
                    VendingRepository.string(rental.get("currency")),
                    "{\"connector\":\"" + json(connectorCode) + "\"}");
            throw e;
        }
    }

    public Map<String, Object> returnRental(
            long merchantId, String rentalReference, String actor) {
        requireEnabled(merchantId);
        Map<String, Object> rental = requireRental(merchantId, rentalReference);
        if (!"ACTIVE".equals(VendingRepository.string(rental.get("status")))) {
            throw new PaymentGatewayException("Only an active rental can be returned");
        }
        VendingPricingPolicy policy =
                repository.pricingPolicy(
                        merchantId,
                        VendingRepository.number(rental.get("pricing_policy_id")));
        Instant startedAt = VendingRepository.instant(rental.get("started_at"));
        long suspendedSeconds =
                VendingRepository.number(rental.get("billing_suspended_seconds"));
        if ("YES".equalsIgnoreCase(
                VendingRepository.string(rental.get("bill_suspended_time_flag")))) {
            suspendedSeconds = 0;
        }
        Rating rating =
                pricingEngine.rate(policy, startedAt, Instant.now(), suspendedSeconds);
        BigDecimal escrow = VendingRepository.decimal(rental.get("escrow_amount"));
        BigDecimal refund = escrow.subtract(rating.usageAmount()).max(BigDecimal.ZERO);
        BigDecimal surcharge =
                rating.usageAmount().subtract(escrow).max(BigDecimal.ZERO);
        String refundReference =
                refund.signum() > 0 ? "VEND-REFUND-" + rentalReference : null;
        String nextStatus = refund.signum() > 0 ? "REFUND_PENDING" : "SETTLED";

        repository.completeRental(
                merchantId,
                rentalReference,
                rating.usageAmount(),
                refund,
                surcharge,
                rating.billedBlocks(),
                nextStatus,
                refundReference);
        repository.addSurcharge(
                merchantId,
                VendingRepository.string(rental.get("customer_hash")),
                policy.currency(),
                surcharge);
        repository.event(
                merchantId,
                "RENTAL_RETURNED",
                "RENTAL",
                rentalReference,
                actor,
                rating.usageAmount(),
                policy.currency(),
                "{\"refund\":\""
                        + refund.toPlainString()
                        + "\",\"surcharge\":\""
                        + surcharge.toPlainString()
                        + "\"}");

        if (refund.signum() > 0) {
            submitRefund(merchantId, requireRental(merchantId, rentalReference), actor);
        }
        return rentalView(merchantId, rentalReference);
    }

    public Map<String, Object> waiveSurcharge(
            long merchantId,
            String customerMsisdn,
            String currency,
            BigDecimal amount,
            String note,
            String actor) {
        requireEnabled(merchantId);
        CustomerIdentity identity = identities.protect(merchantId, customerMsisdn);
        BigDecimal waived =
                repository.waiveSurcharge(
                        merchantId, identity.hash(), currency, amount);
        repository.event(
                merchantId,
                "SURCHARGE_WAIVED",
                "CUSTOMER",
                identity.hash(),
                actor,
                waived,
                currency,
                "{\"note\":\"" + json(note) + "\"}");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("customer", identity.mask());
        response.put("waivedAmount", waived);
        response.put("currency", currency);
        response.put(
                "balance",
                repository.customerBalance(merchantId, identity.hash(), currency));
        return response;
    }

    private void syncCollection(
            long merchantId, Map<String, Object> rental, String actor) {
        String reference = VendingRepository.string(rental.get("collect_reference"));
        Transaction tx =
                transactionRepository
                        .findByMerchantReference(merchantId, reference)
                        .orElseThrow(
                                () ->
                                        new PaymentGatewayException(
                                                "Deposit transaction has not been recorded yet"));
        String txStatus =
                tx.getStatus() == null ? "" : tx.getStatus().trim().toUpperCase();
        if (isSuccess(txStatus)) {
            int activated =
                    repository.activateAfterSuccessfulCollection(
                            merchantId,
                            VendingRepository.string(rental.get("rental_reference")));
            if (activated > 0) {
                repository.settlePriorSurcharge(
                        merchantId,
                        VendingRepository.string(rental.get("customer_hash")),
                        VendingRepository.string(rental.get("currency")),
                        VendingRepository.decimal(
                                rental.get("surcharge_settled_from_deposit")));
                repository.event(
                        merchantId,
                        "DEPOSIT_CONFIRMED",
                        "RENTAL",
                        VendingRepository.string(rental.get("rental_reference")),
                        actor,
                        VendingRepository.decimal(rental.get("deposit_amount")),
                        VendingRepository.string(rental.get("currency")),
                        null);
            }
            release(
                    merchantId,
                    VendingRepository.string(rental.get("rental_reference")),
                    actor);
        } else if (isFailure(txStatus)) {
            repository.setRentalStatus(
                    merchantId,
                    VendingRepository.string(rental.get("rental_reference")),
                    "PAYMENT_FAILED");
        }
    }

    private void submitRefund(
            long merchantId, Map<String, Object> rental, String actor) {
        BigDecimal refund = VendingRepository.decimal(rental.get("refund_amount"));
        if (refund.signum() <= 0) return;
        try {
            PaymentResult result =
                    paymentService.refund(
                            merchantId,
                            identities.reveal(
                                    VendingRepository.string(
                                            rental.get("customer_ciphertext"))),
                            refund.toPlainString(),
                            VendingRepository.string(rental.get("currency")),
                            VendingRepository.string(rental.get("channel_code")),
                            VendingRepository.string(rental.get("refund_reference")));
            repository.setRefundTransaction(
                    merchantId,
                    VendingRepository.string(rental.get("rental_reference")),
                    result.getTransactionId());
            repository.event(
                    merchantId,
                    "REFUND_SUBMITTED",
                    "RENTAL",
                    VendingRepository.string(rental.get("rental_reference")),
                    actor,
                    refund,
                    VendingRepository.string(rental.get("currency")),
                    null);
        } catch (RuntimeException e) {
            repository.event(
                    merchantId,
                    "REFUND_SUBMISSION_FAILED",
                    "RENTAL",
                    VendingRepository.string(rental.get("rental_reference")),
                    actor,
                    refund,
                    VendingRepository.string(rental.get("currency")),
                    null);
        }
    }

    private void syncRefund(
            long merchantId, Map<String, Object> rental, String actor) {
        String refundReference =
                VendingRepository.string(rental.get("refund_reference"));
        if (refundReference.isBlank()) return;
        var tx = transactionRepository.findByMerchantReference(merchantId, refundReference);
        if (tx.isEmpty()) {
            if (VendingRepository.string(rental.get("refund_transaction_id")).isBlank()) {
                submitRefund(merchantId, rental, actor);
            }
            return;
        }
        String txStatus =
                tx.get().getStatus() == null
                        ? ""
                        : tx.get().getStatus().trim().toUpperCase();
        if (isSuccess(txStatus)) {
            repository.setRentalStatus(
                    merchantId,
                    VendingRepository.string(rental.get("rental_reference")),
                    "SETTLED");
            repository.event(
                    merchantId,
                    "REFUND_CONFIRMED",
                    "RENTAL",
                    VendingRepository.string(rental.get("rental_reference")),
                    actor,
                    VendingRepository.decimal(rental.get("refund_amount")),
                    VendingRepository.string(rental.get("currency")),
                    null);
        } else if (isFailure(txStatus)) {
            repository.setRentalStatus(
                    merchantId,
                    VendingRepository.string(rental.get("rental_reference")),
                    "REFUND_FAILED");
        }
    }

    private Map<String, Object> requireRental(long merchantId, String reference) {
        return repository.rental(merchantId, reference)
                .orElseThrow(
                        () -> new PaymentGatewayException("Vending rental was not found"));
    }

    private Map<String, Object> rentalView(long merchantId, String reference) {
        Map<String, Object> row =
                new LinkedHashMap<>(requireRental(merchantId, reference));
        row.remove("customer_hash");
        row.remove("customer_ciphertext");
        return row;
    }

    private void requireEnabled(long merchantId) {
        if (!features.isEnabled(FeatureKeys.VENDING_PLATFORM, merchantId)) {
            throw new PaymentGatewayException(
                    "Vending platform is not enabled for this merchant");
        }
    }

    private boolean isSuccess(String status) {
        return "SUCCESSFUL".equals(status)
                || "SUCCESS".equals(status)
                || "COMPLETED".equals(status);
    }

    private boolean isFailure(String status) {
        return "FAILED".equals(status)
                || "REJECTED".equals(status)
                || "CANCELLED".equals(status);
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }
}
