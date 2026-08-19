package net.citotech.cito.vending.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.VendingRepository;
import net.citotech.cito.vending.VendingRentalService;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Implements the {@code VERIFY_BY_PROVIDER_QUERY} callback verification flow.
 *
 * <p>When the ChargeNow callback contract does not provide a verifiable HMAC signature, the raw
 * callback is persisted and deduplicated first, then the provider is re-queried over its
 * authenticated connector channel and the callback claim is compared with authoritative provider
 * state before any consequential state change.
 *
 * <p>Consequential steps that require verification: ACTIVE, RETURNED, SETTLED, REFUNDED,
 * SURCHARGE_CREATED.
 */
@Service
public class VendingCallbackVerificationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final VendingConnectorRegistry connectors;
    private final VendingRentalService rentals;
    private final VendingRepository repository;

    public VendingCallbackVerificationService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper mapper,
            VendingConnectorRegistry connectors,
            VendingRentalService rentals,
            VendingRepository repository) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.connectors = connectors;
        this.rentals = rentals;
        this.repository = repository;
    }

    /**
     * Executes the VERIFY_BY_PROVIDER_QUERY flow for a raw callback.
     *
     * @return Map with keys: status (VERIFIED, REJECTED, UNKNOWN), rentalReference, providerStatus
     */
    public Map<String, Object> verifyByProviderQuery(
            long merchantId,
            Contract contract,
            String rawBody) {
        JsonNode body = parseJson(rawBody);
        String externalEventId =
                required(valueAt(body, contract.callbackEventIdField()), "callback event id");
        String eventType =
                required(valueAt(body, contract.callbackEventTypeField()), "callback event type");

        // Step 1: Persist and deduplicate the raw callback
        long callbackId =
                persistCallback(merchantId, contract.connectorCode(), externalEventId, eventType, rawBody);
        if (callbackId == 0) {
            return Map.of("status", "DUPLICATE", "eventId", externalEventId);
        }

        try {
            // Step 2: Identify the rental from callback
            String rentalReference = valueAt(body, contract.callbackRentalField());
            String providerReference = valueAt(body, "data.tradeNo");
            if (rentalReference.isBlank() && !providerReference.isBlank()) {
                rentalReference =
                        resolveRentalFromProviderRef(merchantId, contract.connectorCode(), providerReference);
            }
            if (rentalReference.isBlank()) {
                markCallback(callbackId, "REJECTED", "Could not correlate callback to a rental");
                return Map.of("status", "REJECTED", "reason", "unmatched_rental");
            }

            // Step 3: Query provider for authoritative state
            String providerState = queryProviderState(merchantId, contract, providerReference);
            if (providerState == null) {
                markCallback(callbackId, "REJECTED", "Provider query returned no state");
                return Map.of("status", "REJECTED", "reason", "provider_query_failed");
            }

            // Step 4: Compare callback claim with provider state
            String normalizedCallbackEvent = eventType.trim().toUpperCase(Locale.ROOT);
            String normalizedProviderState = providerState.trim().toUpperCase(Locale.ROOT);

            boolean verified = matchEventToProviderState(normalizedCallbackEvent, normalizedProviderState);
            String verificationStatus = verified ? "VERIFIED" : "REJECTED";

            markCallback(callbackId, verificationStatus, null);

            // Step 5: Only verified state may advance consequential lifecycle steps
            if (verified) {
                applyVerifiedState(merchantId, rentalReference, normalizedCallbackEvent, contract.connectorCode());
            }

            return Map.of(
                    "status", verificationStatus,
                    "rentalReference", rentalReference,
                    "providerStatus", providerState,
                    "callbackEvent", eventType);
        } catch (RuntimeException e) {
            markCallback(callbackId, "FAILED", safeMessage(e));
            throw e;
        }
    }

    private void applyVerifiedState(
            long merchantId,
            String rentalReference,
            String normalizedEvent,
            String connectorCode) {
        Map<String, Object> rental =
                repository
                        .rental(merchantId, rentalReference)
                        .orElse(null);
        if (rental == null) return;

        String currentStatus = VendingRepository.string(rental.get("status"));
        if ("ASSET_RELEASED".equals(normalizedEvent)) {
            if (!"ACTIVE".equals(currentStatus)) {
                repository.markRentalActive(merchantId, rentalReference);
                repository.event(
                        merchantId,
                        "VERIFIED_RELEASE_CONFIRMED",
                        "RENTAL",
                        rentalReference,
                        "provider-verification",
                        null,
                        null,
                        "{\"verificationMode\":\"VERIFY_BY_PROVIDER_QUERY\"}");
            }
        } else if ("ASSET_RETURNED".equals(normalizedEvent)) {
            if ("ACTIVE".equals(currentStatus)) {
                rentals.returnRental(
                        merchantId, rentalReference, "provider-verification");
            }
        }
    }

    private boolean matchEventToProviderState(String callbackEvent, String providerState) {
        // Both "ASSET_RELEASED" and active/renting provider state indicate a valid release
        if ("ASSET_RELEASED".equals(callbackEvent)
                && (providerState.contains("ACTIVE")
                        || providerState.contains("RENTING")
                        || providerState.contains("BORROWED"))) {
            return true;
        }
        // Both "ASSET_RETURNED" and returned/settled provider state indicate a valid return
        if ("ASSET_RETURNED".equals(callbackEvent)
                && (providerState.contains("RETURNED")
                        || providerState.contains("COMPLETED")
                        || providerState.contains("CLOSED"))) {
            return true;
        }
        // For other events, accept if provider state is not a hard contradiction
        return !providerState.contains("FAILED") && !providerState.contains("ERROR");
    }

    private String queryProviderState(
            long merchantId, Contract contract, String providerReference) {
        try {
            VendingConnectorAdapter.VendingCommandResult result =
                    connectors
                            .require(contract.connectorCode())
                            .execute(
                                    new VendingConnectorAdapter.VendingCommand(
                                            merchantId,
                                            0L,
                                            "",
                                            "VEND-VERIFY-" + providerReference,
                                            "QUERY_RENTAL",
                                            Map.of("providerReference",
                                                    providerReference == null ? "" : providerReference)));
            if (result.success()) {
                return result.message() != null ? result.message() : result.status();
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private long persistCallback(
            long merchantId,
            String connectorCode,
            String externalEventId,
            String eventType,
            String rawBody) {
        String sql =
                "INSERT IGNORE INTO vending_device_callbacks "
                        + "(merchant_id, connector_code, external_event_id, event_type, "
                        + "body_sha256, signature_status, raw_body, processing_status) "
                        + "VALUES (:merchant_id, :connector_code, :external_event_id, :event_type, "
                        + ":body_hash, 'UNVERIFIED', :raw_body, 'RECEIVED')";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        p.addValue("external_event_id", externalEventId);
        p.addValue("event_type", eventType);
        p.addValue("body_hash", sha256(rawBody == null ? "" : rawBody));
        p.addValue("raw_body", rawBody == null ? "" : rawBody);
        int inserted = jdbc.update(sql, p);
        if (inserted == 0) return 0;
        Long id =
                jdbc.queryForObject(
                        "SELECT id FROM vending_device_callbacks "
                                + "WHERE merchant_id=:merchant_id AND connector_code=:connector_code "
                                + "AND external_event_id=:external_event_id",
                        p,
                        Long.class);
        return id == null ? 0 : id;
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

    private String resolveRentalFromProviderRef(
            long merchantId, String connectorCode, String providerReference) {
        String sql =
                "SELECT r.rental_reference FROM vending_commands c "
                        + "JOIN vending_rentals r ON r.id=c.rental_id AND r.merchant_id=c.merchant_id "
                        + "WHERE c.merchant_id=:merchant_id AND c.connector_code=:connector_code "
                        + "AND c.provider_reference=:provider_reference "
                        + "ORDER BY c.id DESC LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        p.addValue("provider_reference", providerReference);
        var rows = jdbc.queryForList(sql, p);
        return rows.isEmpty() ? "" : VendingRepository.string(rows.get(0).get("rental_reference"));
    }

    private JsonNode parseJson(String body) {
        try {
            JsonNode node = mapper.readTree(body == null ? "" : body);
            return node == null ? mapper.createObjectNode() : node;
        } catch (Exception e) {
            throw new PaymentGatewayException("Invalid callback JSON");
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

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(name + " is required");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash vending callback body", e);
        }
    }

    private String safeMessage(RuntimeException e) {
        String msg = e.getMessage();
        return msg == null ? e.getClass().getSimpleName() : msg.substring(0, Math.min(240, msg.length()));
    }
}
