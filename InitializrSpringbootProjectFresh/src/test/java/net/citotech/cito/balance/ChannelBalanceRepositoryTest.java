package net.citotech.cito.balance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"rawtypes", "unchecked"})
class ChannelBalanceRepositoryTest {

    @Test
    void readsThePendingBalanceColumnDefinedByTheMigration() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        any(String.class), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        ChannelBalanceRepository repository = new ChannelBalanceRepository(jdbcTemplate);

        repository.findByMerchant(42L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate)
                .query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue()).contains("pending_balance");
        assertThat(sql.getValue()).doesNotContain("reserved_balance");
    }

    @Test
    void upsertWritesThePendingBalanceColumnDefinedByTheMigration() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ChannelBalanceRepository repository = new ChannelBalanceRepository(jdbcTemplate);

        repository.upsert(
                42L,
                "mtn_momo",
                "MTNMoMoPaymentGateway",
                "UGX",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("5.00"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), parameters.capture());
        assertThat(sql.getValue()).contains("pending_balance");
        assertThat(sql.getValue()).doesNotContain("reserved_balance");
        assertThat(parameters.getValue().getValue("pending_balance"))
                .isEqualTo(new BigDecimal("5.00"));
    }
}
