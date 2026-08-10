package net.citotech.cito.communication.ussd;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Session-oriented USSD channel. The default journey is deliberately small and configurable; it
 * establishes provider/session plumbing without embedding payment business rules in menu code.
 */
@Service
public class UssdSessionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public UssdSessionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UssdSessionResponse process(UssdSessionRequest request) {
        String input = request.input().trim();
        String action;
        String message;
        if (input.isEmpty()) {
            action = "CON";
            message =
                    setting(
                            request.merchantId(),
                            "ussd_menu_text",
                            "Welcome to CPay\n1. Help\n2. Exit");
        } else if (lastSelection(input).equals("1")) {
            action = "END";
            message =
                    setting(
                            request.merchantId(),
                            "ussd_help_text",
                            "Your request has been received. Please contact the merchant for payment support.");
        } else {
            action = "END";
            message = setting(request.merchantId(), "ussd_exit_text", "Thank you for using CPay.");
        }

        persist(request, action, message);
        return new UssdSessionResponse(request.sessionId(), action, message);
    }

    public List<Map<String, Object>> recentSessions(Long merchantId) {
        String where = merchantId == null ? "" : " WHERE merchant_id = :merchantId";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("merchantId", merchantId);
        return jdbcTemplate.query(
                "SELECT id, session_id, merchant_id, msisdn_hash, last_input, response_text,"
                        + " session_status, created_at, updated_at FROM communication_ussd_sessions"
                        + where
                        + " ORDER BY updated_at DESC LIMIT 200",
                params,
                (rs, rowNum) ->
                        Map.<String, Object>ofEntries(
                                Map.entry("id", rs.getLong("id")),
                                Map.entry("sessionId", rs.getString("session_id")),
                                Map.entry("merchantId", rs.getLong("merchant_id")),
                                Map.entry("msisdnHash", rs.getString("msisdn_hash")),
                                Map.entry("lastInput", nullToEmpty(rs.getString("last_input"))),
                                Map.entry("responseText", rs.getString("response_text")),
                                Map.entry("status", rs.getString("session_status")),
                                Map.entry("createdAt", String.valueOf(rs.getTimestamp("created_at"))),
                                Map.entry("updatedAt", String.valueOf(rs.getTimestamp("updated_at")))));
    }

    private void persist(UssdSessionRequest request, String action, String message) {
        MapSqlParameterSource params =
                new MapSqlParameterSource()
                        .addValue("sessionId", request.sessionId())
                        .addValue("merchantId", request.merchantId())
                        .addValue("msisdnHash", sha256(request.msisdn()))
                        .addValue("lastInput", request.input())
                        .addValue("responseText", message)
                        .addValue("status", "CON".equals(action) ? "ACTIVE" : "ENDED");
        jdbcTemplate.update(
                "INSERT INTO communication_ussd_sessions"
                        + " (session_id, merchant_id, msisdn_hash, last_input, response_text, session_status)"
                        + " VALUES (:sessionId, :merchantId, :msisdnHash, :lastInput, :responseText, :status)"
                        + " ON DUPLICATE KEY UPDATE merchant_id = VALUES(merchant_id),"
                        + " msisdn_hash = VALUES(msisdn_hash), last_input = VALUES(last_input),"
                        + " response_text = VALUES(response_text), session_status = VALUES(session_status),"
                        + " updated_at = CURRENT_TIMESTAMP",
                params);
    }

    private String setting(long merchantId, String name, String fallback) {
        Setting setting = Common.getMerchantSettings(name, merchantId, jdbcTemplate);
        if (setting == null || setting.getSetting_value() == null || setting.getSetting_value().isBlank()) {
            return fallback;
        }
        return setting.getSetting_value();
    }

    private String lastSelection(String input) {
        String[] selections = input.split("\\*");
        return selections.length == 0 ? input : selections[selections.length - 1].trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
