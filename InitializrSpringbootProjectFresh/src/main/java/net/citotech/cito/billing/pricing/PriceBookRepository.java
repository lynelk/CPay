package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code billing_price_book_versions}/{@code billing_price_components} (Flyway
 * {@code V43}): read paths for {@link PriceResolver}/{@link RatingEngine}, write paths for {@link
 * PriceBookAuthoringService}.
 */
@Repository
public class PriceBookRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceBookRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Current-time compatibility lookup used by authoring/admin read surfaces. */
    public List<PriceBookVersion> findActiveVersions(
            Long billingTenantId, String serviceCode, String meterCode, String chargeType) {
        return findVersionsAt(
                billingTenantId, serviceCode, meterCode, chargeType, Instant.now());
    }

    /**
     * Resolves the version that was effective at the supplied business/event time. Rating and
     * rerating must use this method rather than wall-clock time so historical usage remains
     * reproducible after a price-book change.
     */
    public List<PriceBookVersion> findVersionsAt(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required for effective-dated price lookup");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        p.addValue("as_of", Timestamp.from(asOf));
        return jdbcTemplate.query(
                "SELECT id, billing_tenant_id, service_code, meter_code, charge_type, currency, "
                        + "version_no, effective_from, effective_to FROM billing_price_book_versions "
                        + "WHERE (billing_tenant_id <=> :billing_tenant_id) AND service_code=:service_code "
                        + "AND meter_code=:meter_code AND charge_type=:charge_type "
                        + "AND effective_from <= :as_of "
                        + "AND (effective_to IS NULL OR effective_to > :as_of) "
                        + "ORDER BY effective_from DESC LIMIT 1",
                p,
                this::mapVersion);
    }

    /** Exact immutable version lookup used by approved contract overrides. */
    public Optional<PriceBookVersion> findVersionById(long id) {
        List<PriceBookVersion> rows =
                jdbcTemplate.query(
                        "SELECT id, billing_tenant_id, service_code, meter_code, charge_type, currency, "
                                + "version_no, effective_from, effective_to FROM billing_price_book_versions "
                                + "WHERE id=:id LIMIT 1",
                        new MapSqlParameterSource("id", id),
                        this::mapVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<PriceComponent> findComponents(long priceBookVersionId) {
        MapSqlParameterSource p =
                new MapSqlParameterSource("price_book_version_id", priceBookVersionId);
        return jdbcTemplate.query(
                "SELECT id, price_book_version_id, component_type, sequence_no, flat_amount, "
                        + "percentage_rate, tier_definition FROM billing_price_components "
                        + "WHERE price_book_version_id=:price_book_version_id ORDER BY sequence_no ASC",
                p,
                this::mapComponent);
    }

    /** Highest existing version number for this key, plus one. */
    public int nextVersionNo(
            Long billingTenantId, String serviceCode, String meterCode, String chargeType) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        Integer max =
                jdbcTemplate.queryForObject(
                        "SELECT MAX(version_no) FROM billing_price_book_versions "
                                + "WHERE (billing_tenant_id <=> :billing_tenant_id) AND service_code=:service_code "
                                + "AND meter_code=:meter_code AND charge_type=:charge_type",
                        p,
                        Integer.class);
        return max == null ? 1 : max + 1;
    }

    /** Closes currently-open versions for this key at the new version's effective time. */
    public int closeOpenVersions(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            Instant effectiveTo) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        p.addValue("effective_to", Timestamp.from(effectiveTo));
        return jdbcTemplate.update(
                "UPDATE billing_price_book_versions SET effective_to=:effective_to "
                        + "WHERE (billing_tenant_id <=> :billing_tenant_id) AND service_code=:service_code "
                        + "AND meter_code=:meter_code AND charge_type=:charge_type AND effective_to IS NULL",
                p);
    }

    public long insertVersion(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            String currency,
            int versionNo,
            Instant effectiveFrom,
            String createdBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        p.addValue("currency", currency);
        p.addValue("version_no", versionNo);
        p.addValue("effective_from", Timestamp.from(effectiveFrom));
        p.addValue("created_by", createdBy);
        jdbcTemplate.update(
                "INSERT INTO billing_price_book_versions (billing_tenant_id, service_code, meter_code, "
                        + "charge_type, currency, version_no, effective_from, created_by) "
                        + "VALUES (:billing_tenant_id, :service_code, :meter_code, :charge_type, "
                        + ":currency, :version_no, :effective_from, :created_by)",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    public void insertComponent(
            long priceBookVersionId,
            String componentType,
            int sequenceNo,
            BigDecimal flatAmount,
            BigDecimal percentageRate,
            String tierDefinitionJson) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("price_book_version_id", priceBookVersionId);
        p.addValue("component_type", componentType);
        p.addValue("sequence_no", sequenceNo);
        p.addValue("flat_amount", flatAmount);
        p.addValue("percentage_rate", percentageRate);
        p.addValue("tier_definition", tierDefinitionJson);
        jdbcTemplate.update(
                "INSERT INTO billing_price_components (price_book_version_id, component_type, "
                        + "sequence_no, flat_amount, percentage_rate, tier_definition) "
                        + "VALUES (:price_book_version_id, :component_type, :sequence_no, "
                        + ":flat_amount, :percentage_rate, :tier_definition)",
                p);
    }

    private PriceBookVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        Object tenantIdObj = rs.getObject("billing_tenant_id");
        return new PriceBookVersion(
                rs.getLong("id"),
                tenantIdObj == null ? null : rs.getLong("billing_tenant_id"),
                rs.getString("service_code"),
                rs.getString("meter_code"),
                rs.getString("charge_type"),
                rs.getString("currency"),
                rs.getInt("version_no"),
                rs.getTimestamp("effective_from").toInstant(),
                rs.getTimestamp("effective_to") == null
                        ? null
                        : rs.getTimestamp("effective_to").toInstant());
    }

    private PriceComponent mapComponent(ResultSet rs, int rowNum) throws SQLException {
        return new PriceComponent(
                rs.getLong("id"),
                rs.getLong("price_book_version_id"),
                rs.getString("component_type"),
                rs.getInt("sequence_no"),
                rs.getBigDecimal("flat_amount"),
                rs.getBigDecimal("percentage_rate"),
                rs.getString("tier_definition"));
    }
}
