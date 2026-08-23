package net.citotech.cito.analytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantIntelligenceService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MerchantIntelligenceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${cpay.analytics.refresh-delay-ms:300000}")
    @SchedulerLock(name = "merchantIntelligenceRefresh", lockAtMostFor = "PT10M", lockAtLeastFor = "PT15S")
    public void refreshRecentAnalytics() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(7);
        List<Long> merchantIds = jdbcTemplate.query(
                "SELECT DISTINCT id FROM " + Common.DB_TABLE_MERCHANTS + " WHERE id IS NOT NULL",
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getLong("id"));
        for (Long merchantId : merchantIds) {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                refreshDaily(merchantId, day);
                refreshProviderDaily(merchantId, day);
            }
            generateRecommendations(merchantId);
        }
    }

    @Transactional
    public void refreshDaily(long merchantId, LocalDate date) {
        requireMerchant(merchantId);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("merchant_id", merchantId)
                .addValue("metric_date", date);

        Map<String, Object> tx = one(
                "SELECT COUNT(*) transaction_count, "
                        + "SUM(CASE WHEN status IN ('SUCCESS','SUCCESSFUL','COMPLETED') THEN 1 ELSE 0 END) successful_count, "
                        + "SUM(CASE WHEN status IN ('FAILED','FAILURE','REJECTED','CANCELLED') THEN 1 ELSE 0 END) failed_count, "
                        + "COALESCE(SUM(CASE WHEN status IN ('SUCCESS','SUCCESSFUL','COMPLETED') THEN original_amount ELSE 0 END),0) transaction_volume "
                        + "FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " WHERE merchant_id=:merchant_id AND DATE(created_on)=:metric_date",
                p);
        Map<String, Object> refunds = one(
                "SELECT COUNT(*) refund_count, COALESCE(SUM(requested_amount),0) refund_amount FROM refunds "
                        + "WHERE merchant_id=:merchant_id AND refund_status='COMPLETED' AND DATE(COALESCE(approved_at, created_at))=:metric_date",
                p);
        Map<String, Object> splits = one(
                "SELECT COUNT(*) split_execution_count, COALESCE(SUM(gross_amount),0) split_volume FROM marketplace_split_executions "
                        + "WHERE merchant_id=:merchant_id AND DATE(created_at)=:metric_date",
                p);
        Map<String, Object> recurring = one(
                "SELECT COUNT(*) recurring_charge_count, "
                        + "SUM(CASE WHEN c.status='SUCCESS' THEN 1 ELSE 0 END) recurring_success_count, "
                        + "SUM(CASE WHEN c.status='FAILED' THEN 1 ELSE 0 END) recurring_failed_count "
                        + "FROM recurring_scheduled_charges c JOIN recurring_subscriptions s ON s.id=c.subscription_id "
                        + "WHERE s.merchant_id=:merchant_id AND DATE(c.created_at)=:metric_date",
                p);

        jdbcTemplate.update(
                "INSERT INTO merchant_analytics_daily "
                        + "(merchant_id, metric_date, transaction_count, successful_count, failed_count, transaction_volume, "
                        + "refund_count, refund_amount, split_execution_count, split_volume, recurring_charge_count, recurring_success_count, recurring_failed_count) "
                        + "VALUES (:merchant_id, :metric_date, :transaction_count, :successful_count, :failed_count, :transaction_volume, "
                        + ":refund_count, :refund_amount, :split_execution_count, :split_volume, :recurring_charge_count, :recurring_success_count, :recurring_failed_count) "
                        + "ON DUPLICATE KEY UPDATE transaction_count=VALUES(transaction_count), successful_count=VALUES(successful_count), "
                        + "failed_count=VALUES(failed_count), transaction_volume=VALUES(transaction_volume), refund_count=VALUES(refund_count), "
                        + "refund_amount=VALUES(refund_amount), split_execution_count=VALUES(split_execution_count), split_volume=VALUES(split_volume), "
                        + "recurring_charge_count=VALUES(recurring_charge_count), recurring_success_count=VALUES(recurring_success_count), "
                        + "recurring_failed_count=VALUES(recurring_failed_count), updated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("metric_date", date)
                        .addValue("transaction_count", number(tx, "transaction_count"))
                        .addValue("successful_count", number(tx, "successful_count"))
                        .addValue("failed_count", number(tx, "failed_count"))
                        .addValue("transaction_volume", decimal(tx, "transaction_volume"))
                        .addValue("refund_count", number(refunds, "refund_count"))
                        .addValue("refund_amount", decimal(refunds, "refund_amount"))
                        .addValue("split_execution_count", number(splits, "split_execution_count"))
                        .addValue("split_volume", decimal(splits, "split_volume"))
                        .addValue("recurring_charge_count", number(recurring, "recurring_charge_count"))
                        .addValue("recurring_success_count", number(recurring, "recurring_success_count"))
                        .addValue("recurring_failed_count", number(recurring, "recurring_failed_count")));
    }

    @Transactional
    public void refreshProviderDaily(long merchantId, LocalDate date) {
        requireMerchant(merchantId);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("merchant_id", merchantId)
                .addValue("metric_date", date);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT selected_channel channel_code, operation, COUNT(*) routed_count, "
                        + "SUM(CASE WHEN outcome='SUCCESS' THEN 1 ELSE 0 END) successful_count, "
                        + "SUM(CASE WHEN outcome='FAILED' THEN 1 ELSE 0 END) failed_count, "
                        + "COALESCE(AVG(latency_ms),0) average_latency_ms "
                        + "FROM payment_route_decisions WHERE merchant_id=:merchant_id AND DATE(created_at)=:metric_date "
                        + "GROUP BY selected_channel, operation",
                p);
        jdbcTemplate.update(
                "DELETE FROM merchant_provider_analytics WHERE merchant_id=:merchant_id AND metric_date=:metric_date",
                p);
        for (Map<String, Object> row : rows) {
            jdbcTemplate.update(
                    "INSERT INTO merchant_provider_analytics "
                            + "(merchant_id, metric_date, channel_code, operation, routed_count, successful_count, failed_count, average_latency_ms) "
                            + "VALUES (:merchant_id, :metric_date, :channel_code, :operation, :routed_count, :successful_count, :failed_count, :average_latency_ms)",
                    new MapSqlParameterSource()
                            .addValue("merchant_id", merchantId)
                            .addValue("metric_date", date)
                            .addValue("channel_code", row.get("channel_code"))
                            .addValue("operation", row.get("operation"))
                            .addValue("routed_count", number(row, "routed_count"))
                            .addValue("successful_count", number(row, "successful_count"))
                            .addValue("failed_count", number(row, "failed_count"))
                            .addValue("average_latency_ms", number(row, "average_latency_ms")));
        }
    }

    @Transactional
    public void generateRecommendations(long merchantId) {
        requireMerchant(merchantId);
        List<Map<String, Object>> providerRows = jdbcTemplate.queryForList(
                "SELECT channel_code, operation, SUM(routed_count) routed_count, SUM(successful_count) successful_count, "
                        + "SUM(failed_count) failed_count, ROUND(AVG(average_latency_ms)) average_latency_ms "
                        + "FROM merchant_provider_analytics WHERE merchant_id=:merchant_id AND metric_date>=CURRENT_DATE-INTERVAL 7 DAY "
                        + "GROUP BY channel_code, operation",
                new MapSqlParameterSource("merchant_id", merchantId));
        for (Map<String, Object> row : providerRows) {
            long routed = number(row, "routed_count");
            long success = number(row, "successful_count");
            long latency = number(row, "average_latency_ms");
            if (routed >= 10 && success * 100L < routed * 80L) {
                upsertRecommendation(
                        merchantId,
                        "LOW_PROVIDER_SUCCESS_RATE",
                        row.get("channel_code") + ":" + row.get("operation"),
                        "WARNING",
                        "Provider success rate is below target",
                        "The seven-day routed success rate is below 80%. Review the routing policy, provider health and merchant channel configuration.",
                        "{\"routed\":" + routed + ",\"successful\":" + success + "}");
            }
            if (routed >= 5 && latency > 5000) {
                upsertRecommendation(
                        merchantId,
                        "HIGH_PROVIDER_LATENCY",
                        row.get("channel_code") + ":" + row.get("operation"),
                        "WARNING",
                        "Provider latency is elevated",
                        "Average routed latency is above five seconds. Consider a lower-latency routing preference or provider investigation.",
                        "{\"averageLatencyMs\":" + latency + "}");
            }
        }
        Map<String, Object> recurring = one(
                "SELECT COALESCE(SUM(recurring_charge_count),0) total, COALESCE(SUM(recurring_failed_count),0) failed "
                        + "FROM merchant_analytics_daily WHERE merchant_id=:merchant_id AND metric_date>=CURRENT_DATE-INTERVAL 7 DAY",
                new MapSqlParameterSource("merchant_id", merchantId));
        long totalRecurring = number(recurring, "total");
        long failedRecurring = number(recurring, "failed");
        if (totalRecurring >= 10 && failedRecurring * 100L >= totalRecurring * 20L) {
            upsertRecommendation(
                    merchantId,
                    "RECURRING_DUNNING_PRESSURE",
                    "RECURRING",
                    "WARNING",
                    "Recurring payment failures are elevated",
                    "At least 20% of recurring charges failed in the last seven days. Review mandates, payer channels and dunning configuration.",
                    "{\"total\":" + totalRecurring + ",\"failed\":" + failedRecurring + "}");
        }
    }

    public List<Map<String, Object>> daily(long merchantId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return jdbcTemplate.queryForList(
                "SELECT metric_date AS metricDate, transaction_count AS transactionCount, successful_count AS successfulCount, "
                        + "failed_count AS failedCount, transaction_volume AS transactionVolume, refund_count AS refundCount, "
                        + "refund_amount AS refundAmount, split_execution_count AS splitExecutionCount, split_volume AS splitVolume, "
                        + "recurring_charge_count AS recurringChargeCount, recurring_success_count AS recurringSuccessCount, "
                        + "recurring_failed_count AS recurringFailedCount, updated_at AS updatedAt "
                        + "FROM merchant_analytics_daily WHERE merchant_id=:merchant_id AND metric_date BETWEEN :from_date AND :to_date "
                        + "ORDER BY metric_date",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("from_date", from)
                        .addValue("to_date", to));
    }

    public List<Map<String, Object>> providers(long merchantId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        return jdbcTemplate.queryForList(
                "SELECT metric_date AS metricDate, channel_code AS channelCode, operation, routed_count AS routedCount, "
                        + "successful_count AS successfulCount, failed_count AS failedCount, average_latency_ms AS averageLatencyMs "
                        + "FROM merchant_provider_analytics WHERE merchant_id=:merchant_id AND metric_date BETWEEN :from_date AND :to_date "
                        + "ORDER BY metric_date, channel_code, operation",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("from_date", from)
                        .addValue("to_date", to));
    }

    public List<Map<String, Object>> recommendations(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT recommendation_code AS recommendationCode, subject_reference AS subjectReference, severity, title, detail, "
                        + "evidence_json AS evidence, status, generated_at AS generatedAt, acknowledged_by AS acknowledgedBy, "
                        + "acknowledged_at AS acknowledgedAt FROM merchant_analytics_recommendations "
                        + "WHERE merchant_id=:merchant_id ORDER BY CASE severity WHEN 'CRITICAL' THEN 1 WHEN 'WARNING' THEN 2 ELSE 3 END, generated_at DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public void acknowledge(long merchantId, String recommendationCode, String subjectReference, String actor) {
        int updated = jdbcTemplate.update(
                "UPDATE merchant_analytics_recommendations SET status='ACKNOWLEDGED', acknowledged_by=:actor, acknowledged_at=CURRENT_TIMESTAMP "
                        + "WHERE merchant_id=:merchant_id AND recommendation_code=:code AND subject_reference=:subject AND status='OPEN'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("code", required(recommendationCode, "recommendationCode"))
                        .addValue("subject", required(subjectReference, "subjectReference"))
                        .addValue("actor", required(actor, "actor")));
        if (updated == 0) {
            throw new PaymentGatewayException("Open recommendation was not found");
        }
    }

    private void upsertRecommendation(long merchantId, String code, Object subject, String severity, String title, String detail, String evidence) {
        jdbcTemplate.update(
                "INSERT INTO merchant_analytics_recommendations "
                        + "(merchant_id, recommendation_code, subject_reference, severity, title, detail, evidence_json, status) "
                        + "VALUES (:merchant_id, :code, :subject, :severity, :title, :detail, :evidence, 'OPEN') "
                        + "ON DUPLICATE KEY UPDATE severity=VALUES(severity), title=VALUES(title), detail=VALUES(detail), "
                        + "evidence_json=VALUES(evidence_json), generated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("code", code)
                        .addValue("subject", String.valueOf(subject))
                        .addValue("severity", severity)
                        .addValue("title", title)
                        .addValue("detail", detail)
                        .addValue("evidence", evidence));
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource parameters) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, parameters);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private java.math.BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(String.valueOf(value));
    }

    private void requireMerchant(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new PaymentGatewayException("A valid from/to date range is required");
        }
        if (to.isAfter(from.plusDays(366))) {
            throw new PaymentGatewayException("Analytics ranges cannot exceed 366 days");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }
}