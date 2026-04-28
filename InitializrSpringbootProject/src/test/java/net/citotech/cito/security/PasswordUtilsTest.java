package net.citotech.cito.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void hashPasswordReturnsBcryptHash() {
        String hash = PasswordUtils.hashPassword("secret");
        assertTrue(hash.startsWith("$2a$"), "Hash should be BCrypt");
    }

    @Test
    void verifyPasswordBcryptRoundtrip() {
        String hash = PasswordUtils.hashPassword("myP@ssw0rd");
        assertTrue(PasswordUtils.verifyPassword("myP@ssw0rd", hash));
        assertFalse(PasswordUtils.verifyPassword("wrong", hash));
    }

    @Test
    void verifyPasswordLegacySha256() {
        // Pre-computed SHA-256 of "admin"
        String sha256OfAdmin = PasswordUtils.sha256Hex("admin");
        assertEquals(64, sha256OfAdmin.length());
        assertTrue(PasswordUtils.verifyPassword("admin", sha256OfAdmin));
        assertFalse(PasswordUtils.verifyPassword("wrong", sha256OfAdmin));
    }

    @Test
    void isLegacyHashDetectsCorrectly() {
        String sha256Hash = PasswordUtils.sha256Hex("test");
        assertTrue(PasswordUtils.isLegacyHash(sha256Hash));
        assertFalse(PasswordUtils.isLegacyHash(PasswordUtils.hashPassword("test")));
    }

    @Test
    void verifyPasswordReturnsFalseForNullOrEmpty() {
        assertFalse(PasswordUtils.verifyPassword(null, "somehash"));
        assertFalse(PasswordUtils.verifyPassword("pass", null));
        assertFalse(PasswordUtils.verifyPassword("pass", ""));
    }

    @Test
    void sha256HexProducesExpectedLength() {
        assertEquals(64, PasswordUtils.sha256Hex("").length());
        assertEquals(64, PasswordUtils.sha256Hex("hello world").length());
    }
}
