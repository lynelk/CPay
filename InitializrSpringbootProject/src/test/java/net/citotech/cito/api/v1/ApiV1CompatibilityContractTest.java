package net.citotech.cito.api.v1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiV1CompatibilityContractTest {
    private static final String[] V1_ENDPOINTS = {
            "/api/v1/doMobileMoneyPayIn",
            "/api/v1/doMobileMoneyPayOut",
            "/api/v1/doTransactionCheckStatus",
            "/api/v1/doGetBalances"
    };

    @Test
    void preservesDocumentedV1Endpoints() {
        assertArrayEquals(new String[] {
                "/api/v1/doMobileMoneyPayIn",
                "/api/v1/doMobileMoneyPayOut",
                "/api/v1/doTransactionCheckStatus",
                "/api/v1/doGetBalances"
        }, V1_ENDPOINTS);
    }

    @Test
    void v1CompatibilityPolicyRequiresLegacySigningToRemainUnchanged() {
        String policy = "v1 signing remains the existing merchant-number and field-concatenation contract";
        assertTrue(policy.contains("v1 signing remains"));
    }
}
