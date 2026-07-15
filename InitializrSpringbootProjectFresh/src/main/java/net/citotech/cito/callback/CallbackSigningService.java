package net.citotech.cito.callback;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CallbackSigningService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String callbackSecret;
    private final SecureRandom secureRandom = new SecureRandom();

    public CallbackSigningService(NamedParameterJdbcTemplate jdbcTemplate,
                                  @Value("${callback.signing.secret}") String callbackSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.callbackSecret = callbackSecret;
    }

    public SignedCallback sign(CallbackTask task) {
        String nonce = nonce();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String base = task.id + "\n" + task.merchantId + "\n" + task.referenceValue + "\n" + timestamp + "\n" + nonce + "\n" + task.requestBody;
        String signature = hmac(base);
        save(task.id, signature, nonce);
        return new SignedCallback(signature, nonce, timestamp);
    }

    private String nonce() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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

