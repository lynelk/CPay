package net.citotech.cito.billing.pricing;

import java.time.Instant;

/** A row from {@code billing_price_book_versions} (Flyway {@code V43}). */
public record PriceBookVersion(
        long id,
        Long billingTenantId,
        String serviceCode,
        String meterCode,
        String chargeType,
        String currency,
        int versionNo,
        Instant effectiveFrom,
        Instant effectiveTo) {}
