package net.citotech.cito.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciles refunds against the immutable marketplace split captured for the original payment.
 * This is intentionally eventual and idempotent so approval/provider transitions can happen in
 * either order without losing the financial relationship.
 */
@Service
public class MarketplaceRefundAllocationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MarketplaceRefundAllocationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${cpay.marketplace.refund-allocation-delay-ms:30000}")
    @SchedulerLock(name = "marketplaceRefundAllocations", lockAtMostFor = "PT2M", lockAtLeastFor = "PT2S")
    public void scheduledReconcile() {
        reconcile(100);
    }

    @Transactional
    public int reconcile(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> refunds = jdbcTemplate.queryForList(
                "SELECT r.id, r.merchant_id, r.original_merchant_ref, r.requested_amount, r.refund_status, "
                        + "r.split_execution_reference FROM refunds r "
                        + "WHERE EXISTS (SELECT 1 FROM marketplace_split_executions e "
                        + "WHERE e.merchant_id=r.merchant_id AND e.transaction_reference=r.original_merchant_ref) "
                        + "ORDER BY r.id DESC LIMIT " + safeLimit,
                new MapSqlParameterSource());
        int changed = 0;
        for (Map<String, Object> refund : refunds) {
            changed += reconcileRefund(refund);
        }
        return changed;
    }

    private int reconcileRefund(Map<String, Object> refund) {
        long refundId = ((Number) refund.get("id")).longValue();
        long merchantId = ((Number) refund.get("merchant_id")).longValue();
        String originalReference = String.valueOf(refund.get("original_merchant_ref"));
        BigDecimal refundAmount = decimal(refund.get("requested_amount"));
        String refundStatus = String.valueOf(refund.get("refund_status")).toUpperCase(Locale.ROOT);

        List<Map<String, Object>> executions = jdbcTemplate.queryForList(
                "SELECT id, execution_reference, gross_amount, platform_fee_amount, distributable_amount "
                        + "FROM marketplace_split_executions WHERE merchant_id=:merchant_id "
                        + "AND transaction_reference=:transaction_reference ORDER BY id DESC LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("transaction_reference", originalReference));
        if (executions.isEmpty()) {
            return 0;
        }
        Map<String, Object> execution = executions.get(0);
        long executionId = ((Number) execution.get("id")).longValue();
        String executionReference = String.valueOf(execution.get("execution_reference"));
        BigDecimal gross = decimal(execution.get("gross_amount"));
        if (gross.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        String allocationStatus = allocationStatus(refundStatus);

        int changed = jdbcTemplate.update(
                "UPDATE refunds SET split_execution_reference=:execution_reference "
                        + "WHERE id=:refund_id AND (split_execution_reference IS NULL OR split_execution_reference='')",
                new MapSqlParameterSource()
                        .addValue("refund_id", refundId)
                        .addValue("execution_reference", executionReference));

        List<Map<String, Object>> allocations = jdbcTemplate.queryForList(
                "SELECT subaccount_id, allocation_amount FROM marketplace_split_allocations "
                        + "WHERE execution_id=:execution_id ORDER BY id",
                new MapSqlParameterSource("execution_id", executionId));
        BigDecimal recipientRefundTotal = BigDecimal.ZERO;
        for (Map<String, Object> allocation : allocations) {
            long subaccountId = ((Number) allocation.get("subaccount_id")).longValue();
            BigDecimal originalAllocation = decimal(allocation.get("allocation_amount"));
            BigDecimal proportional = refundAmount
                    .multiply(originalAllocation)
                    .divide(gross, 6, RoundingMode.HALF_UP);
            BigDecimal alreadyAllocated = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(SUM(refund_allocation_amount),0) FROM marketplace_split_refund_allocations "
                            + "WHERE split_execution_id=:execution_id AND subaccount_id=:subaccount_id AND refund_id<>:refund_id "
                            + "AND status<>'RELEASED'",
                    new MapSqlParameterSource()
                            .addValue("execution_id", executionId)
                            .addValue("subaccount_id", subaccountId)
                            .addValue("refund_id", refundId),
                    BigDecimal.class);
            BigDecimal remaining = originalAllocation.subtract(
                    alreadyAllocated == null ? BigDecimal.ZERO : alreadyAllocated);
            BigDecimal safeAmount = proportional.min(remaining.max(BigDecimal.ZERO));
            recipientRefundTotal = recipientRefundTotal.add(safeAmount);
            changed += jdbcTemplate.update(
                    "INSERT INTO marketplace_split_refund_allocations "
                            + "(refund_id, split_execution_id, subaccount_id, original_allocation_amount, refund_allocation_amount, status) "
                            + "VALUES (:refund_id, :execution_id, :subaccount_id, :original_amount, :refund_amount, :status) "
                            + "ON DUPLICATE KEY UPDATE refund_allocation_amount=VALUES(refund_allocation_amount), status=VALUES(status)",
                    new MapSqlParameterSource()
                            .addValue("refund_id", refundId)
                            .addValue("execution_id", executionId)
                            .addValue("subaccount_id", subaccountId)
                            .addValue("original_amount", originalAllocation)
                            .addValue("refund_amount", safeAmount)
                            .addValue("status", allocationStatus));
        }

        BigDecimal platformRefund = refundAmount.subtract(recipientRefundTotal).max(BigDecimal.ZERO);
        String detail = "{\"splitExecutionReference\":\"" + escape(executionReference)
                + "\",\"recipientRefundAmount\":" + recipientRefundTotal.toPlainString()
                + ",\"platformRefundAmount\":" + platformRefund.toPlainString() + "}";
        jdbcTemplate.update(
                "INSERT INTO payment_financial_timeline "
                        + "(merchant_id, transaction_reference, event_reference, event_type, event_status, amount, detail_json) "
                        + "SELECT :merchant_id, :transaction_reference, CONCAT('split-refund:', :refund_id), 'SPLIT_REFUND_ALLOCATION', "
                        + ":event_status, :amount, :detail_json FROM DUAL WHERE NOT EXISTS ("
                        + "SELECT 1 FROM payment_financial_timeline WHERE merchant_id=:merchant_id "
                        + "AND event_reference=CONCAT('split-refund:', :refund_id) AND event_type='SPLIT_REFUND_ALLOCATION')",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("transaction_reference", originalReference)
                        .addValue("refund_id", refundId)
                        .addValue("event_status", allocationStatus)
                        .addValue("amount", refundAmount)
                        .addValue("detail_json", detail));
        jdbcTemplate.update(
                "UPDATE payment_financial_timeline SET event_status=:event_status, detail_json=:detail_json "
                        + "WHERE merchant_id=:merchant_id AND event_reference=CONCAT('split-refund:', :refund_id) "
                        + "AND event_type='SPLIT_REFUND_ALLOCATION'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("refund_id", refundId)
                        .addValue("event_status", allocationStatus)
                        .addValue("detail_json", detail));
        return changed;
    }

    public List<Map<String, Object>> allocations(long merchantId, String refundReference) {
        return jdbcTemplate.queryForList(
                "SELECT r.refund_reference AS refundReference, e.execution_reference AS splitExecutionReference, "
                        + "s.subaccount_reference AS subaccountReference, s.display_name AS displayName, "
                        + "a.original_allocation_amount AS originalAllocationAmount, "
                        + "a.refund_allocation_amount AS refundAllocationAmount, a.status "
                        + "FROM marketplace_split_refund_allocations a "
                        + "JOIN refunds r ON r.id=a.refund_id "
                        + "JOIN marketplace_split_executions e ON e.id=a.split_execution_id "
                        + "JOIN marketplace_subaccounts s ON s.id=a.subaccount_id "
                        + "WHERE r.merchant_id=:merchant_id AND r.refund_reference=:refund_reference ORDER BY a.id",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("refund_reference", refundReference));
    }

    private String allocationStatus(String refundStatus) {
        return switch (refundStatus) {
            case "COMPLETED" -> "COMPLETED";
            case "FAILED", "REJECTED" -> "RELEASED";
            default -> "PENDING";
        };
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
