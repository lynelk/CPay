package net.citotech.cito.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CanonicalRequestSignerTest {
    @Test
    void canonicalizeBuildsExpectedV2Payload() {
        String body = "{\"merchantNumber\":\"M001\"}";
        String canonical = CanonicalRequestSigner.canonicalize(
                "post",
                "/api/v2/payments/collect",
                "2026-07-03T08:00:00Z",
                "nonce-1",
                body);

        String expected = "POST\n"
                + "/api/v2/payments/collect\n"
                + "2026-07-03T08:00:00Z\n"
                + "nonce-1\n"
                + CanonicalRequestSigner.sha256Hex(body);
        assertEquals(expected, canonical);
    }
}
