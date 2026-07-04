package net.citotech.cito;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight health/status endpoint for monitoring and load-balancer probes.
 * GET /status/health — returns HTTP 200 with a JSON status summary.
 */
@RestController
@RequestMapping(path = "/status")
public class HealthController {

    private static final Logger logger = Logger.getLogger(HealthController.class.getName());

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${custom.gatewaystate}")
    private String gatewaystate;

    @GetMapping(path = "/health", produces = "application/json")
    @CrossOrigin
    public String health() {
        JSONObject result = new JSONObject();
        try {
            // DB connectivity check
            boolean dbOk = checkDb();
            result.put("db", dbOk ? "UP" : "DOWN");

            // Pending (stuck) transactions older than 30 minutes
            long pendingCount = countPendingTransactions();
            result.put("pending_transactions", pendingCount);

            // Failed callbacks waiting for retry
            long failedCallbacks = countFailedCallbacks();
            result.put("failed_callbacks", failedCallbacks);

            // Gateway operating mode (live / sandbox)
            result.put("gateway_mode", gatewaystate != null ? gatewaystate : "unknown");

            String overallStatus = dbOk ? "UP" : "DOWN";
            result.put("status", overallStatus);
            result.put("code",   "000");
        } catch (JSONException e) {
            logger.log(Level.SEVERE, "Health check JSON error: " + e.getMessage(), e);
            try {
                result.put("status", "ERROR");
                result.put("code",   "102");
            } catch (JSONException ignored) {}
        }
        return result.toString();
    }

    private boolean checkDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", new MapSqlParameterSource(), Integer.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private long countPendingTransactions() {
        try {
            String sql = "SELECT COUNT(*) FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                    + " WHERE status='PENDING' AND created_on < DATE_SUB(NOW(), INTERVAL 30 MINUTE)";
            Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private long countFailedCallbacks() {
        try {
            String sql = "SELECT COUNT(*) FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                    + " WHERE callback_status='FAILED'";
            Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }
}

