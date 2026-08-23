package net.citotech.cito.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MarketplaceSplitSimulationServiceTest {

    @Test
    void simulationCalculatesAllocationWithoutPersistingExecution() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 5L,
                                        "currency_code", "UGX",
                                        "allocation_mode", "PERCENTAGE",
                                        "platform_fee_type", "PERCENTAGE",
                                        "platform_fee_value", new BigDecimal("10"),
                                        "fee_bearer", "PLATFORM")),
                        List.of(
                                Map.of(
                                        "subaccount_id", 11L,
                                        "subaccount_reference", "SUB-1",
                                        "display_name", "Seller A",
                                        "allocation_value", new BigDecimal("60"),
                                        "priority_rank", 1),
                                Map.of(
                                        "subaccount_id", 12L,
                                        "subaccount_reference", "SUB-2",
                                        "display_name", "Seller B",
                                        "allocation_value", new BigDecimal("40"),
                                        "priority_rank", 2)));
        MarketplaceSplitSimulationService service =
                new MarketplaceSplitSimulationService(jdbcTemplate);

        Map<String, Object> result =
                service.simulate(7L, "RULE-1", "UGX", new BigDecimal("10000"));

        assertThat(result.get("platformFeeAmount")).isEqualTo(new BigDecimal("1000.000000"));
        assertThat(result.get("distributableAmount")).isEqualTo(new BigDecimal("9000.000000"));
        assertThat((List<?>) result.get("allocations")).hasSize(2);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
