package net.citotech.cito.identity.metering;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * Append-only JDBC access to {@code validation_usage} (Flyway {@code V79}). Records one usage row
 * per provider attempt; {@code usage_reference} doubles as the idempotency key so a retried relay
 * sweep never double-counts. Billing-agnostic: provider cost is captured here; merchant rating
 * happens in the billing engine via {@code billing_usage_events}.
 */
@Repository
public class ValidationUsageRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ValidationUsageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a usage row if {@code usageReference} is new; otherwise returns the existing row.
     * Concurrent relays racing the same reference are deduped on the unique key, never errored.
     */
    public ValidationUsage insertIfAbsent(ValidationUsage usage) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("usage_reference", usage.usageReference())
                        .addValue("merchant_id", usage.merchantId())
                        .addValue("case_id", usage.caseId())
                        .addValue("check_id", usage.checkId())
                        .addValue("capability", usage.capability())
                        .addValue("provider_code", usage.providerCode())
                        .addValue("provider_operation", usage.providerOperation())
                        .addValue("provider_reference", usage.providerReference())
                        .addValue("provider_cost", usage.providerCost())
                        .addValue("provider_currency", usage.providerCurrency())
                        .addValue(
                                "billable_attempt",
                                usage.billableAttempt() ? "Y" : "N")
                        .addValue("merchant_charge", usage.merchantCharge())
                        .addValue("merchant_currency", usage.merchantCurrency());
        try {
            jdbcTemplate.update(
                    "INSERT INTO validation_usage "
                            + "(usage_reference, merchant_id, case_id, check_id, capability, provider_code, "
                            + " provider_operation, provider_reference, provider_cost, provider_currency, "
                            + " billable_attempt, merchant_charge, merchant_currency) "
                            + "VALUES (:usage_reference, :merchant_id, :case_id, :check_id, :capability, :provider_code, "
                            + " :provider_operation, :provider_reference, :provider_cost, :provider_currency, "
                            + " :billable_attempt, :merchant_charge, :merchant_currency)",
                    p);
        } catch (DuplicateKeyException raced) {
            return findByReference(usage.usageReference()).orElseThrow(() -> raced);
        }
        return findByReference(usage.usageReference())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Inserted validation usage not found: "
                                                + usage.usageReference()));
    }

    /** Usage rows newer than {@code afterId}, oldest first, bounded — the relay sweep cursor. */
    public List<ValidationUsage> since(long afterId, int limit) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("after_id", afterId)
                        .addValue("limit", Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.query(
                "SELECT id, usage_reference, merchant_id, case_id, check_id, capability, provider_code, "
                        + " provider_operation, provider_reference, provider_cost, provider_currency, "
                        + " billable_attempt, merchant_charge, merchant_currency, created_at "
                        + "FROM validation_usage "
                        + "WHERE id > :after_id AND billable_attempt='Y' "
                        + "ORDER BY id ASC LIMIT :limit",
                p,
                this::mapRow);
    }

    private Optional<ValidationUsage> findByReference(String usageReference) {
        List<ValidationUsage> rows =
                jdbcTemplate.query(
                        "SELECT id, usage_reference, merchant_id, case_id, check_id, capability, provider_code, "
                                + " provider_operation, provider_reference, provider_cost, provider_currency, "
                                + " billable_attempt, merchant_charge, merchant_currency, created_at "
                                + "FROM validation_usage WHERE usage_reference=:usage_reference",
                        new MapSqlParameterSource("usage_reference", usageReference),
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private ValidationUsage mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ValidationUsage(
                rs.getLong("id"),
                rs.getString("usage_reference"),
                rs.getLong("merchant_id"),
                nullableLong(rs, "case_id"),
                nullableLong(rs, "check_id"),
                rs.getString("capability"),
                rs.getString("provider_code"),
                rs.getString("provider_operation"),
                rs.getString("provider_reference"),
                rs.getBigDecimal("provider_cost"),
                rs.getString("provider_currency"),
                "Y".equalsIgnoreCase(rs.getString("billable_attempt")),
                rs.getBigDecimal("merchant_charge"),
                rs.getString("merchant_currency"),
                rs.getTimestamp("created_at").toInstant());
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    /**
     * One metered validation attempt. Snapshot of the V79 row; {@code id}/{@code createdAt} are
     * {@code 0}/{@code null} on a not-yet-persisted instance.
     */
    public record ValidationUsage(
            long id,
            String usageReference,
            long merchantId,
            Long caseId,
            Long checkId,
            String capability,
            String providerCode,
            String providerOperation,
            String providerReference,
            BigDecimal providerCost,
            String providerCurrency,
            boolean billableAttempt,
            BigDecimal merchantCharge,
            String merchantCurrency,
            Instant createdAt) {}
}
