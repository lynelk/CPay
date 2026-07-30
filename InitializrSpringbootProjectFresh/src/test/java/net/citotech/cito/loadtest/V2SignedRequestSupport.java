package net.citotech.cito.loadtest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit K5 (load-testing setup). Builds CPay API v2 request-signing headers
 * for Gatling simulations that exercise signed {@code /api/v2} endpoints.
 *
 * <p>This mirrors {@code net.citotech.cito.security.CanonicalRequestSigner}
 * and {@code net.citotech.cito.api.v2.V2RequestSecurityService} exactly, per
 * {@code Docs/Api-v2-signing.md}:
 *
 * <pre>
 * canonical = METHOD\nPATH\nCANONICAL_QUERY\nTIMESTAMP\nNONCE\nBODY_SHA256_HEX
 * signature = base64(SHA256withRSA(canonical, merchantPrivateKey))
 * </pre>
 *
 * <p><b>Important:</b> without a real merchant seeded in the target database
 * whose stored public key matches the private key used here, the backend
 * will correctly reject these requests with 401 ("Merchant was not found" or
 * "Invalid request signature") -- signature verification is working as
 * designed, it's just that no merchant/keypair exists to verify against in a
 * fresh sandbox. To exercise the success path, seed a merchant and point
 * {@code -Dcpay.loadtest.privateKeyPath} at the PKCS8 PEM private key whose
 * public counterpart was stored for that merchant number. Absent that
 * system property, an ephemeral RSA-2048 keypair is generated per JVM run
 * purely so the simulation is structurally runnable (it will always get 401
 * against an unseeded environment).
 */
final class V2SignedRequestSupport {

    private static final PrivateKey EPHEMERAL_PRIVATE_KEY = loadConfiguredOrEphemeralKey();

    private V2SignedRequestSupport() {
    }

    /**
     * Computes the full set of CPay v2 signature headers for a GET request with
     * no body and a single query parameter (the common shape of the read
     * endpoints this load test targets, e.g. {@code /api/v2/balances}).
     */
    static Map<String, String> getRequestHeaders(String merchantNumber,
                                                  String path,
                                                  String queryParamName,
                                                  String queryParamValue) {
        String timestamp = Instant.now().toString();
        String nonce = UUID.randomUUID().toString();
        String canonicalQuery = encode(queryParamName) + "=" + encode(queryParamValue);
        String canonical = "GET" + "\n"
                + path + "\n"
                + canonicalQuery + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + sha256Hex("");
        String signature = sign(canonical);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-CPay-Merchant-Number", merchantNumber);
        headers.put("X-CPay-Signature-Version", "v2");
        headers.put("X-CPay-Timestamp", timestamp);
        headers.put("X-CPay-Nonce", nonce);
        headers.put("X-CPay-Signature", signature);
        return headers;
    }

    private static String sign(String canonical) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(EPHEMERAL_PRIVATE_KEY);
            signer.update(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign canonical CPay v2 request string", e);
        }
    }

    private static PrivateKey loadConfiguredOrEphemeralKey() {
        String path = System.getProperty("cpay.loadtest.privateKeyPath");
        if (path == null || path.isBlank()) {
            return generateEphemeralKey();
        }
        try {
            String pem = Files.readString(Path.of(path), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(pem);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to load PKCS8 RSA private key from cpay.loadtest.privateKeyPath=" + path, e);
        }
    }

    private static PrivateKey generateEphemeralKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return keyPair.getPrivate();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate ephemeral RSA keypair for load testing", e);
        }
    }

    private static String sha256Hex(String value) {
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

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
