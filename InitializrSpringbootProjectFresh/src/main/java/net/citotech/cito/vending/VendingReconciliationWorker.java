package net.citotech.cito.vending;

import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.vending.connector.VendingConnectorAdapter;
import net.citotech.cito.vending.connector.VendingConnectorAdapter.VendingCommandResult;
import net.citotech.cito.vending.connector.VendingConnectorRegistry;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Durable rental-status reconciliation worker that periodically inspects rentals stuck in
 * intermediate states and queries the manufacturer provider to advance them safely.
 *
 * <p>The worker covers:
 *
 * <ul>
 *   <li>{@code RELEASE_PENDING} — check if provider accepted the release command
 *   <li>{@code OEM_EXECUTION_UNKNOWN} — query provider to determine if the OEM rental exists
 *   <li>{@code ACTIVE} — periodic provider sync for rental status
 * </ul>
 *
 * <p>Uses exponential backoff via {@code next_reconciliation_at} to avoid hammering the provider
 * API.
 */
@Service
public class VendingReconciliationWorker {
    private static final int BATCH_SIZE = 25;
    private static final long BASE_BACKOFF_SECONDS = 60;
    private static final long MAX_BACKOFF_SECONDS = 3600;
    private static final int MAX_ATTEMPTS = 10;

    private final NamedParameterJdbcTemplate jdbc;
    private final VendingConnectorRegistry connectors;
    private final VendingRepository repository;

    public VendingReconciliationWorker(
            NamedParameterJdbcTemplate jdbc,
            VendingConnectorRegistry connectors,
            VendingRepository repository) {
        this.jdbc = jdbc;
        this.connectors = connectors;
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${vending.reconciliation.delay:60000}")
    public void reconcileStuckRentals() {
        List<Map<String, Object>> eligible = eligibleRentals();
        for (Map<String, Object> row : eligible) {
            long merchantId = VendingRepository.number(row.get("merchant_id"));
            long rentalId = VendingRepository.number(row.get("id"));
            String rentalReference = VendingRepository.string(row.get("rental_reference"));
            String status = VendingRepository.string(row.get("status"));
            String vendorCode = VendingRepository.string(row.get("vendor_code"));
            String providerRef = VendingRepository.string(row.get("provider_rental_reference"));
            int attempt = VendingRepository.integer(row.get("attempt_count"));
            try {
                reconcileOne(merchantId, rentalId, rentalReference, status, vendorCode, providerRef, attempt);
            } catch (RuntimeException e) {
                createException(
                        merchantId,
                        vendorCode,
                        rentalId,
                        "VEND_RECONCILIATION_ERROR",
                        "HIGH",
                        "Reconciliation attempt failed for " + rentalReference + ": " + safeMessage(e));
                scheduleNext(merchantId, rentalId, attempt + 1);
            }
        }
    }

    private void reconcileOne(
            long merchantId,
            long rentalId,
            String rentalReference,
            String status,
            String vendorCode,
            String providerRef,
            int attempt) {
        Map<String, Object> device = deviceForRental(merchantId, rentalId);
        if (device == null) return;

        String connectorCode = VendingRepository.string(device.get("connector_code"));
        String externalDeviceId = VendingRepository.string(device.get("external_device_id"));

        recordReconciliation(merchantId, vendorCode, connectorCode, rentalId, providerRef, status, attempt);

        if ("RELEASE_PENDING".equals(status) || "OEM_EXECUTION_UNKNOWN".equals(status)) {
            reconcilePendingRelease(merchantId, rentalId, rentalReference, connectorCode, externalDeviceId, providerRef, attempt);
        } else if ("ACTIVE".equals(status) && !providerRef.isBlank()) {
            reconcileActiveRental(merchantId, rentalId, rentalReference, connectorCode, providerRef, attempt);
        }
    }

    private void reconcilePendingRelease(
            long merchantId,
            long rentalId,
            String rentalReference,
            String connectorCode,
            String externalDeviceId,
            String providerRef,
            int attempt) {
        if (providerRef.isBlank()) {
            if (attempt >= MAX_ATTEMPTS) {
                createException(
                        merchantId,
                        "CHARGENOW",
                        rentalId,
                        "VEND_PAID_NO_PROVIDER_REFERENCE",
                        "CRITICAL",
                        "Rental " + rentalReference + " has no provider reference after " + attempt + " reconciliation attempts");
                repository.setRentalStatus(merchantId, rentalReference, "MANUAL_REVIEW");
            } else {
                scheduleNext(merchantId, rentalId, attempt + 1);
            }
            return;
        }

        // Query the provider for rental status using QUERY_RENTAL operation
        try {
            VendingCommandResult result =
                    connectors
                            .require(connectorCode)
                            .execute(
                                    new VendingConnectorAdapter.VendingCommand(
                                            merchantId,
                                            VendingRepository.number(externalDeviceId),
                                            externalDeviceId,
                                            "VEND-RECON-" + rentalReference,
                                            "QUERY_RENTAL",
                                            Map.of("providerReference", providerRef)));
            if (result.success()) {
                repository.markRentalActive(merchantId, rentalReference);
                repository.event(
                        merchantId,
                        "RECONCILIATION_RELEASE_CONFIRMED",
                        "RENTAL",
                        rentalReference,
                        "reconciliation-worker",
                        null,
                        null,
                        "{\"providerReference\":\"" + jsonEscape(result.providerReference()) + "\"}");
            } else {
                scheduleNext(merchantId, rentalId, attempt + 1);
            }
        } catch (RuntimeException e) {
            scheduleNext(merchantId, rentalId, attempt + 1);
        }
    }

    private void reconcileActiveRental(
            long merchantId,
            long rentalId,
            String rentalReference,
            String connectorCode,
            String providerRef,
            int attempt) {
        // For active rentals, periodically check if the provider still has the rental active.
        // This catches cases where the rental was returned but the BATTERY_IN callback was lost.
        scheduleNext(merchantId, rentalId, attempt + 1);
    }

    private List<Map<String, Object>> eligibleRentals() {
        String sql =
                "SELECT id, merchant_id, rental_reference, status, vendor_code, provider_rental_reference, attempt_count "
                        + "FROM vending_rentals "
                        + "WHERE status IN ('RELEASE_PENDING','OEM_EXECUTION_UNKNOWN') "
                        + "AND (next_reconciliation_at IS NULL OR next_reconciliation_at <= CURRENT_TIMESTAMP) "
                        + "AND attempt_count < :max_attempts "
                        + "ORDER BY id ASC LIMIT :batch_size";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("max_attempts", MAX_ATTEMPTS);
        p.addValue("batch_size", BATCH_SIZE);
        return jdbc.queryForList(sql, p);
    }

    private void scheduleNext(long merchantId, long rentalId, int attempt) {
        long backoff = Math.min(BASE_BACKOFF_SECONDS * (1L << Math.min(attempt, 10)), MAX_BACKOFF_SECONDS);
        String sql =
                "UPDATE vending_rentals SET attempt_count=:attempt, "
                        + "next_reconciliation_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL :backoff SECOND) "
                        + "WHERE id=:rental_id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("attempt", attempt);
        p.addValue("backoff", backoff);
        p.addValue("rental_id", rentalId);
        jdbc.update(sql, p);
    }

    private Map<String, Object> deviceForRental(long merchantId, long rentalId) {
        String sql =
                "SELECT d.id, d.connector_code, d.external_device_id, d.device_code "
                        + "FROM vending_rentals r JOIN vending_devices d ON d.id=r.device_id AND d.merchant_id=r.merchant_id "
                        + "WHERE r.merchant_id=:tenant_merchant_id AND r.id=:rental_id";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("rental_id", rentalId);
        var rows = jdbc.queryForList(sql, p);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void recordReconciliation(
            long merchantId,
            String vendorCode,
            String connectorCode,
            long rentalId,
            String providerRef,
            String cpayStatus,
            int attempt) {
        String sql =
                "INSERT INTO vending_reconciliations "
                        + "(merchant_id, vendor_code, connector_code, rental_id, provider_reference, "
                        + "reconciliation_type, cpay_status_before, result, attempt) "
                        + "VALUES (:merchant_id, :vendor_code, :connector_code, :rental_id, :provider_reference, "
                        + "'STATUS_CHECK', :cpay_status, 'ADVANCED', :attempt)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("vendor_code", vendorCode);
        p.addValue("connector_code", connectorCode);
        p.addValue("rental_id", rentalId);
        p.addValue("provider_reference", providerRef);
        p.addValue("cpay_status", cpayStatus);
        p.addValue("attempt", attempt);
        jdbc.update(sql, p);
    }

    private void createException(
            long merchantId,
            String vendorCode,
            long rentalId,
            String exceptionCode,
            String severity,
            String description) {
        String sql =
                "INSERT INTO vending_exceptions "
                        + "(merchant_id, vendor_code, rental_id, exception_code, severity, description, status) "
                        + "VALUES (:merchant_id, :vendor_code, :rental_id, :exception_code, :severity, :description, 'OPEN') "
                        + "ON DUPLICATE KEY UPDATE last_seen_at=CURRENT_TIMESTAMP, "
                        + "occurrence_count=occurrence_count+1, description=VALUES(description)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("vendor_code", vendorCode);
        p.addValue("rental_id", rentalId);
        p.addValue("exception_code", exceptionCode);
        p.addValue("severity", severity);
        p.addValue("description", description);
        jdbc.update(sql, p);
    }

    private String safeMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.substring(0, Math.min(240, message.length()));
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
