package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.billing.pricing.BillingCommercialRatingService;
import net.citotech.cito.billing.pricing.ContractPriceOverrideService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public BaaS pricing surface layered on the existing compatibility charging API. */
@RestController
@RequestMapping("/api/v2/native/billing/baas")
public class BillingBaasPricingController {
    private final BillingBaasApiKeyService apiKeyService;
    private final BillingCommercialRatingService ratingService;
    private final BillingRatedAuthorizationService ratedAuthorizationService;
    private final ContractPriceOverrideService contractPriceOverrideService;

    public BillingBaasPricingController(
            BillingBaasApiKeyService apiKeyService,
            BillingCommercialRatingService ratingService,
            BillingRatedAuthorizationService ratedAuthorizationService,
            ContractPriceOverrideService contractPriceOverrideService) {
        this.apiKeyService = apiKeyService;
        this.ratingService = ratingService;
        this.ratedAuthorizationService = ratedAuthorizationService;
        this.contractPriceOverrideService = contractPriceOverrideService;
    }

    @PostMapping("/pricing/quotes")
    public ResponseEntity<?> quote(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody PricingQuoteRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_READ",
                            requestId,
                            "POST",
                            "/pricing/quotes");
            Instant asOf = body.asOf() == null ? Instant.now() : body.asOf();
            return ResponseEntity.ok(
                    ratingService.rate(
                            context,
                            body.billingAccountReference(),
                            body.contractReference(),
                            body.serviceCode(),
                            body.meterCode(),
                            body.ratingBaseAmount(),
                            body.sourceCurrency(),
                            asOf));
        } catch (PaymentGatewayException | IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    @PostMapping("/charges/rate-and-authorize")
    public ResponseEntity<?> rateAndAuthorize(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody RatedAuthorizationRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_CHARGE",
                            requestId,
                            "POST",
                            "/charges/rate-and-authorize");
            return ResponseEntity.ok(
                    ratedAuthorizationService.rateAndAuthorize(
                            context,
                            body.billingAccountReference(),
                            body.contractReference(),
                            body.serviceCode(),
                            body.meterCode(),
                            body.entitlementCode(),
                            body.usageQuantity(),
                            body.ratingBaseAmount(),
                            body.sourceCurrency(),
                            body.idempotencyKey(),
                            body.expiresAt()));
        } catch (PaymentGatewayException | IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    @PostMapping("/contracts/{reference}/price-overrides")
    public ResponseEntity<?> submitPriceOverride(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String reference,
            @RequestBody PriceOverrideRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/contracts/{reference}/price-overrides");
            return ResponseEntity.ok(
                    contractPriceOverrideService.submit(
                            context,
                            reference,
                            body.serviceCode(),
                            body.meterCode(),
                            body.priceBookVersionId(),
                            body.effectiveFrom(),
                            body.effectiveTo()));
        } catch (PaymentGatewayException | IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    @PostMapping("/price-overrides/{overrideId}/approve")
    public ResponseEntity<?> approvePriceOverride(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long overrideId) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/price-overrides/{overrideId}/approve");
            return ResponseEntity.ok(contractPriceOverrideService.approve(context, overrideId));
        } catch (PaymentGatewayException | IllegalArgumentException | IllegalStateException e) {
            return error(e);
        }
    }

    private BillingBaasContext context(
            String apiKey,
            String environment,
            String scope,
            String requestId,
            String method,
            String route) {
        return apiKeyService.authenticate(
                apiKey,
                environment,
                scope,
                requestId,
                method,
                "/api/v2/native/billing/baas" + route);
    }

    private ResponseEntity<?> error(RuntimeException e) {
        String message = e.getMessage() == null ? "BaaS pricing request failed" : e.getMessage();
        HttpStatus status =
                message.contains("credential") || message.contains("X-Cito-Api-Key")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("code", "BILLING_BAAS_PRICING_REJECTED", "message", message));
    }

    public record PricingQuoteRequest(
            String billingAccountReference,
            String contractReference,
            String serviceCode,
            String meterCode,
            BigDecimal ratingBaseAmount,
            String sourceCurrency,
            Instant asOf) {}

    public record RatedAuthorizationRequest(
            String billingAccountReference,
            String contractReference,
            String serviceCode,
            String meterCode,
            String entitlementCode,
            BigDecimal usageQuantity,
            BigDecimal ratingBaseAmount,
            String sourceCurrency,
            String idempotencyKey,
            Instant expiresAt) {}

    public record PriceOverrideRequest(
            String serviceCode,
            String meterCode,
            long priceBookVersionId,
            Instant effectiveFrom,
            Instant effectiveTo) {}
}
