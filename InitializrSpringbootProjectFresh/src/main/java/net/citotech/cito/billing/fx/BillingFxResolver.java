package net.citotech.cito.billing.fx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Effective-dated billing FX resolver backed by the platform's canonical {@code fx_rates}. */
@Service
public class BillingFxResolver {
    private static final int FX_SCALE = 12;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingFxResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResolvedFxRate resolve(String sourceCurrency, String targetCurrency, Instant asOf) {
        String source = currency(sourceCurrency);
        String target = currency(targetCurrency);
        if (asOf == null) {
            throw new PaymentGatewayException("FX resolution requires asOf");
        }
        if (source.equals(target)) {
            return new ResolvedFxRate(
                    null, source, target, BigDecimal.ONE, "IDENTITY", asOf, false);
        }

        List<ResolvedFxRate> direct = find(source, target, asOf);
        if (!direct.isEmpty()) {
            return direct.get(0);
        }
        List<ResolvedFxRate> inverse = find(target, source, asOf);
        if (!inverse.isEmpty()) {
            ResolvedFxRate row = inverse.get(0);
            return new ResolvedFxRate(
                    row.sourceFxRateId(),
                    source,
                    target,
                    BigDecimal.ONE.divide(row.rate(), FX_SCALE, RoundingMode.HALF_UP),
                    row.provider(),
                    row.rateAsOf(),
                    true);
        }
        throw new PaymentGatewayException(
                "No active FX rate for " + source + "/" + target + " at " + asOf);
    }

    public BigDecimal convert(BigDecimal amount, ResolvedFxRate rate) {
        if (amount == null || amount.signum() < 0 || rate == null) {
            throw new PaymentGatewayException(
                    "FX conversion requires a non-negative amount and rate");
        }
        return amount.multiply(rate.rate()).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Retains immutable FX evidence. Replaying the same artifact/pair is allowed only when every
     * commercial attribute matches the original snapshot; a conflicting replay fails closed.
     */
    @Transactional
    public void snapshot(
            long billingTenantId,
            String artifactType,
            String artifactReference,
            ResolvedFxRate rate) {
        if (billingTenantId <= 0
                || blank(artifactType)
                || blank(artifactReference)
                || rate == null) {
            throw new PaymentGatewayException(
                    "FX snapshot requires tenant, artifact and resolved rate");
        }
        String type = artifactType.trim().toUpperCase(Locale.ROOT);
        String reference = artifactReference.trim();
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("artifact_type", type)
                        .addValue("artifact_reference", reference)
                        .addValue("source", rate.sourceCurrency())
                        .addValue("target", rate.targetCurrency())
                        .addValue("rate", rate.rate())
                        .addValue("source_id", rate.sourceFxRateId())
                        .addValue("provider", rate.provider())
                        .addValue("as_of", Timestamp.from(rate.rateAsOf()));
        try {
            jdbcTemplate.update(
                    "INSERT INTO billing_fx_snapshots "
                            + "(billing_tenant_id,artifact_type,artifact_reference,source_currency,target_currency,"
                            + "rate,source_fx_rate_id,provider,rate_as_of) "
                            + "VALUES (:tenant,:artifact_type,:artifact_reference,:source,:target,:rate,:source_id,:provider,:as_of)",
                    p);
        } catch (DuplicateKeyException duplicate) {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            "SELECT billing_tenant_id,rate,source_fx_rate_id,provider,rate_as_of "
                                    + "FROM billing_fx_snapshots WHERE artifact_type=:artifact_type "
                                    + "AND artifact_reference=:artifact_reference AND source_currency=:source "
                                    + "AND target_currency=:target LIMIT 1",
                            p);
            if (rows.isEmpty()) {
                throw duplicate;
            }
            Map<String, Object> existing = rows.get(0);
            Long existingSource =
                    existing.get("source_fx_rate_id") == null
                            ? null
                            : ((Number) existing.get("source_fx_rate_id")).longValue();
            if (((Number) existing.get("billing_tenant_id")).longValue() != billingTenantId
                    || ((BigDecimal) existing.get("rate")).compareTo(rate.rate()) != 0
                    || !java.util.Objects.equals(existingSource, rate.sourceFxRateId())
                    || !java.util.Objects.equals(existing.get("provider"), rate.provider())
                    || !((Timestamp) existing.get("rate_as_of"))
                            .toInstant()
                            .equals(rate.rateAsOf())) {
                throw new PaymentGatewayException(
                        "FX snapshot already exists with different immutable evidence");
            }
        }
    }

    private List<ResolvedFxRate> find(String source, String target, Instant asOf) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("source", source)
                        .addValue("target", target)
                        .addValue("as_of", Timestamp.from(asOf));
        return jdbcTemplate.query(
                "SELECT id,source_currency,target_currency,rate,provider_code,valid_from "
                        + "FROM fx_rates WHERE source_currency=:source AND target_currency=:target "
                        + "AND rate_status='ACTIVE' AND valid_from<=:as_of "
                        + "AND (valid_until IS NULL OR valid_until>:as_of) "
                        + "ORDER BY valid_from DESC,id DESC LIMIT 1",
                p,
                (rs, rowNum) ->
                        new ResolvedFxRate(
                                rs.getLong("id"),
                                rs.getString("source_currency"),
                                rs.getString("target_currency"),
                                rs.getBigDecimal("rate"),
                                rs.getString("provider_code"),
                                rs.getTimestamp("valid_from").toInstant(),
                                false));
    }

    private String currency(String value) {
        if (blank(value)) {
            throw new PaymentGatewayException("FX currency is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ResolvedFxRate(
            Long sourceFxRateId,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal rate,
            String provider,
            Instant rateAsOf,
            boolean inverted) {}
}
