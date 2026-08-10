package net.citotech.cito.vending;

import java.math.BigDecimal;

/** Immutable pricing snapshot used by the vending rating engine. */
public record VendingPricingPolicy(
        long id,
        long merchantId,
        String policyCode,
        String currency,
        BigDecimal depositAmount,
        int freeMinutes,
        BigDecimal unitPrice,
        int billingBlockMinutes,
        int minimumBillingBlocks,
        BigDecimal dailyCapAmount,
        BigDecimal overtimeAmount,
        Integer overtimeDays,
        String refundMode) {}
