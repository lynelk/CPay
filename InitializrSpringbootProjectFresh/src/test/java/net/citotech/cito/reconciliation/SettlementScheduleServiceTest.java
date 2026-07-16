package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SettlementScheduleServiceTest {

    @Test
    void returnsEmptyResultsWhenNoSchedulesAreDue() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        assertThat(service.runDueSweeps(LocalDate.parse("2026-07-16"), 2)).isEmpty();
    }
}
