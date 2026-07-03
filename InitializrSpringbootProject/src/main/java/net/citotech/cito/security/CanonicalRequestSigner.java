package net.citotech.cito.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.Locale;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;

/**
 * Versioned request-signature verifier for /api/v2.
 *
 * Canonical format:
 * METHOD\nPATH\nTIMESTAMP\nNONCE\nBODY_SHA256_HEX
 */
public class CanonicalRequestSigner {
    public static final String SIGNATURE_VERSION = "v2";

    public static String canonicalize(String method,
                                      String path,
                                      String timestamp,
                                      String nonce,
                                      String body) {
        return safeUpper(method) + "\n"
                + safe(path) + "\n"
                + safe(timestamp) + "\n"
                + safe(nonce) + "\n"
                + sha256Hex(safe(body));
    }

    public static boolean verify(Merchant merchant,
                                 String canonical,
                                 String signatureBase64) {
        if (merchant == null || isBlank(merchant.getPublic_key()) || isBlank(signatureBase64)) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            String base64PublicKey = merchant.getPublic_key()
                    .replace("-----BEGIN PUBLIC KEY-----\n", "")
                    .replace("\n-----END PUBLIC KEY-----\n", "")
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("\n", "")
                    .trim();
            PublicKey publicKey = Common.getPublicKeyFromBase64String(base64PublicKey);
            if (publicKey == null) {
                return false;
            }
            verifier.initVerify(publicKey);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute SHA-256 hash", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeUpper(String value) {
        return safe(value).toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
