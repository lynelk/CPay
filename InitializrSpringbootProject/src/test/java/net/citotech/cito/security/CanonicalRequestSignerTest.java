package net.citotech.cito.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CanonicalRequestSignerTest {
    @Test
    void canonicalizeBuildsExpectedV2PayloadWithoutQuery() {
        String body = "{\"merchantNumber\":\"M001\"}";
        String canonical = CanonicalRequestSigner.canonicalize(
                "post",
                "/api/v2/payments/collect",
                "2026-07-03T08:00:00Z",
                "nonce-1",
                body);

        String expected = "POST\n"
                + "/api/v2/payments/collect\n"
                + "\n"
                + "2026-07-03T08:00:00Z\n"
                + "nonce-1\n"
                + CanonicalRequestSigner.sha256Hex(body);
        assertEquals(expected, canonical);
    }

    @Test
    void canonicalizeIncludesSortedQueryLine() {
        String canonical = CanonicalRequestSigner.canonicalize(
                "get",
                "/api/v2/balances",
                "merchantNumber=M001",
                "2026-07-03T08:00:00Z",
                "nonce-2",
                "");

        String expected = "GET\n"
                + "/api/v2/balances\n"
                + "merchantNumber=M001\n"
                + "2026-07-03T08:00:00Z\n"
                + "nonce-2\n"
                + CanonicalRequestSigner.sha256Hex("");
        assertEquals(expected, canonical);
    }
}
