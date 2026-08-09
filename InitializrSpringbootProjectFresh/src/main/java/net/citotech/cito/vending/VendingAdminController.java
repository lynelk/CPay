package net.citotech.cito.vending;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cross-tenant operator view for vending rollout, device health and callback evidence. */
@RestController
@RequestMapping(path = "/api/v2/admin/vending")
@PreAuthorize("hasRole('ADMIN')")
public class VendingAdminController {
    private final NamedParameterJdbcTemplate jdbc;
    private final VendingConnectorConfigurationService configurations;
    private final VendingHostedRentalService hosted;
    private final String appBaseUrl;

    public VendingAdminController(
            NamedParameterJdbcTemplate jdbc,
            VendingConnectorConfigurationService configurations,
            VendingHostedRentalService hosted,
            @Value("${app.base-url:}") String appBaseUrl) {
        this.jdbc = jdbc;
        this.configurations = configurations;
        this.hosted = hosted;
        this.appBaseUrl = appBaseUrl;
    }

    @GetMapping(path = "/overview")
    public Map<String, Object> overview(
            @RequestParam(name = "merchantId", required = false) Long merchantId) {
        String where = merchantId == null || merchantId <= 0 ? "" : " WHERE merchant_id=:merchant_id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (!where.isEmpty()) p.addValue("merchant_id", merchantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("locations", count("vending_locations", where, p));
        result.put("devices", count("vending_devices", where, p));
        result.put("assets", count("vending_assets", where, p));
        result.put("rentals", count("vending_rentals", where, p));
        result.put("activeRentals", countWhere("vending_rentals", where, p, "status='ACTIVE'"));
        result.put("pendingPayments", countWhere("vending_rentals", where, p, "status='PAYMENT_PENDING'"));
        result.put("refundPending", countWhere("vending_rentals", where, p, "status IN ('REFUND_PENDING','REFUND_FAILED')"));
        result.put("offlineDevices", countWhere("vending_devices", where, p, "status='OFFLINE'"));
        result.put("failedCallbacks", countWhere("vending_device_callbacks", where, p, "processing_status='FAILED'"));
        result.put("recentRentals", recentRentals(merchantId, 50));
        return result;
    }

    @GetMapping(path = "/events")
    public List<Map<String, Object>> events(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int bounded = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        MapSqlParameterSource p = new MapSqlParameterSource("limit", bounded);
        String where = "";
        if (merchantId != null && merchantId > 0) {
            where = " WHERE merchant_id=:merchant_id";
            p.addValue("merchant_id", merchantId);
        }
        return jdbc.queryForList(
                "SELECT id, merchant_id, event_type, entity_type, entity_reference, actor, amount, currency, detail_json, created_at "
                        + "FROM vending_events" + where + " ORDER BY id DESC LIMIT :limit",
                p);
    }

    @GetMapping(path = "/callbacks")
    public List<Map<String, Object>> callbacks(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int bounded = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        MapSqlParameterSource p = new MapSqlParameterSource("limit", bounded);
        String where = "";
        if (merchantId != null && merchantId > 0) {
            where = " WHERE merchant_id=:merchant_id";
            p.addValue("merchant_id", merchantId);
        }
        return jdbc.queryForList(
                "SELECT id, merchant_id, connector_code, external_event_id, external_device_id, event_type, "
                        + "signature_status, processing_status, error_message, created_at, processed_at "
                        + "FROM vending_device_callbacks" + where + " ORDER BY id DESC LIMIT :limit",
                p);
    }

    @GetMapping(path = "/commands")
    public List<Map<String, Object>> commands(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int bounded = Math.max(1, Math.min(limit == null ? 100 : limit, 500));
        MapSqlParameterSource p = new MapSqlParameterSource("limit", bounded);
        String where = "";
        if (merchantId != null && merchantId > 0) {
            where = " WHERE merchant_id=:merchant_id";
            p.addValue("merchant_id", merchantId);
        }
        return jdbc.queryForList(
                "SELECT id, merchant_id, device_id, rental_id, command_reference, command_type, connector_code, "
                        + "status, provider_reference, created_at, completed_at FROM vending_commands"
                        + where + " ORDER BY id DESC LIMIT :limit",
                p);
    }

    @GetMapping(path = "/connectors/{merchantId}")
    public List<Map<String, Object>> connectors(@PathVariable("merchantId") long merchantId) {
        return configurations.list(merchantId);
    }

    @PostMapping(path = "/connectors/{merchantId}/{connectorCode}")
    public ResponseEntity<?> saveConnector(
            @PathVariable("merchantId") long merchantId,
            @PathVariable("connectorCode") String connectorCode,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(configurations.save(merchantId, connectorCode, body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "VENDING_CONNECTOR_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping(path = "/connectors/{merchantId}/{connectorCode}/rotate-callback-secret")
    public ResponseEntity<?> rotateCallbackSecret(
            @PathVariable("merchantId") long merchantId,
            @PathVariable("connectorCode") String connectorCode) {
        try {
            return ResponseEntity.ok(configurations.rotateCallbackSecret(merchantId, connectorCode));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "VENDING_CONNECTOR_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping(path = "/devices/{merchantId}/{deviceCode}/rotate-public-token")
    public ResponseEntity<?> rotateDeviceToken(
            @PathVariable("merchantId") long merchantId,
            @PathVariable("deviceCode") String deviceCode) {
        try {
            return ResponseEntity.ok(hosted.rotateDevicePublicToken(merchantId, deviceCode, appBaseUrl));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("code", "VENDING_DEVICE_REJECTED", "message", e.getMessage()));
        }
    }

    private long count(String table, String where, MapSqlParameterSource p) {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + where, p, Long.class);
        return result == null ? 0 : result;
    }

    private long countWhere(
            String table, String tenantWhere, MapSqlParameterSource p, String condition) {
        String where = tenantWhere.isEmpty() ? " WHERE " + condition : tenantWhere + " AND " + condition;
        return count(table, where, p);
    }

    private List<Map<String, Object>> recentRentals(Long merchantId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        String where = "";
        if (merchantId != null && merchantId > 0) {
            where = " WHERE r.merchant_id=:merchant_id";
            p.addValue("merchant_id", merchantId);
        }
        return jdbc.queryForList(
                "SELECT r.id, r.merchant_id, r.rental_reference, d.device_code, r.customer_mask, r.currency, "
                        + "r.deposit_amount, r.usage_amount, r.refund_amount, r.surcharge_created, r.status, "
                        + "r.started_at, r.ended_at, r.created_at FROM vending_rentals r "
                        + "LEFT JOIN vending_devices d ON d.id=r.device_id AND d.merchant_id=r.merchant_id"
                        + where + " ORDER BY r.id DESC LIMIT :limit",
                p);
    }
}
