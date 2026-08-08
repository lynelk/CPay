package net.citotech.cito.billing.pricing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only JDBC access to {@code billing_price_book_versions}/{@code billing_price_components}
 * (Flyway {@code V43}).
 */
@Repository
public class PriceBookRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PriceBookRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * The active version for {@code (billingTenantId, serviceCode, meterCode, chargeType)}, at most
     * one row (most-recently-effective first) - {@code billingTenantId} may be {@code null} to look
     * up the global price book.
     */
    public List<PriceBookVersion> findActiveVersions(
            Long billingTenantId, String serviceCode, String meterCode, String chargeType) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        return jdbcTemplate.query(
                "SELECT id, billing_tenant_id, service_code, meter_code, charge_type, currency, "
                        + "version_no, effective_from, effective_to FROM billing_price_book_versions "
                        + "WHERE (billing_tenant_id <=> :billing_tenant_id) AND service_code=:service_code "
                        + "AND meter_code=:meter_code AND charge_type=:charge_type "
                        + "AND effective_from <= CURRENT_TIMESTAMP "
                        + "AND (effective_to IS NULL OR effective_to > CURRENT_TIMESTAMP) "
                        + "ORDER BY effective_from DESC LIMIT 1",
                p,
                this::mapVersion);
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
