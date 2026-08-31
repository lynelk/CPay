package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import net.citotech.cito.billing.fx.BillingFxResolver.ResolvedFxRate;

public record CommercialRatingResult(
        Long billingContractId,
        Long contractPriceOverrideId,
        String serviceCode,
        String meterCode,
        String sourceCurrency,
        BigDecimal sourceBaseAmount,
        String billingCurrency,
        BigDecimal normalizedBaseAmount,
        long customerPriceBookVersionId,
        Long providerPriceBookVersionId,
        BigDecimal customerNetAmount,
        long taxRuleVersionId,
        String taxCode,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal grossAmount,
        BigDecimal providerCostAmount,
        BigDecimal marginAmount,
        ResolvedFxRate sourceFxRate,
        Instant ratedAt) {}
