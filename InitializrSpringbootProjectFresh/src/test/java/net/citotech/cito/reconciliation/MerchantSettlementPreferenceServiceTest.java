package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit N4: merchants can save a DAILY or WEEKLY settlement cadence, an optional preferred
 * day (only meaningful for WEEKLY - DAILY silently drops it), and a minimum settlement threshold;
 * anything outside the fixed DAILY/WEEKLY set is rejected with a clear error, matching the
 * requireOneOf validation style used by {@code net.citotech.cito.fees.FeeScheduleService}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class MerchantSettlementPreferenceServiceTest {

    @Test
    void saveNormalizesAndPersistsAValidWeeklyFrequency() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        stubPreferenceRow(jdbcTemplate, preferenceRow(1L, 42L, "WEEKLY", "TUESDAY", "50000"));

        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);
        MerchantSettlementPreference saved = service.save(42L, "weekly", "tuesday", new BigDecimal("50000"), "merchant@example.com");

        assertThat(saved.settlementFrequency()).isEqualTo("WEEKLY");
        assertThat(saved.settlementDayOfWeek()).isEqualTo("TUESDAY");
        assertThat(saved.minimumSettlementAmount()).isEqualByComparingTo("50000");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("settlement_frequency")).isEqualTo("WEEKLY");
        assertThat(captor.getValue().getValue("settlement_day_of_week")).isEqualTo("TUESDAY");
    }

    @Test
    void saveRejectsAnInvalidFrequency() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);

        assertThatThrownBy(() -> service.save(42L, "MONTHLY", null, BigDecimal.ZERO, "tester"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("settlementFrequency");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void saveRejectsAnInvalidDayOfWeekForWeeklyFrequency() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);

        assertThatThrownBy(() -> service.save(42L, "WEEKLY", "SOMEDAY", BigDecimal.ZERO, "tester"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("settlementDayOfWeek");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void saveRejectsANegativeMinimumSettlementAmount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);

        assertThatThrownBy(() -> service.save(42L, "DAILY", null, new BigDecimal("-1"), "tester"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("minimumSettlementAmount");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void dailyFrequencyDropsAnyProvidedDayOfWeek() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        stubPreferenceRow(jdbcTemplate, preferenceRow(2L, 7L, "DAILY", null, "0"));

        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);
        MerchantSettlementPreference saved = service.save(7L, "DAILY", "MONDAY", null, "tester");

        assertThat(saved.settlementDayOfWeek()).isNull();
        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("settlement_day_of_week")).isNull();
    }

    @Test
    void findReturnsEmptyAndGetOrDefaultFallsBackToDailyWhenNoPreferenceIsSaved() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);

        assertThat(service.find(99L)).isEmpty();
        MerchantSettlementPreference fallback = service.getOrDefault(99L);
        assertThat(fallback.settlementFrequency()).isEqualTo("DAILY");
        assertThat(fallback.settlementDayOfWeek()).isNull();
        assertThat(fallback.minimumSettlementAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void isDueOnMatchesOnlyTheConfiguredWeeklyDay() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantSettlementPreferenceService service = new MerchantSettlementPreferenceService(jdbcTemplate);
        MerchantSettlementPreference weeklyMonday = new MerchantSettlementPreference(
            1L, 5L, "WEEKLY", "MONDAY", BigDecimal.ZERO, "tester", Instant.now(), Instant.now());

        assertThat(service.isDueOn(weeklyMonday, LocalDate.parse("2024-01-01"))).isTrue(); // Monday
        assertThat(service.isDueOn(weeklyMonday, LocalDate.parse("2024-01-02"))).isFalse(); // Tuesday
        assertThat(service.isDueOn(null, LocalDate.parse("2024-01-02"))).isTrue(); // no preference => daily behavior
    }

    private void stubPreferenceRow(NamedParameterJdbcTemplate jdbcTemplate, ResultSet row) {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }

    private ResultSet preferenceRow(long id, long merchantId, String frequency, String dayOfWeek, String minimumAmount) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(id);
            when(row.getLong("merchant_id")).thenReturn(merchantId);
            when(row.getString("settlement_frequency")).thenReturn(frequency);
            when(row.getString("settlement_day_of_week")).thenReturn(dayOfWeek);
            when(row.getBigDecimal("minimum_settlement_amount")).thenReturn(new BigDecimal(minimumAmount));
            when(row.getString("updated_by")).thenReturn("tester");
            when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
            when(row.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return row;
    }
}
