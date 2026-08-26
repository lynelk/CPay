package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/native/billing/baas")
public class BillingBaasController {
    private final BillingBaasApiKeyService apiKeyService;
    private final BillingBaasCommercialService commercialService;
    private final BillingBaasChargingService chargingService;
    private final BillingBaasProtectedActionService protectedActionService;

    public BillingBaasController(
            BillingBaasApiKeyService apiKeyService,
            BillingBaasCommercialService commercialService,
            BillingBaasChargingService chargingService,
            BillingBaasProtectedActionService protectedActionService) {
        this.apiKeyService = apiKeyService;
        this.commercialService = commercialService;
        this.chargingService = chargingService;
        this.protectedActionService = protectedActionService;
    }

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody CustomerRequest body) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_WRITE", requestId, "POST", "/customers");
            return ResponseEntity.ok(
                    commercialService.createCustomer(
                            context,
                            body.externalReference(),
                            body.displayName(),
                            body.legalName(),
                            body.email(),
                            body.metadataJson()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/customers")
    public ResponseEntity<?> customers(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_READ", requestId, "GET", "/customers");
            return ResponseEntity.ok(commercialService.customers(context));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/accounts")
    public ResponseEntity<?> createAccount(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody AccountRequest body) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_WRITE", requestId, "POST", "/accounts");
            return ResponseEntity.ok(
                    commercialService.createAccount(
                            context,
                            body.customerReference(),
                            body.accountReference(),
                            body.currency()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/contracts")
    public ResponseEntity<?> createContract(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody ContractRequest body) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_WRITE", requestId, "POST", "/contracts");
            return ResponseEntity.ok(
                    commercialService.createContract(
                            context,
                            body.customerReference(),
                            body.contractReference(),
                            body.currency(),
                            body.effectiveFrom(),
                            body.effectiveTo(),
                            body.termsJson()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/contracts/{reference}/submit")
    public ResponseEntity<?> submitContract(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return contractTransition(apiKey, environment, requestId, reference, "submit");
    }

    @PostMapping("/contracts/{reference}/approve")
    public ResponseEntity<?> approveContract(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return contractTransition(apiKey, environment, requestId, reference, "approve");
    }

    @PostMapping("/contracts/{reference}/activate")
    public ResponseEntity<?> activateContract(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return contractTransition(apiKey, environment, requestId, reference, "activate");
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody SubscriptionRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/subscriptions");
            return ResponseEntity.ok(
                    commercialService.createSubscription(
                            context,
                            body.customerReference(),
                            body.accountReference(),
                            body.contractReference(),
                            body.subscriptionReference(),
                            body.serviceCode(),
                            body.planCode(),
                            body.quantity(),
                            body.startsAt(),
                            body.endsAt()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/subscriptions/{reference}/activate")
    public ResponseEntity<?> activateSubscription(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return subscriptionTransition(apiKey, environment, requestId, reference, "activate");
    }

    @PostMapping("/subscriptions/{reference}/pause")
    public ResponseEntity<?> pauseSubscription(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return subscriptionTransition(apiKey, environment, requestId, reference, "pause");
    }

    @PostMapping("/subscriptions/{reference}/cancel")
    public ResponseEntity<?> cancelSubscription(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        return subscriptionTransition(apiKey, environment, requestId, reference, "cancel");
    }

    @PostMapping("/subscriptions/{reference}/entitlements")
    public ResponseEntity<?> grantEntitlement(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference,
            @RequestBody EntitlementRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/subscriptions/{reference}/entitlements");
            return ResponseEntity.ok(
                    commercialService.grantEntitlement(
                            context,
                            reference,
                            body.entitlementCode(),
                            body.limitQuantity(),
                            body.validFrom(),
                            body.validTo()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/charges")
    public ResponseEntity<?> authorizeCharge(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody AuthorizeChargeRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_CHARGE",
                            requestId,
                            "POST",
                            "/charges");
            return ResponseEntity.ok(
                    chargingService.authorize(
                            context,
                            body.billingAccountReference(),
                            body.serviceCode(),
                            body.entitlementCode(),
                            body.usageQuantity(),
                            body.netAmount(),
                            body.currency(),
                            body.idempotencyKey(),
                            body.expiresAt()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/charges/{reference}/commit")
    public ResponseEntity<?> commitCharge(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_CHARGE",
                            requestId,
                            "POST",
                            "/charges/{reference}/commit");
            return ResponseEntity.ok(chargingService.commit(context, reference));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/charges/{reference}/release")
    public ResponseEntity<?> releaseCharge(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_CHARGE",
                            requestId,
                            "POST",
                            "/charges/{reference}/release");
            return ResponseEntity.ok(chargingService.release(context, reference));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/charges/{reference}/reverse")
    public ResponseEntity<?> reverseCharge(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_CHARGE",
                            requestId,
                            "POST",
                            "/charges/{reference}/reverse");
            return ResponseEntity.ok(chargingService.reverse(context, reference));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/charges/{reference}")
    public ResponseEntity<?> getCharge(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_READ",
                            requestId,
                            "GET",
                            "/charges/{reference}");
            return ResponseEntity.ok(chargingService.get(context, reference));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/protected-actions")
    public ResponseEntity<?> requestProtectedAction(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody ProtectedActionRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/protected-actions");
            return ResponseEntity.ok(
                    protectedActionService.request(
                            context,
                            body.actionType(),
                            body.resourceType(),
                            body.resourceReference()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/protected-actions/{id}/approve")
    public ResponseEntity<?> approveProtectedAction(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long id,
            @RequestBody(required = false) DecisionRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/protected-actions/{id}/approve");
            return ResponseEntity.ok(
                    protectedActionService.approve(
                            context, id, body == null ? null : body.reason()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> contractTransition(
            String apiKey,
            String environment,
            String requestId,
            String reference,
            String transition) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/contracts/{reference}/" + transition);
            Object result =
                    switch (transition) {
                        case "submit" -> commercialService.submitContract(context, reference);
                        case "approve" -> commercialService.approveContract(context, reference);
                        case "activate" -> commercialService.activateContract(context, reference);
                        default ->
                                throw new PaymentGatewayException(
                                        "Unsupported contract transition");
                    };
            return ResponseEntity.ok(result);
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    private ResponseEntity<?> subscriptionTransition(
            String apiKey,
            String environment,
            String requestId,
            String reference,
            String transition) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/subscriptions/{reference}/" + transition);
            Object result =
                    switch (transition) {
                        case "activate" ->
                                commercialService.activateSubscription(context, reference);
                        case "pause" -> commercialService.pauseSubscription(context, reference);
                        case "cancel" -> commercialService.cancelSubscription(context, reference);
                        default ->
                                throw new PaymentGatewayException(
                                        "Unsupported subscription transition");
                    };
            return ResponseEntity.ok(result);
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    private BillingBaasContext context(
            String apiKey,
            String environment,
            String scope,
            String requestId,
            String httpMethod,
            String route) {
        return apiKeyService.authenticate(
                apiKey,
                environment,
                scope,
                requestId,
                httpMethod,
                "/api/v2/native/billing/baas" + route);
    }

    private ResponseEntity<?> error(PaymentGatewayException e) {
        String message = e.getMessage() == null ? "BaaS request failed" : e.getMessage();
        HttpStatus status =
                message.contains("credential") || message.contains("X-Cito-Api-Key")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("code", "BILLING_BAAS_REQUEST_REJECTED", "message", message));
    }

    public record CustomerRequest(
            String externalReference,
            String displayName,
            String legalName,
            String email,
            String metadataJson) {}

    public record AccountRequest(
            String customerReference, String accountReference, String currency) {}

    public record ContractRequest(
            String customerReference,
            String contractReference,
            String currency,
            Instant effectiveFrom,
            Instant effectiveTo,
            String termsJson) {}

    public record SubscriptionRequest(
            String customerReference,
            String accountReference,
            String contractReference,
            String subscriptionReference,
            String serviceCode,
            String planCode,
            BigDecimal quantity,
            Instant startsAt,
            Instant endsAt) {}

    public record EntitlementRequest(
            String entitlementCode, BigDecimal limitQuantity, Instant validFrom, Instant validTo) {}

    public record AuthorizeChargeRequest(
            String billingAccountReference,
            String serviceCode,
            String entitlementCode,
            BigDecimal usageQuantity,
            BigDecimal netAmount,
            String currency,
            String idempotencyKey,
            Instant expiresAt) {}

    public record ProtectedActionRequest(
            String actionType, String resourceType, String resourceReference) {}

    public record DecisionRequest(String reason) {}
}
