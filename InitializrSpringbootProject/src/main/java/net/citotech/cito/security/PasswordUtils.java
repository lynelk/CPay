package net.citotech.cito.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Utility class for password hashing and verification.
 *
 * <p>New passwords are stored as BCrypt hashes. Legacy SHA-256 hex hashes are
 * still verifiable so existing accounts keep working; the hash is upgraded to
 * BCrypt on the next successful login.
 */
public final class PasswordUtils {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    /** Length of a lowercase hex-encoded SHA-256 digest (64 characters). */
    private static final int SHA256_HEX_LENGTH = 64;

    private PasswordUtils() {}

    /**
     * Returns a BCrypt hash of the given raw password.
     */
    public static String hashPassword(String rawPassword) {
        return BCRYPT.encode(rawPassword);
    }

    /**
     * Verifies {@code rawPassword} against {@code storedHash}.
     *
     * <p>Supports:
     * <ul>
     *   <li>BCrypt hashes (start with {@code $2a$}, {@code $2b$}, or {@code $2y$})</li>
     *   <li>Legacy unsalted SHA-256 hex hashes (64 lowercase hex chars)</li>
     * </ul>
     */
    public static boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        if (isBcryptHash(storedHash)) {
            return BCRYPT.matches(rawPassword, storedHash);
        }
        if (isLegacySha256Hash(storedHash)) {
            return sha256Hex(rawPassword).equals(storedHash);
        }
        return false;
    }

    /**
     * Returns {@code true} when the stored hash is a legacy SHA-256 value that
     * should be upgraded to BCrypt on the next successful login.
     */
    public static boolean isLegacyHash(String storedHash) {
        return storedHash != null && isLegacySha256Hash(storedHash);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static boolean isBcryptHash(String hash) {
        return hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$");
    }

    private static boolean isLegacySha256Hash(String hash) {
        return hash.length() == SHA256_HEX_LENGTH && hash.matches("[0-9a-f]+");
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(SHA256_HEX_LENGTH);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }
}
