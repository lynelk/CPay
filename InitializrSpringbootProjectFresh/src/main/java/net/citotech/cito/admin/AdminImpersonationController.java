package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin impersonation (audit O4): start/end a time-boxed, audited "view as merchant" session, and
 * a read-only lookup a merchant-scoped read endpoint can use to resolve the impersonated merchant.
 *
 * <p>Lives under {@code /api/v2/admin/**}, which {@code SecurityConfig} already role-guards with
 * {@code hasRole("ADMIN")} at the filter-chain level (see {@code FeeScheduleAdminController} for
 * the same convention). {@link AdminImpersonationService#start} additionally enforces the
 * {@link AdminImpersonationService#PERMISSION_CODE} permission via the existing
 * {@link AdminPermissionService#require} pattern before anything is written.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/impersonation")
public class AdminImpersonationController {
    private final AdminImpersonationService impersonationService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminImpersonationController(AdminImpersonationService impersonationService,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.impersonationService = impersonationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(path = "/start")
    public Map<String, Object> start(@RequestBody Map<String, Object> body) {
        long adminUserId = requiredLong(body, "adminUserId");
        long merchantId = requiredLong(body, "merchantId");
        String reason = requiredString(body, "reason");

        long sessionId = impersonationService.start(adminUserId, merchantId, reason);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "000");
        response.put("sessionId", sessionId);
        response.put("merchantId", merchantId);
        response.put("ttlMinutes", AdminImpersonationService.DEFAULT_TTL.toMinutes());
        return response;
    }

    @PostMapping(path = "/end")
    public Map<String, Object> end(@RequestBody Map<String, Object> body) {
        long sessionId = requiredLong(body, "sessionId");
        long adminUserId = requiredLong(body, "adminUserId");
        Object reasonRaw = body.get("reason");

        impersonationService.end(sessionId, adminUserId, reasonRaw == null ? null : reasonRaw.toString());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "000");
        return response;
    }

    /**
     * Read-only resolution: does this admin currently have an active impersonation session, and
     * if so, for which merchant. Intended for merchant-scoped read endpoints (statement export,
     * balance, transaction status, ...) to consult instead of requiring a real merchant login -
     * never for anything that mutates state (see {@link AdminImpersonationService#requireNotImpersonating}).
     */
    @GetMapping(path = "/current")
    public Map<String, Object> current(@RequestParam("adminUserId") long adminUserId) {
        Optional<Long> merchantId = impersonationService.resolveImpersonatedMerchantId(adminUserId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("active", merchantId.isPresent());
        merchantId.ifPresent(id -> response.put("merchantId", id));
        return response;
    }

    /**
     * Demonstrates the read-only wiring described above end-to-end: resolves the merchant this
     * admin is actively impersonating (if any) and returns just enough non-sensitive information
     * to render a "viewing as {merchant}" banner. Returns {@code active: false} rather than an
     * error when there is no active session, since "not impersonating" is a normal, expected state.
     */
    @GetMapping(path = "/merchant-context")
    public Map<String, Object> merchantContext(@RequestParam("adminUserId") long adminUserId) {
        Optional<Long> merchantId = impersonationService.resolveImpersonatedMerchantId(adminUserId);
        Map<String, Object> response = new LinkedHashMap<>();
        if (merchantId.isEmpty()) {
            response.put("active", false);
            return response;
        }

        Map<String, Object> merchant = merchantSummary(merchantId.get());
        response.put("active", true);
        response.put("merchant", merchant);
        return response;
    }

    private Map<String, Object> merchantSummary(long merchantId) {
        String sql = "SELECT id, account_number, name, status FROM " + Common.DB_TABLE_MERCHANTS + " WHERE id = :id";
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("id", merchantId),
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("accountNumber", rs.getString("account_number"));
                row.put("name", rs.getString("name"));
                row.put("status", rs.getString("status"));
                return row;
            });
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Impersonated merchant " + merchantId + " was not found");
        }
        return rows.get(0);
    }

    private long requiredLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null) {
            throw new PaymentGatewayException(field + " is required");
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException ex) {
            throw new PaymentGatewayException(field + " must be a number");
        }
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.toString();
    }
}
