package net.citotech.cito.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import net.citotech.cito.crossborder.TreasuryPositionService.TreasuryPositionRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Treasury-position read view (audit finding: cross-border reservation writes to {@code
 * treasury_positions} but finance/ops had no API to read available vs reserved balances). Covers
 * the list, the by-currency lookup, the net-available computation, and the strict ISO-currency
 * guard.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class TreasuryPositionServiceTest {

    @Test
    void listPositionsMapsRowsAndComputesNetAvailable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet row = mock(ResultSet.class);
                            when(row.getString("currency")).thenReturn("UGX");
                            when(row.getBigDecimal("available_balance"))
                                    .thenReturn(new BigDecimal("10000.0000"));
                            when(row.getBigDecimal("reserved_balance"))
                                    .thenReturn(new BigDecimal("2000.0000"));
                            when(row.getString("position_status")).thenReturn("ACTIVE");
                            when(row.getString("updated_at")).thenReturn("2026-08-03 10:00:00");
                            return java.util.List.of(mapper.mapRow(row, 1));
                        });
        TreasuryPositionService service = new TreasuryPositionService(jdbcTemplate);

        java.util.List<TreasuryPositionRow> rows = service.listPositions();

        assertThat(rows).hasSize(1);
        TreasuryPositionRow row = rows.get(0);
        assertThat(row.currency()).isEqualTo("UGX");
        assertThat(row.availableBalance()).isEqualByComparingTo("10000.0000");
        assertThat(row.reservedBalance()).isEqualByComparingTo("2000.0000");
        assertThat(row.netAvailable()).isEqualByComparingTo("8000.0000");
        assertThat(row.status()).isEqualTo("ACTIVE");
        assertThat(row.updatedAt()).isEqualTo("2026-08-03 10:00:00");
    }

    @Test
    void netAvailableHandlesNullBalancesAsZero() {
        TreasuryPositionRow row = new TreasuryPositionRow("UGX", null, null, "ACTIVE", null);

        assertThat(row.netAvailable()).isEqualByComparingTo("0");
    }

    @Test
    void findByCurrencyReturnsNullWhenNoActivePosition() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(java.util.List.of());
        TreasuryPositionService service = new TreasuryPositionService(jdbcTemplate);

        assertThat(service.findByCurrency("UGX")).isNull();
    }

    @Test
    void findByCurrencyNormalizesAndFindsThePosition() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet row = mock(ResultSet.class);
                            when(row.getString("currency")).thenReturn("KES");
                            when(row.getBigDecimal("available_balance"))
                                    .thenReturn(new BigDecimal("5000.0000"));
                            when(row.getBigDecimal("reserved_balance"))
                                    .thenReturn(new BigDecimal("0.0000"));
                            when(row.getString("position_status")).thenReturn("ACTIVE");
                            when(row.getString("updated_at")).thenReturn("2026-08-03 10:00:00");
                            return java.util.List.of(mapper.mapRow(row, 1));
                        });
        TreasuryPositionService service = new TreasuryPositionService(jdbcTemplate);

        TreasuryPositionRow row = service.findByCurrency("kes");

        assertThat(row).isNotNull();
        assertThat(row.currency()).isEqualTo("KES");
    }

    @Test
    void findByCurrencyRejectsNonIsoCurrency() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TreasuryPositionService service = new TreasuryPositionService(jdbcTemplate);

        assertThatThrownBy(() -> service.findByCurrency("UG"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("ISO currency code");
    }
}
