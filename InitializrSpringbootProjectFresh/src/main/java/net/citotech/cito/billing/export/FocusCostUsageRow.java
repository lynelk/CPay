package net.citotech.cito.billing.export;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cito FOCUS 1.4 Cost and Usage row. Mandatory FOCUS columns are represented explicitly; relevant
 * unit-pricing/usage columns are included because Cito meters and publishes versioned price books.
 * Cito provenance/cost extensions use the specification-required {@code x_} prefix.
 */
public record FocusCostUsageRow(
        BigDecimal billedCost,
        String billingAccountId,
        String billingAccountName,
        String billingCurrency,
        Instant billingPeriodEnd,
        Instant billingPeriodStart,
        String chargeCategory,
        String chargeClass,
        String chargeDescription,
        String chargeFrequency,
        Instant chargePeriodEnd,
        Instant chargePeriodStart,
        BigDecimal consumedQuantity,
        String consumedUnit,
        BigDecimal contractedCost,
        BigDecimal contractedUnitPrice,
        BigDecimal effectiveCost,
        String hostProviderName,
        String invoiceIssuerName,
        BigDecimal listCost,
        BigDecimal listUnitPrice,
        String pricingCategory,
        String pricingCurrency,
        BigDecimal pricingQuantity,
        String pricingUnit,
        String resourceId,
        String resourceName,
        String serviceProviderName,
        String serviceCategory,
        String serviceName,
        String skuId,
        String skuMeter,
        String skuPriceId,
        String tags,
        BigDecimal x_CitoProviderCost,
        Long x_CitoCustomerPriceBookVersionId,
        Long x_CitoProviderPriceBookVersionId,
        Long x_CitoUsageEventId) {}
