package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit N4: the settlement scheduler must consult a merchant's self-service settlement
 * preference (in addition to the existing ops-configured schedule) before opening a batch - a
 * WEEKLY preference only fires on its configured day, and a merchant with no saved preference (or
 * a DAILY one) settles on every due run exactly like before this feature existed.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class SettlementScheduleServiceTest {

    @Test
    void returnsEmptyResultsWhenNoSchedulesAreDue() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        MerchantSettlementPreferenceService preferenceService = mock(MerchantSettlementPreferenceService.class);
        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService, preferenceService);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        assertThat(service.runDueSweeps(LocalDate.parse("2026-07-16"), 2)).isEmpty();
    }

    @Test
    void skipsTheSweepWhenTheMerchantsWeeklyPreferenceDayDoesNotMatchTheRunDate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        MerchantSettlementPreferenceService preferenceService = mock(MerchantSettlementPreferenceService.class);
        stubDueSchedule(jdbcTemplate, 9L, 5L, new BigDecimal("500000"), new BigDecimal("100000"));
        MerchantSettlementPreference weeklyMonday = new MerchantSettlementPreference(
            1L, 5L, "WEEKLY", "MONDAY", BigDecimal.ZERO, "merchant-user", Instant.now(), Instant.now());
        LocalDate tuesday = LocalDate.parse("2024-01-02");
        when(preferenceService.find(5L)).thenReturn(Optional.of(weeklyMonday));
        when(preferenceService.isDueOn(weeklyMonday, tuesday)).thenReturn(false);

        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService, preferenceService);
        List<SettlementSweepResult> results = service.runDueSweeps(tuesday, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("SKIPPED");
        assertThat(results.get(0).getMessage()).contains("settlement preference");
        verify(opsService, never()).openBatch(anyString(), anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void skipsTheSweepWhenTheAmountIsBelowTheMerchantsMinimumSettlementAmount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        MerchantSettlementPreferenceService preferenceService = mock(MerchantSettlementPreferenceService.class);
        stubDueSchedule(jdbcTemplate, 9L, 5L, new BigDecimal("150000"), new BigDecimal("100000"));
        MerchantSettlementPreference dailyWithHighMinimum = new MerchantSettlementPreference(
            1L, 5L, "DAILY", null, new BigDecimal("100000"), "merchant-user", Instant.now(), Instant.now());
        LocalDate runDate = LocalDate.parse("2024-01-02");
        when(preferenceService.find(5L)).thenReturn(Optional.of(dailyWithHighMinimum));
        when(preferenceService.isDueOn(dailyWithHighMinimum, runDate)).thenReturn(true);

        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService, preferenceService);
        // available (150000) - minimum retained (100000) = 50000 sweep amount, below the merchant's
        // own 100000 minimum settlement threshold.
        List<SettlementSweepResult> results = service.runDueSweeps(runDate, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("SKIPPED");
        assertThat(results.get(0).getMessage()).contains("minimum settlement amount");
        verify(opsService, never()).openBatch(anyString(), anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void opensTheBatchWhenTheMerchantsWeeklyPreferenceDayMatchesTheRunDate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        MerchantSettlementPreferenceService preferenceService = mock(MerchantSettlementPreferenceService.class);
        stubDueSchedule(jdbcTemplate, 9L, 5L, new BigDecimal("500000"), new BigDecimal("100000"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantSettlementPreference weeklyMonday = new MerchantSettlementPreference(
            1L, 5L, "WEEKLY", "MONDAY", BigDecimal.ZERO, "merchant-user", Instant.now(), Instant.now());
        LocalDate monday = LocalDate.parse("2024-01-01");
        when(preferenceService.find(5L)).thenReturn(Optional.of(weeklyMonday));
        when(preferenceService.isDueOn(weeklyMonday, monday)).thenReturn(true);

        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService, preferenceService);
        List<SettlementSweepResult> results = service.runDueSweeps(monday, 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("OPENED");
        verify(opsService).openBatch(anyString(), eq("MTNMOMO"), eq("COLLECTIONS"), eq("UGX"), any(BigDecimal.class), eq("settlement-scheduler"));
    }

    @Test
    void opensTheBatchWhenTheMerchantHasNoSavedSettlementPreference() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SettlementOpsService opsService = mock(SettlementOpsService.class);
        MerchantSettlementPreferenceService preferenceService = mock(MerchantSettlementPreferenceService.class);
        stubDueSchedule(jdbcTemplate, 9L, 5L, new BigDecimal("500000"), new BigDecimal("100000"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(preferenceService.find(5L)).thenReturn(Optional.empty());

        SettlementScheduleService service = new SettlementScheduleService(jdbcTemplate, opsService, preferenceService);
        List<SettlementSweepResult> results = service.runDueSweeps(LocalDate.parse("2024-01-02"), 2);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("OPENED");
        verify(opsService).openBatch(anyString(), eq("MTNMOMO"), eq("COLLECTIONS"), eq("UGX"), any(BigDecimal.class), eq("settlement-scheduler"));
    }

    private void stubDueSchedule(NamedParameterJdbcTemplate jdbcTemplate, long scheduleId, long merchantId,
            BigDecimal availableBalance, BigDecimal minimumRetainedBalance) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(scheduleId);
            when(row.getLong("merchant_id")).thenReturn(merchantId);
            when(row.getString("provider_code")).thenReturn("MTNMOMO");
            when(row.getString("channel_code")).thenReturn("COLLECTIONS");
            when(row.getString("currency")).thenReturn("UGX");
            when(row.getBigDecimal("minimum_retained_balance")).thenReturn(minimumRetainedBalance);
            when(row.getBigDecimal("available_balance")).thenReturn(availableBalance);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }
}
