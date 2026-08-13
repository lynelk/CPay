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
 * <p>This class does not invent provider credentials or private payout contracts. It uses configured
 * corridor route metadata and beneficiary instruments to create an idempotent dispatch record that the
 * provider adapter/execution worker can submit through the existing payout path. That keeps cross-border
 * orchestration auditable while still respecting provider certification boundaries. Humanity may yet recover.
 */
@Service
public class CrossBorderPayoutDispatcher {

    private final JdbcTemplate jdbcTemplate;

    public CrossBorderPayoutDispatcher(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> dispatch(Long transferId, String requestedBy) {
        Map<String, Object> transfer = fetchOne(
            "select id, merchant_id, corridor_code, status, source_amount, destination_amount, destination_currency, "
                + "beneficiary_id, beneficiary_instrument_id, merchant_reference "
                + "from cross_border_transfers where id = ?",
            transferId
        );

        String status = value(transfer, "status");
        if ("COMPLIANCE_HOLD".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            throw new IllegalStateException("Transfer cannot be dispatched while in status " + status);
        }

        String corridorCode = value(transfer, "corridor_code");
        Map<String, Object> route = fetchOne(
            "select route_code, payout_channel, destination_country, destination_currency "
                + "from corridor_routes where corridor_code = ? and route_status = 'ACTIVE' "
                + "order by priority asc, id asc limit 1",
            corridorCode
        );

        String idempotencyKey = "XFER-" + transferId + "-" + value(route, "route_code");
        String payload = dispatchPayload(transfer, route);

        Long dispatchId = jdbcTemplate.queryForObject(
            "insert into cross_border_payout_rail_dispatches "
                + "(transfer_id, merchant_id, corridor_code, route_code, payout_channel, payout_country, payout_currency, "
                + " beneficiary_id, beneficiary_instrument_id, source_amount, destination_amount, merchant_reference, "
                + " dispatch_status, idempotency_key, dispatch_payload, requested_by, created_at) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'READY_FOR_PROVIDER', ?, ?, ?, current_timestamp) "
                + "on conflict (idempotency_key) do update set dispatch_payload = excluded.dispatch_payload "
                + "returning id",
            Long.class,
            transferId,
            transfer.get("merchant_id"),
            corridorCode,
            route.get("route_code"),
            route.get("payout_channel"),
            route.get("destination_country"),
            route.get("destination_currency"),
            transfer.get("beneficiary_id"),
            transfer.get("beneficiary_instrument_id"),
            transfer.get("source_amount"),
            transfer.get("destination_amount"),
            transfer.get("merchant_reference"),
            idempotencyKey,
            payload,
            requestedBy
        );

        jdbcTemplate.update(
            "update cross_border_transfers set status = 'SUBMITTED_TO_PARTNER', updated_at = current_timestamp where id = ?",
            transferId
        );
        jdbcTemplate.update(
            "insert into cross_border_transfer_events (transfer_id, event_type, from_status, to_status, event_note, created_by) "
                + "values (?, 'PAYOUT_RAIL_DISPATCH_CREATED', ?, 'SUBMITTED_TO_PARTNER', ?, ?)",
            transferId,
            status,
            "Created payout rail dispatch " + dispatchId,
            requestedBy
        );

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
            limit
        );
    }

    @Transactional
    public Map<String, Object> markProviderSubmitted(Long dispatchId, String providerReference, String responsePayload) {
        jdbcTemplate.update(
            "update cross_border_payout_rail_dispatches set dispatch_status = 'SUBMITTED', provider_reference = ?, "
                + "response_payload = ?, dispatched_at = current_timestamp where id = ?",
            providerReference,
            responsePayload,
            dispatchId
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dispatchId", dispatchId);
        result.put("status", "SUBMITTED");
        result.put("providerReference", providerReference);
        result.put("updatedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> fetchOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("No record found for requested dispatch operation");
        }
        return rows.get(0);
    }

    private static String dispatchPayload(Map<String, Object> transfer, Map<String, Object> route) {
        return "{"
            + "\"transferId\":" + transfer.get("id") + ","
            + "\"merchantId\":" + transfer.get("merchant_id") + ","
            + "\"corridorCode\":\"" + value(transfer, "corridor_code") + "\"," 
            + "\"routeCode\":\"" + value(route, "route_code") + "\"," 
            + "\"channel\":\"" + value(route, "payout_channel") + "\"," 
            + "\"country\":\"" + value(route, "destination_country") + "\"," 
            + "\"currency\":\"" + value(route, "destination_currency") + "\"," 
            + "\"destinationAmount\":\"" + transfer.get("destination_amount") + "\"," 
            + "\"merchantReference\":\"" + value(transfer, "merchant_reference") + "\""
            + "}";
    }

    private static String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
