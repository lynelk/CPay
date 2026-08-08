package net.citotech.cito.billing.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Covers the {@code billing_tenants} (Flyway {@code V38}) read paths. */
@SuppressWarnings({"rawtypes", "unchecked"})
class BillingTenantRepositoryTest {

    @Test
    void findByMerchantIdMapsARowIntoABillingTenant() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet row = tenantRow(7L, 42L, "CPAY_MERCHANT", "ACTIVE");
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });

        Optional<BillingTenant> result =
                new BillingTenantRepository(jdbcTemplate).findByMerchantId(42L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(7L);
        assertThat(result.get().merchantId()).isEqualTo(42L);
        assertThat(result.get().tenantType()).isEqualTo("CPAY_MERCHANT");
        assertThat(result.get().tenantStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void findByMerchantIdReturnsEmptyWhenNoRowMatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        Optional<BillingTenant> result =
                new BillingTenantRepository(jdbcTemplate).findByMerchantId(99L);

        assertThat(result).isEmpty();
    }

    private ResultSet tenantRow(long id, long merchantId, String tenantType, String tenantStatus)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getLong("merchant_id")).thenReturn(merchantId);
        when(rs.getString("tenant_type")).thenReturn(tenantType);
        when(rs.getString("tenant_status")).thenReturn(tenantStatus);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(java.time.Instant.now()));
        return rs;
    }
}
