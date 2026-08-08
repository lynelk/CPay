package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.util.List;

public record TierResult(BigDecimal totalCharge, List<TierStep> steps) {}
