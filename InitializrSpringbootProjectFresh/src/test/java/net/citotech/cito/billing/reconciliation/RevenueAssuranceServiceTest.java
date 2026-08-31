package net.citotech.cito.billing.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RevenueAssuranceServiceTest {

    @Test
    void summarizesCompletenessLeakageAndMarginForAuthenticatedTenant() {
        NamedParameterJdbcTemplate jdbc =
                org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L, 2L, 3L, 4L);
        when(jdbc.queryForMap(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        Map.of("negative_margin_count", 5L, "exposure", new BigDecimal("125.50")));
        RevenueAssuranceService service = new RevenueAssuranceService(jdbc);

        RevenueAssuranceSummary result = service.summarize(77L);

        assertThat(result.billingTenantId()).isEqualTo(77L);
        assertThat(result.incompleteSourceWatermarks()).isEqualTo(1L);
        assertThat(result.openMaterialExceptions()).isEqualTo(2L);
        assertThat(result.unratedUsageEvents()).isEqualTo(3L);
        assertThat(result.uninvoicedCustomerCharges()).isEqualTo(4L);
        assertThat(result.negativeMarginCharges()).isEqualTo(5L);
        assertThat(result.negativeMarginExposure()).isEqualByComparingTo("125.50");
    }

    @Test
    void missingTenantFailsClosed() {
        RevenueAssuranceService service =
                new RevenueAssuranceService(
                        org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class));

        assertThatThrownBy(() -> service.summarize(0L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("tenant");
    }
}
