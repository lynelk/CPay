package net.citotech.cito.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Covers audit B3: payout compensation (fund reversal for a failed payout) now persists its
 * step-by-step progress, so a partial/stuck reversal is a queryable fact rather than only a log
 * line.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class PayoutCompensationSagaServiceTest {

    @Test
    void startInsertsANewSagaRow() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(contains("INSERT INTO payout_compensation_sagas"), any(MapSqlParameterSource.class), any(KeyHolder.class)))
            .thenAnswer(invocation -> {
                KeyHolder holder = invocation.getArgument(2);
                holder.getKeyList().add(Map.of("GENERATED_KEY", 501L));
                return 1;
            });

        PayoutCompensationSagaService service = new PayoutCompensationSagaService(jdbcTemplate);
        long sagaId = service.start(10L, "tx-unique-1", 5L, 5);

        assertThat(sagaId).isEqualTo(501L);
    }

    @Test
    void recordStepCompleteIncrementsProgress() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PayoutCompensationSagaService service = new PayoutCompensationSagaService(jdbcTemplate);

        service.recordStepComplete(501L, "DR_SUSPENSE");

        verify(jdbcTemplate).update(contains("completed_steps=completed_steps+1"), any(MapSqlParameterSource.class));
    }

    @Test
    void findStuckOlderThanReturnsNonTerminalSagasPastTheThreshold() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("saga_status IN"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenAnswer(invocation -> {
                org.springframework.jdbc.core.RowMapper mapper = invocation.getArgument(2);
                java.sql.ResultSet row = mock(java.sql.ResultSet.class);
                when(row.getLong("id")).thenReturn(1L);
                when(row.getLong("transactions_log_id")).thenReturn(10L);
                when(row.getString("tx_unique_id")).thenReturn("tx-unique-1");
                when(row.getLong("merchant_id")).thenReturn(5L);
                when(row.getInt("total_steps")).thenReturn(5);
                when(row.getInt("completed_steps")).thenReturn(2);
                when(row.getString("last_step_name")).thenReturn("DR_SUSPENSE");
                when(row.getString("saga_status")).thenReturn("IN_PROGRESS");
                when(row.getString("last_error")).thenReturn(null);
                return List.of(mapper.mapRow(row, 1));
            });

        PayoutCompensationSagaService service = new PayoutCompensationSagaService(jdbcTemplate);
        List<PayoutCompensationSagaService.StuckSaga> stuck = service.findStuckOlderThan(15);

        assertThat(stuck).hasSize(1);
        assertThat(stuck.get(0).completedSteps()).isEqualTo(2);
        assertThat(stuck.get(0).totalSteps()).isEqualTo(5);
        assertThat(stuck.get(0).sagaStatus()).isEqualTo("IN_PROGRESS");
    }
}
