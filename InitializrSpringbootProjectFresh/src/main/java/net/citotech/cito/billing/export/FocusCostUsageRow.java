package net.citotech.cito.billing.export;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cito's FOCUS 1.4 cost-and-usage export row. Standard FOCUS columns are represented first; Cito
 * provenance/cost fields use the required {@code x_} custom-column prefix.
 *
 * <p>This is a deliberately small, truthful subset of the FOCUS 1.4 Cost and Usage dataset. It is
 * not labelled fully conformant until automated validation against the published FOCUS schema is
 * part of CI.
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
        BigDecimal effectiveCost,
        String pricingCategory,
        BigDecimal pricingQuantity,
        String pricingUnit,
        String resourceId,
        String resourceName,
        String serviceProviderName,
        String serviceCategory,
        String serviceName,
        String skuId,
        String skuMeter,
        String tags,
        BigDecimal x_CitoProviderCost,
        Long x_CitoCustomerPriceBookVersionId,
        Long x_CitoProviderPriceBookVersionId,
        Long x_CitoUsageEventId) {}
