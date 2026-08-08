package net.citotech.cito.billing.tenancy;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Read-only JDBC access to {@code billing_tenants} (Flyway {@code V38}). */
@Repository
public class BillingTenantRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingTenantRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BillingTenant> findByMerchantId(long merchantId) {
        SqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        List<BillingTenant> rows =
                jdbcTemplate.query(
                        "SELECT id, merchant_id, tenant_type, tenant_status, created_at "
                                + "FROM billing_tenants WHERE merchant_id=:merchant_id",
                        p,
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public Optional<BillingTenant> findById(long id) {
        SqlParameterSource p = new MapSqlParameterSource("id", id);
        List<BillingTenant> rows =
                jdbcTemplate.query(
                        "SELECT id, merchant_id, tenant_type, tenant_status, created_at "
                                + "FROM billing_tenants WHERE id=:id",
                        p,
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private BillingTenant mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BillingTenant(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("tenant_type"),
                rs.getString("tenant_status"),
                rs.getTimestamp("created_at").toInstant());
    }
}
