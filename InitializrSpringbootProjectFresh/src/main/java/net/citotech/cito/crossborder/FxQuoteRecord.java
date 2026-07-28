package net.citotech.cito.crossborder;

import java.math.BigDecimal;
import java.time.Instant;

public record FxQuoteRecord(
    String quoteReference,
    long merchantId,
    String sourceCurrency,
    String targetCurrency,
    BigDecimal sourceAmount,
    BigDecimal targetAmount,
    BigDecimal rate,
    Instant expiresAt) {
}
