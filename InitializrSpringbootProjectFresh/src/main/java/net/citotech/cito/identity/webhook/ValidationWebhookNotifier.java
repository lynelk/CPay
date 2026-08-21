package net.citotech.cito.identity.webhook;

import java.util.List;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Validation → merchant webhook bridge (Track B Phase 6). Enqueues normalized
 * {@code validation.*} events through the existing {@link MerchantWebhookService} so merchants
 * receive CPay-signed events for case/check transitions. The payload is deliberately
 * PII-minimal: case/check references, status, capability, and a normalized reason code — never
 * raw NINs, CRB bodies, or full identifiers. {@code eventReference} is stable per logical event
 * ({@code validation:<caseId>:<status>}), so a redelivery dedupes via the webhook table's unique
 * key instead of queueing duplicates.
 */
@Service
public class ValidationWebhookNotifier {

    private final MerchantWebhookService webhookService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ValidationWebhookNotifier(
            MerchantWebhookService webhookService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.webhookService = webhookService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Enqueues a validation event for the merchant's active endpoint(s) of {@code eventType}.
     * Returns the number of deliveries queued (0 = no active endpoint, which is fine — the event
     * is intentionally not retained).
     */
    public int notify(
            long merchantId,
            String eventType,
            String caseId,
            String merchantReference,
            String capability,
            String status,
            String reasonCode) {
        JSONObject payload = new JSONObject();
        payload.put("eventType", eventType);
        payload.put("merchantNumber", merchantNumber(merchantId));
        payload.put("caseId", blankToNull(caseId));
        payload.put("merchantReference", blankToNull(merchantReference));
        payload.put("status", status);
        payload.put("capability", blankToNull(capability));
        payload.put("reasonCode", blankToNull(reasonCode));
        String eventReference =
                "validation:"
                        + (caseId == null ? merchantReference : caseId)
                        + ":"
                        + (status == null ? "UPDATE" : status);
        return webhookService.enqueue(merchantId, eventType, eventReference, payload.toString());
    }

    private String merchantNumber(long merchantId) {
        try {
            List<String> rows =
                    jdbcTemplate.query(
                            "SELECT account_number FROM merchants WHERE id=:id LIMIT 1",
                            new MapSqlParameterSource("id", merchantId),
                            (rs, rowNum) -> rs.getString("account_number"));
            return rows.isEmpty() ? "" : rows.get(0);
        } catch (Exception e) {
            return "";
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
