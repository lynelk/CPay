package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;

/** A row from {@code billing_price_components} (Flyway {@code V43}). */
public record PriceComponent(
        long id,
        long priceBookVersionId,
        String componentType,
        int sequenceNo,
        BigDecimal flatAmount,
        BigDecimal percentageRate,
        String tierDefinitionJson) {}
