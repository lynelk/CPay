package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.time.Instant;
import net.citotech.cito.billing.baas.BillingBaasChargingService.ChargeView;
import net.citotech.cito.billing.pricing.BillingCommercialRatingService;
import net.citotech.cito.billing.pricing.CommercialRatingResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side rate-and-authorize path. The legacy BaaS `/charges` route still accepts a caller-
 * supplied net amount for compatibility; this service is the preferred monetization contract and
 * derives the net amount from Cito's deterministic commercial rating chain.
 */
@Service
public class BillingRatedAuthorizationService {
    private final BillingCommercialRatingService ratingService;
    private final BillingBaasChargingService chargingService;

    public BillingRatedAuthorizationService(
            BillingCommercialRatingService ratingService,
            BillingBaasChargingService chargingService) {
        this.ratingService = ratingService;
        this.chargingService = chargingService;
    }

    @Transactional
    public RatedAuthorizationResult rateAndAuthorize(
            BillingBaasContext context,
            String billingAccountReference,
            String contractReference,
            String serviceCode,
            String meterCode,
            String entitlementCode,
            BigDecimal usageQuantity,
            BigDecimal ratingBaseAmount,
            String sourceCurrency,
            String idempotencyKey,
            Instant expiresAt) {
        Instant ratedAt = Instant.now();
        CommercialRatingResult rating =
                ratingService.rate(
                        context,
                        billingAccountReference,
                        contractReference,
                        serviceCode,
                        meterCode,
                        ratingBaseAmount,
                        sourceCurrency,
                        ratedAt);
        ChargeView charge =
                chargingService.authorize(
                        context,
                        billingAccountReference,
                        serviceCode,
                        entitlementCode,
                        usageQuantity,
                        rating.customerNetAmount(),
                        rating.billingCurrency(),
                        idempotencyKey,
                        expiresAt);

        // The charging service independently resolves tax for the live authorization. Prove it
        // matches the commercial quote before retaining evidence. If configuration changed between
        // the two operations, release rather than reserve funds against two different commercial
        // truths.
        if (charge.taxRuleVersionId() == null
                || charge.taxRuleVersionId() != rating.taxRuleVersionId()
                || charge.authorizedNetAmount().compareTo(rating.customerNetAmount()) != 0
                || charge.authorizedTaxAmount().compareTo(rating.taxAmount()) != 0) {
            if ("AUTHORIZED".equals(charge.status())) {
                chargingService.release(context, charge.reservationReference());
            }
            throw new PaymentGatewayException(
                    "Commercial rating changed during authorization; retry with a new idempotency key");
        }

        ratingService.retainEvidence(context, charge.reservationReference(), rating);
        return new RatedAuthorizationResult(charge, rating);
    }

    public record RatedAuthorizationResult(
            ChargeView charge, CommercialRatingResult commercialRating) {}
}
