package net.citotech.cito.vending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VendingPricingComparisonServiceTest {

    private VendingPricingComparisonService service;

    @BeforeEach
    void setUp() {
        service = new VendingPricingComparisonService(null, new ObjectMapper());
    }

    @Test
    void matchingPricingReturnsMatch() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":0,\"timeoutAmount\":0,"
                        + "\"timeoutDay\":0,\"autoRefund\":1,\"priceStrategy\":{}}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MATCH", result);
    }

    @Test
    void currencyMismatchDetected() {
        String providerJson =
                "{\"currency\":\"KES\",\"depositAmount\":20000,\"priceMinute\":2000}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void depositMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":25000,\"priceMinute\":2000}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void priceMinuteMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":3000}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void freeMinutesMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":5}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void dailyMaxPriceMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":50000}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void timeoutAmountMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":0,\"timeoutAmount\":10000}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void timeoutDayMismatchDetected() {
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":0,\"timeoutAmount\":0,"
                        + "\"timeoutDay\":10}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    @Test
    void nullProviderJsonReturnsUnknown() {
        String result = service.compare(standardPolicy(), null);
        assertEquals("UNKNOWN", result);
    }

    @Test
    void emptyProviderJsonReturnsUnknown() {
        String result = service.compare(standardPolicy(), "");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void malformedProviderJsonReturnsUnknown() {
        String result = service.compare(standardPolicy(), "not json");
        assertEquals("UNKNOWN", result);
    }

    @Test
    void providerJsonWithNoRelevantFieldsReturnsMatch() {
        String providerJson = "{\"name\":\"some device\"}";
        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MATCH", result);
    }

    @Test
    void dailyMaxPriceZeroIgnoredWhenPolicyNull() {
        VendingPricingPolicy policy = policyWithNullDailyCap();
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":50000,\"timeoutAmount\":0,"
                        + "\"timeoutDay\":0}";

        String result = service.compare(policy, providerJson);
        // Provider says 50000 but policy says null, so this should be a mismatch only if policy has a value
        // Since policy has null daily cap and provider has 50000, and we only compare when provider > 0 and policy != null
        // So this should be MATCH because policy.dailyCapAmount() is null
        assertEquals("MATCH", result);
    }

    @Test
    void timeoutAmountZeroIgnoredWhenPolicyNull() {
        VendingPricingPolicy policy = policyWithNullOvertime();
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":0,\"timeoutAmount\":10000,"
                        + "\"timeoutDay\":3}";

        String result = service.compare(policy, providerJson);
        // Policy overtimeAmount is null, provider timeoutAmount is 10000 (non-zero)
        // Code only compares when providerTimeout != 0 && policy.overtimeAmount() != null
        // So this should be MATCH because policy.overtimeAmount() is null
        assertEquals("MATCH", result);
    }

    @Test
    void timeoutDayZeroIgnoredWhenPolicyNull() {
        VendingPricingPolicy policy = policyWithNullOvertime();
        String providerJson =
                "{\"currency\":\"UGX\",\"depositAmount\":20000,\"priceMinute\":2000,"
                        + "\"freeMinutes\":0,\"dailyMaxPrice\":0,\"timeoutAmount\":0,"
                        + "\"timeoutDay\":5}";

        String result = service.compare(policy, providerJson);
        assertEquals("MATCH", result);
    }

    @Test
    void allFieldsMismatch() {
        String providerJson =
                "{\"currency\":\"KES\",\"depositAmount\":99999,\"priceMinute\":999,"
                        + "\"freeMinutes\":99,\"dailyMaxPrice\":999,\"timeoutAmount\":999,"
                        + "\"timeoutDay\":99}";

        String result = service.compare(standardPolicy(), providerJson);
        assertEquals("MISMATCH", result);
    }

    private VendingPricingPolicy standardPolicy() {
        return new VendingPricingPolicy(
                1L,
                1L,
                "CHARGENOW_UG",
                "UGX",
                new java.math.BigDecimal("20000"),
                0,
                new java.math.BigDecimal("2000"),
                60,
                1,
                null,
                null,
                null,
                "ORIGINAL_ROUTE");
    }

    private VendingPricingPolicy policyWithNullDailyCap() {
        return new VendingPricingPolicy(
                1L,
                1L,
                "CHARGENOW_UG",
                "UGX",
                new java.math.BigDecimal("20000"),
                0,
                new java.math.BigDecimal("2000"),
                60,
                1,
                null,
                null,
                null,
                "ORIGINAL_ROUTE");
    }

    private VendingPricingPolicy policyWithNullOvertime() {
        return new VendingPricingPolicy(
                1L,
                1L,
                "CHARGENOW_UG",
                "UGX",
                new java.math.BigDecimal("20000"),
                0,
                new java.math.BigDecimal("2000"),
                60,
                1,
                null,
                null,
                null,
                "ORIGINAL_ROUTE");
    }
}
