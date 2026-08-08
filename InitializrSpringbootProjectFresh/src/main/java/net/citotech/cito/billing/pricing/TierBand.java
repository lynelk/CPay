package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;

/**
 * One graduated/marginal tier band: {@code rate} applies only to the slice of the base amount
 * between the previous band's upper bound and this band's {@code upToInclusive} - {@code null}
 * means open-ended (the final band).
 */
public record TierBand(BigDecimal upToInclusive, BigDecimal rate) {}
