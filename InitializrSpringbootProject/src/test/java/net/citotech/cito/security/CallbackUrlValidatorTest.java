package net.citotech.cito.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallbackUrlValidatorTest {

    @Test
    void testValidHttpsUrl() {
        assertNull(CallbackUrlValidator.validate("https://example.com/callback"));
    }

    @Test
    void testValidHttpUrl() {
        assertNull(CallbackUrlValidator.validate("http://example.com/callback"));
    }

    @Test
    void testNullUrl() {
        assertNotNull(CallbackUrlValidator.validate(null));
    }

    @Test
    void testEmptyUrl() {
        assertNotNull(CallbackUrlValidator.validate(""));
    }

    @Test
    void testLocalhostBlocked() {
        assertNotNull(CallbackUrlValidator.validate("http://localhost/callback"));
    }

    @Test
    void testLoopbackBlocked() {
        assertNotNull(CallbackUrlValidator.validate("http://127.0.0.1/callback"));
    }

    @Test
    void testMalformedUrl() {
        assertNotNull(CallbackUrlValidator.validate("not-a-url"));
    }

    @Test
    void testFtpProtocolBlocked() {
        assertNotNull(CallbackUrlValidator.validate("ftp://example.com/callback"));
    }
}
