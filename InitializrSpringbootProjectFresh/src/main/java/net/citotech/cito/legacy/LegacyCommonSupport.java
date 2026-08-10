package net.citotech.cito.legacy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Pure, side-effect-free compatibility helpers extracted from the legacy Common god class.
 *
 * <p>This component intentionally has no database, HTTP, Spring Security, transaction-manager or
 * provider dependencies. It gives legacy callers a small deterministic surface that can be tested
 * independently while Common remains the public compatibility facade for v1 code.
 */
@Component
public class LegacyCommonSupport {
    private static final String NUMERIC = "0123456789";
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random;

    public LegacyCommonSupport() {
        this(new SecureRandom());
    }

    LegacyCommonSupport(SecureRandom random) {
        this.random = random;
    }

    public String jsonText(JSONObject object, String key, String defaultValue) {
        if (object == null || key == null || object.isNull(key)) {
            return defaultValue;
        }
        Object value = object.opt(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    public String randomNumericString(int count) {
        return randomString(count, NUMERIC);
    }

    public String randomAlphaNumericString(int count) {
        return randomString(count, ALPHANUMERIC);
    }

    public String urlEncodeValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public BigDecimal decimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid decimal value", ex);
        }
    }

    public BigDecimal money(String value, int scale) {
        return decimal(value).setScale(scale, RoundingMode.HALF_UP);
    }

    public boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String randomString(int count, String alphabet) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }
}
