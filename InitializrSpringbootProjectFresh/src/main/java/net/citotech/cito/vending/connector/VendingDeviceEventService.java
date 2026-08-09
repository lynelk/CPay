package net.citotech.cito.vending.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.VendingRentalService;
import net.citotech.cito.vending.VendingRepository;
import net.citotech.cito.vending.connector.VendingCallbackCorrelationService.Mapping;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Applies already-authenticated manufacturer events to tenant-scoped vending state.
 *
 * <p>This method deliberately does not wrap callback registration, domain application and the
 * final audit update in one transaction. A failed domain mutation must still leave a durable
 * VERIFIED/FAILED callback row for operations and replay analysis instead of rolling the evidence
 * back with the failed state change.
 */
@Service
public class VendingDeviceEventService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VendingRentalService rentals;
    private final VendingRepository repository;
    private final VendingCallbackCorrelationService correlations;

    public VendingDeviceEventService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            VendingRentalService rentals,
            VendingRepository repository,
            VendingCallbackCorrelationService correlations) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.rentals = rentals;
        this.repository = repository;
        this.correlations = correlations;
    }

    public Map<String, Object> process(long merchantId, Contract contract, String rawBody) {
        JsonNode body = parse(rawBody);
        String externalEventId =
                required(valueAt(body, contract.callbackEventIdField()), "callback event id");
        String externalDeviceId =
                required(valueAt(body, contract.callbackDeviceField()), "callback device id");
        String eventType =
                required(valueAt(body, contract.callbackEventTypeField()), "callback event type");

        long callbackId =
                registerCallback(
                        merchantId,
                        contract.connectorCode(),
                        externalEventId,
                        externalDeviceId,
                        eventType,
                        rawBody);
        if (callbackId == 0) {
            return Map.of("status", "DUPLICATE", "eventId", externalEventId);
        }

        try {
            Map<String, Object> device =
                    requireDevice(merchantId, contract.connectorCode(), externalDeviceId);
            EventValues values = eventValues(merchantId, contract.connectorCode());
            String normalizedEvent = eventType.trim().toUpperCase(Locale.ROOT);
            String rentalReference = valueAt(body, contract.callbackRentalField());
            if (rentalReference.isBlank()) {
                Mapping mapping = correlations.mapping(merchantId, contract.connectorCode());
                String commandReference = valueAt(body, mapping.commandReferenceField());
                String providerReference = valueAt(body, mapping.providerReferenceField());
                rentalReference =
                        correlations.resolveRentalReference(
                                merchantId,
                                contract.connectorCode(),
                                commandReference,
                                providerReference);
            }
            String assetCode = valueAt(body, contract.callbackAssetField());
            Integer availableCount =
                    integerOrNull(valueAt(body, contract.callbackAvailableCountField()));

            if (matches(
                    normalizedEvent,
                    values.heartbeatValue(),
                    "HEARTBEAT",
                    "DEVICE_ONLINE",
                    "INVENTORY")) {
                heartbeat(merchantId, number(device.get("id")), availableCount);
                repository.event(
                        merchantId,
                        "DEVICE_HEARTBEAT",
                        "DEVICE",
                        String.valueOf(device.get("device_code")),
                        "manufacturer:" + contract.connectorCode(),
                        null,
                        null,
                        "{\"externalEventId\":\"" + json(externalEventId) + "\"}");
            } else if (matches(
                    normalizedEvent,
                    values.offlineValue(),
                    "DEVICE_OFFLINE",
                    "OFFLINE")) {
                setDeviceStatus(merchantId, number(device.get("id")), "OFFLINE");
                repository.event(
                        merchantId,
                        "DEVICE_OFFLINE",
                        "DEVICE",
                        String.valueOf(device.get("device_code")),
                        "manufacturer:" + contract.connectorCode(),
                        null,
                        null,
                        null);
            } else if (matches(
                    normalizedEvent,
                    values.releaseValue(),
                    "ASSET_RELEASED",
                    "POWER_BANK_RELEASED",
                    "RELEASED")) {
                if (rentalReference.isBlank()) {
                    throw new PaymentGatewayException(
                            "Release callback cannot be reconciled: map a rental, command, or OEM provider reference field");
                }
                if (repository.markRentalActive(merchantId, rentalReference) == 0) {
                    Map<String, Object> rental =
                            repository.rental(merchantId, rentalReference)
                                    .orElseThrow(
                                            () ->
                                                    new PaymentGatewayException(
                                                            "Release callback rental was not found"));
                    if (!"ACTIVE".equalsIgnoreCase(String.valueOf(rental.get("status")))) {
                        throw new PaymentGatewayException(
                                "Release callback does not match a release-pending rental");
                    }
                }
                if (!assetCode.isBlank()) markAssetOut(merchantId, assetCode);
                repository.event(
                        merchantId,
                        "MANUFACTURER_RELEASE_CONFIRMED",
                        "RENTAL",
                        rentalReference,
                        "manufacturer:" + contract.connectorCode(),
                        null,
                        null,
                        "{\"assetCode\":\"" + json(assetCode) + "\"}");
            } else if (matches(
                    normalizedEvent,
                    values.returnValue(),
                    "ASSET_RETURNED",
                    "POWER_BANK_RETURNED",
                    "RETURNED")) {
                if (rentalReference.isBlank()) {
                    throw new PaymentGatewayException(
                            "Return callback cannot be reconciled: map a rental, command, or OEM provider reference field");
                }
                if (!assetCode.isBlank()) {
                    markAssetAvailable(merchantId, number(device.get("id")), assetCode);
                }
                Map<String, Object> rental =
                        repository.rental(merchantId, rentalReference)
                                .orElseThrow(
                                        () ->
                                                new PaymentGatewayException(
                                                        "Return callback rental was not found"));
                String status = String.valueOf(rental.get("status"));
                if ("ACTIVE".equalsIgnoreCase(status)) {
                    rentals.returnRental(
                            merchantId,
                            rentalReference,
                            "manufacturer:" + contract.connectorCode());
                } else if (!"REFUND_PENDING".equalsIgnoreCase(status)
                        && !"SETTLED".equalsIgnoreCase(status)
                        && !"REFUND_FAILED".equalsIgnoreCase(status)) {
                    throw new PaymentGatewayException(
                            "Return callback does not match an active or already-returned rental");
                }
            } else {
                repository.event(
                        merchantId,
                        "MANUFACTURER_EVENT_UNMAPPED",
                        "DEVICE",
                        String.valueOf(device.get("device_code")),
                        "manufacturer:" + contract.connectorCode(),
                        null,
                        null,
                        "{\"eventType\":\"" + json(eventType) + "\"}");
            }

            markCallback(callbackId, "PROCESSED", null);
            return Map.of(
                    "status",
                    "PROCESSED",
                    "eventId",
                    externalEventId,
                    "eventType",
                    eventType,
                    "deviceCode",
                    String.valueOf(device.get("device_code")));
        } catch (RuntimeException e) {
            markCallback(callbackId, "FAILED", safeMessage(e));
            throw e;
        }
    }

    private long registerCallback(
            long merchantId,
            String connectorCode,
            String externalEventId,
            String externalDeviceId,
            String eventType,
            String rawBody) {
        String sql =
                "INSERT IGNORE INTO vending_device_callbacks (merchant_id, connector_code, external_event_id, "
                        + "external_device_id, event_type, body_sha256, signature_status, raw_body) "
                        + "VALUES (:tenant_merchant_id, :connector_code, :external_event_id, :external_device_id, "
                        + ":event_type, :body_hash, 'VERIFIED', :raw_body)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        p.addValue("external_event_id", externalEventId);
        p.addValue("external_device_id", externalDeviceId);
        p.addValue("event_type", eventType);
        p.addValue("body_hash", sha256(rawBody));
        p.addValue("raw_body", rawBody == null ? "" : rawBody);
        int inserted = jdbc.update(sql, p);
        if (inserted == 0) return 0;
        Long id =
                jdbc.queryForObject(
                        "SELECT id FROM vending_device_callbacks WHERE merchant_id=:tenant_merchant_id "
                                + "AND connector_code=:connector_code AND external_event_id=:external_event_id",
                        p,
                        Long.class);
        return id == null ? 0 : id;
    }

    private Map<String, Object> requireDevice(
            long merchantId, String connectorCode, String externalId) {
        String sql =
                "SELECT id, device_code, status, available_count FROM vending_devices "
                        + "WHERE merchant_id=:tenant_merchant_id AND connector_code=:connector_code "
                        + "AND external_device_id=:external_device_id LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        p.addValue("external_device_id", externalId);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "Manufacturer callback device is not registered");
        }
        return rows.get(0);
    }

    private EventValues eventValues(long merchantId, String connectorCode) {
        String sql =
                "SELECT callback_heartbeat_value, callback_return_value, callback_release_value, callback_offline_value "
                        + "FROM vending_connector_configs WHERE merchant_id=:tenant_merchant_id AND connector_code=:connector_code";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            return new EventValues(
                    "HEARTBEAT", "ASSET_RETURNED", "ASSET_RELEASED", "DEVICE_OFFLINE");
        }
        Map<String, Object> row = rows.get(0);
        return new EventValues(
                text(row.get("callback_heartbeat_value")),
                text(row.get("callback_return_value")),
                text(row.get("callback_release_value")),
                text(row.get("callback_offline_value")));
    }

    private void heartbeat(long merchantId, long deviceId, Integer availableCount) {
        String sql =
                "UPDATE vending_devices SET status='ONLINE', heartbeat_at=CURRENT_TIMESTAMP, "
                        + "available_count=COALESCE(:available_count, available_count) "
                        + "WHERE merchant_id=:tenant_merchant_id AND id=:device_id";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("device_id", deviceId);
        p.addValue("available_count", availableCount);
        jdbc.update(sql, p);
    }

    private void setDeviceStatus(long merchantId, long deviceId, String status) {
        String sql =
                "UPDATE vending_devices SET status=:status WHERE merchant_id=:tenant_merchant_id AND id=:device_id";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("device_id", deviceId);
        p.addValue("status", status);
        jdbc.update(sql, p);
    }

    private void markAssetOut(long merchantId, String assetCode) {
        updateAsset(merchantId, assetCode, null, "RENTED");
    }

    private void markAssetAvailable(long merchantId, long deviceId, String assetCode) {
        updateAsset(merchantId, assetCode, deviceId, "AVAILABLE");
    }

    private void updateAsset(
            long merchantId, String assetCode, Long deviceId, String status) {
        String sql =
                "UPDATE vending_assets SET status=:status, device_id=COALESCE(:device_id, device_id), "
                        + "last_seen_at=CURRENT_TIMESTAMP WHERE merchant_id=:tenant_merchant_id AND asset_code=:asset_code";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("asset_code", assetCode);
        p.addValue("device_id", deviceId);
        p.addValue("status", status);
        jdbc.update(sql, p);
    }

    private void markCallback(long callbackId, String status, String error) {
        jdbc.update(
                "UPDATE vending_device_callbacks SET processing_status=:status, error_message=:error, "
                        + "processed_at=CURRENT_TIMESTAMP WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("error", error)
                        .addValue("id", callbackId));
    }

    private JsonNode parse(String rawBody) {
        try {
            JsonNode body = mapper.readTree(rawBody == null ? "" : rawBody);
            if (body == null || !body.isObject()) {
                throw new PaymentGatewayException(
                        "Vending callback body must be a JSON object");
            }
            return body;
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException(
                    "Vending callback body is invalid JSON");
        }
    }

    private String valueAt(JsonNode node, String dottedPath) {
        if (node == null || dottedPath == null || dottedPath.isBlank()) return "";
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null) return "";
            current = current.get(part);
        }
        return current == null || current.isNull() ? "" : current.asText("");
    }

    private boolean matches(String actual, String configured, String... aliases) {
        if (actual.equalsIgnoreCase(text(configured))) return true;
        for (String alias : aliases) {
            if (actual.equalsIgnoreCase(alias)) return true;
        }
        return false;
    }

    private Integer integerOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long number(Object value) {
        return value instanceof Number n
                ? n.longValue()
                : Long.parseLong(String.valueOf(value));
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(name + " is required");
        }
        return value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (value == null ? "" : value)
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to hash vending callback body", e);
        }
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null
                ? e.getClass().getSimpleName()
                : message.substring(0, Math.min(500, message.length()));
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record EventValues(
            String heartbeatValue,
            String returnValue,
            String releaseValue,
            String offlineValue) {}
}
