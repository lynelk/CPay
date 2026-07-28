package net.citotech.cito.callback;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CallbackSigningService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CallbackSecretService callbackSecretService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CallbackSigningService(NamedParameterJdbcTemplate jdbcTemplate,
                                  CallbackSecretService callbackSecretService) {
        this.jdbcTemplate = jdbcTemplate;
        this.callbackSecretService = callbackSecretService;
    }

    public SignedCallback sign(CallbackTask task) {
        String nonce = nonce();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String base = task.id + "\n" + task.merchantId + "\n" + task.referenceValue + "\n" + timestamp + "\n" + nonce + "\n" + task.requestBody;
        // Sign with this merchant's own active secret (falls back to the global secret only
        // when the merchant has never rotated one) so one merchant can't forge callbacks
        // addressed to another merchant that happens to know the old shared global secret.
        String signature = hmac(base, callbackSecretService.activeSecret(task.merchantId));
        save(task.id, signature, nonce);
        return new SignedCallback(signature, nonce, timestamp);
    }

    private String nonce() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hmac(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign callback", e);
        }
    }

    private void save(long taskId, String signature, String nonce) {
        String sql = "INSERT INTO callback_delivery_signatures (callback_task_id, signature_algorithm, signature_value, nonce) VALUES (:task_id, 'HMAC-SHA256', :signature, :nonce)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("task_id", taskId);
        p.addValue("signature", signature);
        p.addValue("nonce", nonce);
        jdbcTemplate.update(sql, p);
    }

    public static class SignedCallback {
        public final String signature;
        public final String nonce;
        public final String timestamp;

        public SignedCallback(String signature, String nonce, String timestamp) {
            this.signature = signature;
            this.nonce = nonce;
            this.timestamp = timestamp;
        }
    }
}

