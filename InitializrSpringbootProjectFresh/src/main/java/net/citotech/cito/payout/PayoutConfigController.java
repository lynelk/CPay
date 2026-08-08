package net.citotech.cito.payout;

import java.util.Map;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.payout.PayoutConfigService.ConfigRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin self-service surface for {@code payout_controls} (audit finding: the V34 limits could only
 * be configured by SQL). Backed by {@link PayoutConfigService}; every write is audited. The surface
 * is gated by the {@code payout-controls-config} feature flag — when off, the endpoints answer 400
 * with {@code PAYOUT_CONTROLS_CONFIG_DISABLED}.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/payout-controls")
@PreAuthorize("hasRole('ADMIN')")
public class PayoutConfigController {

    private final PayoutConfigService configService;
    private final AdminAuditService auditService;

    public PayoutConfigController(
            PayoutConfigService configService, AdminAuditService auditService) {
        this.configService = configService;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "merchantId", required = false) String merchantFilter) {
        try {
            return ResponseEntity.ok(configService.list(merchantFilter));
        } catch (PayoutConfigDisabledException e) {
            return disabledResponse(e);
        }
    }

    /** Inserts or updates a control row; returns the saved row. */
    @PostMapping
    public ResponseEntity<?> upsert(@RequestBody Map<String, Object> body) {
        ConfigRequest request = toRequest(body);
        if (request == null) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "PAYOUT_CONTROLS_CONFIG_BAD_REQUEST",
                                    "message",
                                    "Request body could not be read"));
        }
        Map<String, Object> saved = configService.upsert(request);
        auditService.record(
                "PAYOUT_CONTROLS",
                "PAYOUT_CONTROL_UPSERT",
                String.valueOf(saved.get("id")),
                actor(request));
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        try {
            int updated = configService.delete(id);
            if (updated == 0) {
                return ResponseEntity.badRequest()
                        .body(
                                Map.of(
                                        "code",
                                        "PAYOUT_CONTROLS_CONFIG_NOT_FOUND",
                                        "message",
                                        "Payout control row not found: " + id));
            }
            auditService.record(
                    "PAYOUT_CONTROLS", "PAYOUT_CONTROL_DELETE", "rowId:" + id, "system");
            return ResponseEntity.ok(Map.of("code", "000", "id", id, "deleted", updated));
        } catch (PayoutConfigDisabledException e) {
            return disabledResponse(e);
        }
    }

    private ConfigRequest toRequest(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        try {
            return new ConfigRequest(
                    longValue(body.get("merchantId")),
                    stringValue(body.get("channelCode")),
                    stringValue(body.get("currency")),
                    stringValue(body.get("country")),
                    decimalValue(body.get("dailyAmountLimit")),
                    decimalValue(body.get("monthlyAmountLimit")),
                    decimalValue(body.get("perTransactionLimit")),
                    integerValue(body.get("beneficiaryVelocityLimit")),
                    stringValue(body.get("approvalRequiredFlag")),
                    stringValue(body.get("enabledFlag")),
                    stringValue(body.get("changedBy")));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> disabledResponse(PaymentGatewayException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "PAYOUT_CONTROLS_CONFIG_DISABLED", "message", e.getMessage()));
    }

    private String actor(ConfigRequest request) {
        return request.changedBy() == null || request.changedBy().isBlank()
                ? "system"
                : request.changedBy().trim();
    }

    private static Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Long.valueOf(text);
    }

    private static Integer integerValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : Integer.valueOf(text);
    }

    private static java.math.BigDecimal decimalValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return new java.math.BigDecimal(number.toString());
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : new java.math.BigDecimal(text);
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
