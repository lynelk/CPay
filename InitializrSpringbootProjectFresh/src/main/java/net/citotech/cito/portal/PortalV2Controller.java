package net.citotech.cito.portal;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/portal", produces = "application/json")
public class PortalV2Controller {
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PortalV2Controller(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpSession session) {
        Map<String, Object> response = new LinkedHashMap<>();
        Object admin = session.getAttribute("user");
        Object merchant = session.getAttribute("merchantUser");
        response.put("authenticated", admin != null || merchant != null);
        if (admin instanceof User user) {
            response.put("type", "ADMIN");
            response.put("user", userInfo(user));
        } else if (merchant instanceof MerchantUser user) {
            response.put("type", "MERCHANT");
            Map<String, Object> info = userInfo(user);
            info.put("merchantId", user.getMerchant_id());
            response.put("user", info);
        }
        return response;
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboardSummary() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("merchants", scalarInt("SELECT COUNT(*) FROM merchants"));
        response.put("transactions", scalarInt("SELECT COUNT(*) FROM merchant_transactions_log"));
        response.put("payIns", scalarDecimal("SELECT COALESCE(SUM(original_amount),0) FROM merchant_transactions_log WHERE tx_type='PAYIN'"));
        response.put("payOuts", scalarDecimal("SELECT COALESCE(SUM(original_amount),0) FROM merchant_transactions_log WHERE tx_type='PAYOUT'"));
        response.put("pendingCallbacks", scalarInt("SELECT COUNT(*) FROM callback_tasks WHERE task_status IN ('PENDING','RETRY','PARKED')"));
        response.put("smsBatches", scalarInt("SELECT COUNT(*) FROM merchant_sms"));
        response.put("channelBalances", rows(
            "SELECT merchant_id, channel_code, gateway_id, currency, available_balance, ledger_balance, pending_balance "
                + "FROM merchant_channel_balances ORDER BY updated_at DESC LIMIT 50",
            new MapSqlParameterSource()
        ));
        response.put("recentNotifications", rows(
            "SELECT id, alert_type, alert_status, severity, reference_value, message, created_at "
                + "FROM operations_alerts ORDER BY id DESC LIMIT 20",
            new MapSqlParameterSource()
        ));
        return response;
    }

    @GetMapping("/merchants")
    public Map<String, Object> merchants(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int safeLimit = limit(limit);
        MapSqlParameterSource p = new MapSqlParameterSource("limit", safeLimit);
        List<Map<String, Object>> rows = rows(
            "SELECT id, name, short_name, account_number, account_type, status, created_by, created_on "
                + "FROM merchants ORDER BY id DESC LIMIT :limit",
            p
        );
        return listResponse(rows, safeLimit);
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        List<Map<String, Object>> rows = rows(
            "SELECT id, label, name, setting_value, description, setting_group FROM settings ORDER BY setting_group, label",
            new MapSqlParameterSource()
        );
        return listResponse(rows, rows.size());
    }

    @GetMapping("/transactions")
    public Map<String, Object> transactions(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int safeLimit = limit(limit);
        MapSqlParameterSource p = new MapSqlParameterSource("limit", safeLimit);
        List<Map<String, Object>> rows = rows(
            "SELECT id, merchant_id, gateway_id, original_amount, charges, status, tx_unique_id, tx_gateway_ref, "
                + "tx_merchant_ref, created_on, tx_type, payer_number, currency, callback_status "
                + "FROM merchant_transactions_log ORDER BY id DESC LIMIT :limit",
            p
        );
        return listResponse(rows, safeLimit);
    }

    @GetMapping("/sms")
    public Map<String, Object> sms(@RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
        int safeLimit = limit(limit);
        MapSqlParameterSource p = new MapSqlParameterSource("limit", safeLimit);
        List<Map<String, Object>> rows = rows(
            "SELECT id, merchant_id, total_recipients, status, smsgw, created_by, send_time, total_amount "
                + "FROM merchant_sms ORDER BY id DESC LIMIT :limit",
            p
        );
        return listResponse(rows, safeLimit);
    }

    private Map<String, Object> userInfo(User user) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.getId());
        info.put("name", user.getName());
        info.put("email", user.getEmail());
        info.put("phone", user.getPhone());
        return info;
    }

    private Map<String, Object> listResponse(List<Map<String, Object>> rows, int limit) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", rows);
        response.put("count", rows.size());
        response.put("limit", limit);
        return response;
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.queryForList(sql, parameters);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private Integer scalarInt(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
            return value == null ? 0 : value;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private BigDecimal scalarDecimal(String sql) {
        try {
            BigDecimal value = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), BigDecimal.class);
            return value == null ? BigDecimal.ZERO : value;
        } catch (DataAccessException e) {
            return BigDecimal.ZERO;
        }
    }

    private int limit(int requested) {
        if (requested < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
