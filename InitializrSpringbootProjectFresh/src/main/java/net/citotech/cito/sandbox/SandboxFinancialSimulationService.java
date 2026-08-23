package net.citotech.cito.sandbox;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.refund.RefundRecord;
import net.citotech.cito.refund.RefundStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sandbox-only simulations for v2 financial flows that otherwise call legacy payout internals. */
@Service
public class SandboxFinancialSimulationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SandboxFinancialSimulationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public RefundRecord refund(
            long merchantId,
            String originalReference,
            String refundReference,
            BigDecimal amount,
            String reason) {
        Optional<RefundRecord> existing = findRefund(merchantId, refundReference);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (refundReference == null || refundReference.isBlank()) {
            throw new IllegalArgumentException("Sandbox refund reference is required.");
        }
        if (originalReference == null || originalReference.isBlank()) {
            throw new IllegalArgumentException("Sandbox original payment reference is required.");
        }
        if (amount != null && amount.signum() <= 0) {
            throw new IllegalArgumentException("Sandbox refund amount must be greater than zero.");
        }

        String scenario = originalReference.trim().toUpperCase(Locale.ROOT);
        RefundStatus status =
                scenario.contains("FAIL") ? RefundStatus.FAILED : RefundStatus.COMPLETED;
        String failure =
                status == RefundStatus.FAILED ? "Simulated sandbox refund failure" : null;
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("refundReference", refundReference.trim());
        p.addValue("originalReference", originalReference.trim());
        p.addValue("amount", amount);
        p.addValue("status", status.name());
        p.addValue("reason", reason);
        p.addValue("failure", failure);
        jdbcTemplate.update(
                "INSERT INTO sandbox_refunds "
                        + "(merchant_id,refund_reference,original_reference,requested_amount,refund_status,reason,failure_message) "
                        + "VALUES (:merchantId,:refundReference,:originalReference,:amount,:status,:reason,:failure)",
                p);
        return findRefund(merchantId, refundReference)
                .orElseThrow(
                        () -> new IllegalStateException("Sandbox refund could not be read back."));
    }

    public Optional<RefundRecord> findRefund(long merchantId, String refundReference) {
        if (refundReference == null || refundReference.isBlank()) {
            return Optional.empty();
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("refundReference", refundReference.trim());
        List<RefundRecord> rows =
                jdbcTemplate.query(
                        "SELECT id,merchant_id,refund_reference,original_reference,requested_amount,refund_status,reason,failure_message "
                                + "FROM sandbox_refunds WHERE merchant_id=:merchantId AND refund_reference=:refundReference",
                        p,
                        (rs, rowNum) ->
                                new RefundRecord(
                                        rs.getLong("id"),
                                        rs.getString("refund_reference"),
                                        rs.getLong("merchant_id"),
                                        0L,
                                        rs.getString("original_reference"),
                                        null,
                                        rs.getBigDecimal("requested_amount"),
                                        RefundStatus.valueOf(rs.getString("refund_status")),
                                        rs.getString("reason"),
                                        rs.getString("failure_message")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Map<String, Object> batchStatus(long merchantId, long batchId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("batchId", batchId);
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT operation,result_status,retried_count,created_at FROM sandbox_batch_payout_runs "
                                + "WHERE merchant_id=:merchantId AND batch_id=:batchId ORDER BY id DESC LIMIT 1",
                        p);
        if (rows.isEmpty()) {
            return Map.of(
                    "batchId",
                    batchId,
                    "environment",
                    "SANDBOX",
                    "status",
                    "READY_FOR_SIMULATION",
                    "retriedCount",
                    0);
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("environment", "SANDBOX");
        result.put("operation", row.get("operation"));
        result.put("status", row.get("result_status"));
        result.put("retriedCount", row.get("retried_count"));
        result.put("createdAt", row.get("created_at"));
        return result;
    }

    @Transactional
    public Map<String, Object> retryFailedBatch(long merchantId, long batchId) {
        int retried = Math.floorMod(Long.hashCode(batchId), 4) + 1;
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("batchId", batchId);
        p.addValue("retried", retried);
        jdbcTemplate.update(
                "INSERT INTO sandbox_batch_payout_runs "
                        + "(merchant_id,batch_id,operation,result_status,retried_count) "
                        + "VALUES (:merchantId,:batchId,'RETRY_FAILED','SIMULATED',:retried)",
                p);
        return Map.of(
                "code",
                "000",
                "environment",
                "SANDBOX",
                "batchId",
                batchId,
                "retriedCount",
                retried,
                "status",
                "SIMULATED");
    }
}
