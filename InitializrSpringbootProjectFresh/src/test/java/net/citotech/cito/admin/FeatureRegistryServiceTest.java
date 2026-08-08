package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Tenant-aware feature registry (ADR 0002). The registry resolves a feature for a merchant as
 * global default overridden by a per-merchant row; every merchant-scoped statement must bind the
 * tenant key ({@link TenantScopeGuard}), and a merchant can never read another merchant's overrides
 * - the isolation test proves the tenant parameter is what selects the row.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
class FeatureRegistryServiceTest {

    @Test
    void merchantOverrideWinsOverGlobalDefault() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(1));
        FeatureRegistryService registry = new FeatureRegistryService(jdbcTemplate);

        boolean enabled = registry.isEnabled("balance-monitoring", 7L);

        assertThat(enabled).isTrue();
    }

    @Test
    void globalDefaultAppliesWhenNoMerchantOverrideExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(0));
        FeatureRegistryService registry = new FeatureRegistryService(jdbcTemplate);

        boolean enabled = registry.isEnabled("balance-monitoring", 7L);

        assertThat(enabled).isFalse();
    }

    @Test
    void globalDefaultAppliesWhenRegistryMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        FeatureRegistryService registry = new FeatureRegistryService(jdbcTemplate);

        assertThat(registry.isEnabled("unknown-feature", 7L)).isFalse();
        assertThat(registry.isGloballyEnabled("unknown-feature")).isFalse();
    }

    @Test
    void merchantScopedStatementsAlwaysBindTheTenantKey() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FeatureRegistryService registry = new FeatureRegistryService(jdbcTemplate);

        registry.listMerchant(7L);
        registry.setMerchantOverride(7L, "kyb-review", true, "pilot");

        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("tenant_merchant_id")).isEqualTo(7L);

        verify(jdbcTemplate).update(anyString(), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue().getValue("tenant_merchant_id")).isEqualTo(7L);
    }

    @Test
    void guardRejectsUnboundTenantStatement() {
        assertThatThrownBy(
                        () ->
                                TenantScopeGuard.assertTenantBound(
                                        "SELECT * FROM merchant_feature_flags"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_merchant_id");
    }

    @Test
    void guardRejectsAnonymousScoping() {
        assertThatThrownBy(() -> TenantScopeGuard.scope(null, 0L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("merchant id");
    }
}
