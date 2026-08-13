package net.citotech.cito;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges approved cross-border transfer records into payout-rail dispatch envelopes.
 *
 * <p>This service never submits live money movement itself. It creates an idempotent dispatch
 * envelope that a certified payout adapter/execution worker can pick up after provider gates are
 * satisfied.
 */
@Service
public class CrossBorderPayoutDispatcher {

    private final JdbcTemplate jdbcTemplate;

    public CrossBorderPayoutDispatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> dispatch(Long transferId, String requestedBy) {
        Map<String, Object> transfer =
                fetchOne(
                        "select xbt.id, xbt.transfer_reference, xbt.merchant_id, xbt.corridor_id, c.corridor_code, "
                                + "xbt.route_id, xbt.status, xbt.source_amount, xbt.destination_amount, "
                                + "xbt.destination_currency_code, c.destination_country_code, xbt.beneficiary_id, "
                                + "xbt.beneficiary_instrument_id "
                                + "from cross_border_transfers xbt join corridors c on c.id = xbt.corridor_id where xbt.id = ?",
                        transferId);

        String status = value(transfer, "status");
        if (!"APPROVED".equalsIgnoreCase(status)) {
            throw new IllegalStateException(
                    "Transfer must be APPROVED before dispatch; found " + status);
        }

        Map<String, Object> route = resolveRoute(transfer);
        String idempotencyKey = "XFER-" + transferId + "-" + value(route, "route_code");
        if (dispatchExists(idempotencyKey)) {
            throw new IllegalStateException(
                    "Transfer dispatch already exists for idempotency key " + idempotencyKey);
        }

        String payload = dispatchPayload(transfer, route);
        jdbcTemplate.update(
                "insert into cross_border_payout_rail_dispatches "
                        + "(transfer_id, merchant_id, corridor_code, route_code, payout_channel, payout_country, payout_currency, "
                        + "beneficiary_id, beneficiary_instrument_id, source_amount, destination_amount, merchant_reference, "
                        + "dispatch_status, idempotency_key, dispatch_payload, requested_by, created_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY_FOR_PROVIDER', ?, ?, ?, current_timestamp)",
                transferId,
                transfer.get("merchant_id"),
                transfer.get("corridor_code"),
                route.get("route_code"),
                route.get("delivery_method"),
                transfer.get("destination_country_code"),
                transfer.get("destination_currency_code"),
                transfer.get("beneficiary_id"),
                transfer.get("beneficiary_instrument_id"),
                transfer.get("source_amount"),
                transfer.get("destination_amount"),
                transfer.get("transfer_reference"),
                idempotencyKey,
                payload,
                requestedBy);
        Long dispatchId = lastInsertId();

        jdbcTemplate.update(
                "update cross_border_transfers set status = 'SUBMITTED_TO_PARTNER', submitted_at = current_timestamp, "
                        + "updated_at = current_timestamp where id = ? and status = 'APPROVED'",
                transferId);
        jdbcTemplate.update(
                "insert into cross_border_transfer_events (transfer_id, event_type, from_status, to_status, actor, notes) "
                        + "values (?, 'PAYOUT_RAIL_DISPATCH_CREATED', ?, 'SUBMITTED_TO_PARTNER', ?, ?)",
                transferId,
                status,
                requestedBy,
                "Created payout rail dispatch " + dispatchId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transferId", transferId);
        result.put("dispatchId", dispatchId);
        result.put("dispatchStatus", "READY_FOR_PROVIDER");
        result.put("idempotencyKey", idempotencyKey);
        return result;
    }

    public List<Map<String, Object>> pendingDispatches(int limit) {
        return jdbcTemplate.queryForList(
                "select id, transfer_id, corridor_code, route_code, payout_channel, payout_currency, destination_amount, "
                        + "dispatch_status, created_at from cross_border_payout_rail_dispatches "
                        + "where dispatch_status in ('READY_FOR_PROVIDER', 'RETRY_READY') order by created_at asc limit ?",
                safeLimit(limit));
    }

    @Transactional
    public Map<String, Object> markProviderSubmitted(
            Long dispatchId, String providerReference, String responsePayload) {
        Map<String, Object> dispatch =
                fetchOne(
                        "select id, transfer_id, dispatch_status from cross_border_payout_rail_dispatches where id = ?",
                        dispatchId);
        String status = value(dispatch, "dispatch_status");
        if (!"READY_FOR_PROVIDER".equalsIgnoreCase(status)
                && !"RETRY_READY".equalsIgnoreCase(status)) {
            throw new IllegalStateException(
                    "Dispatch is not ready for provider submission; found " + status);
        }
        int updated =
                jdbcTemplate.update(
                        "update cross_border_payout_rail_dispatches set dispatch_status = 'SUBMITTED', provider_reference = ?, "
                                + "response_payload = ?, dispatched_at = current_timestamp where id = ? "
                                + "and dispatch_status in ('READY_FOR_PROVIDER', 'RETRY_READY')",
                        providerReference,
                        responsePayload,
                        dispatchId);
        if (updated != 1) {
            throw new IllegalStateException("Dispatch could not be marked submitted");
        }
        jdbcTemplate.update(
                "update cross_border_transfers set provider_reference = coalesce(?, provider_reference), "
                        + "updated_at = current_timestamp where id = ?",
                providerReference,
                dispatch.get("transfer_id"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dispatchId", dispatchId);
        result.put("status", "SUBMITTED");
        result.put("providerReference", providerReference);
        result.put("updatedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> resolveRoute(Map<String, Object> transfer) {
        Object routeId = transfer.get("route_id");
        if (routeId != null) {
            return fetchOne(
                    "select route_code, provider_code, delivery_method from corridor_routes "
                            + "where id = ? and enabled = true",
                    routeId);
        }
        return fetchOne(
                "select route_code, provider_code, delivery_method from corridor_routes "
                        + "where corridor_id = ? and enabled = true order by priority asc, id asc limit 1",
                transfer.get("corridor_id"));
    }

    private boolean dispatchExists(String idempotencyKey) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "select count(*) from cross_border_payout_rail_dispatches where idempotency_key = ?",
                        Integer.class,
                        idempotencyKey);
        return count != null && count > 0;
    }

    private Map<String, Object> fetchOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No record found for requested dispatch operation");
        }
        return rows.get(0);
    }

    private Long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 250));
    }

    private static String dispatchPayload(Map<String, Object> transfer, Map<String, Object> route) {
        return "{"
                + "\"transferId\":"
                + transfer.get("id")
                + ","
                + "\"merchantId\":"
                + transfer.get("merchant_id")
                + ","
                + "\"corridorCode\":\""
                + value(transfer, "corridor_code")
                + "\","
                + "\"routeCode\":\""
                + value(route, "route_code")
                + "\","
                + "\"channel\":\""
                + value(route, "delivery_method")
                + "\","
                + "\"country\":\""
                + value(transfer, "destination_country_code")
                + "\","
                + "\"currency\":\""
                + value(transfer, "destination_currency_code")
                + "\","
                + "\"destinationAmount\":\""
                + transfer.get("destination_amount")
                + "\","
                + "\"merchantReference\":\""
                + value(transfer, "transfer_reference")
                + "\""
                + "}";
    }

    private static String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
