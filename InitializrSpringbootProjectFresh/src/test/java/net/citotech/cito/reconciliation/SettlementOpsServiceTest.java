package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"rawtypes", "unchecked"})
class SettlementOpsServiceTest {

    @Test
    void newBatchIsInsertedAndPostedOnce() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService ledger = mock(DoubleEntryLedgerService.class);
        when(jdbc.query(
                        contains("FOR UPDATE"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of());
        when(jdbc.update(
                        contains("INSERT INTO reconciliation_settlement_batches"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        SettlementOpsService service = new SettlementOpsService(jdbc, ledger);

        service.openBatch("SET-1", "mtn", "mtn_momo", "ugx", new BigDecimal("1000.12345"), "maker");

        verify(jdbc)
                .update(
                        contains("INSERT INTO reconciliation_settlement_batches"),
                        any(MapSqlParameterSource.class));
        verify(ledger)
                .post(
                        org.mockito.ArgumentMatchers.eq("settlement:SET-1"),
                        org.mockito.ArgumentMatchers.eq("SETTLEMENT"),
                        org.mockito.ArgumentMatchers.eq("SET-1"),
                        anyString(),
                        any());
    }

    @Test
    void conflictingReplayFailsBeforeLedgerCanDiverge() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService ledger = mock(DoubleEntryLedgerService.class);
        java.sql.ResultSet row = mock(java.sql.ResultSet.class);
        when(row.getString("provider_code")).thenReturn("MTN");
        when(row.getString("channel_code")).thenReturn("MTN_MOMO");
        when(row.getString("currency")).thenReturn("UGX");
        when(row.getBigDecimal("expected_amount")).thenReturn(new BigDecimal("1000.0000"));
        when(jdbc.query(
                        contains("FOR UPDATE"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
        SettlementOpsService service = new SettlementOpsService(jdbc, ledger);

        assertThatThrownBy(
                        () ->
                                service.openBatch(
                                        "SET-1",
                                        "MTN",
                                        "MTN_MOMO",
                                        "UGX",
                                        new BigDecimal("1100"),
                                        "maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("different commercial attributes");
        verify(ledger, never()).post(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void identicalReplayIsIdempotentAndDoesNotRewriteBatch() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService ledger = mock(DoubleEntryLedgerService.class);
        java.sql.ResultSet row = mock(java.sql.ResultSet.class);
        when(row.getString("provider_code")).thenReturn("MTN");
        when(row.getString("channel_code")).thenReturn("MTN_MOMO");
        when(row.getString("currency")).thenReturn("UGX");
        when(row.getBigDecimal("expected_amount")).thenReturn(new BigDecimal("1000.0000"));
        when(jdbc.query(
                        contains("FOR UPDATE"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
        SettlementOpsService service = new SettlementOpsService(jdbc, ledger);

        service.openBatch("SET-1", "mtn", "mtn_momo", "ugx", new BigDecimal("1000"), "maker");

        verify(jdbc, never())
                .update(
                        contains("INSERT INTO reconciliation_settlement_batches"),
                        any(MapSqlParameterSource.class));
        verify(ledger)
                .post(
                        org.mockito.ArgumentMatchers.eq("settlement:SET-1"),
                        org.mockito.ArgumentMatchers.eq("SETTLEMENT"),
                        org.mockito.ArgumentMatchers.eq("SET-1"),
                        anyString(),
                        any());
    }

    @Test
    void makerCheckerCloseRemainsEnforced() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbc, mock(DoubleEntryLedgerService.class));

        assertThat(service.requestBatchClose("SET-1", "maker")).isEqualTo(1);
        assertThat(service.approveBatchClose("SET-1", "checker")).isEqualTo(1);
        verify(jdbc).update(contains("close_requested_by<>"), any(MapSqlParameterSource.class));
    }

    @Test
    void requireApprovedExplainsNonPendingState() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("OPEN"));
        SettlementOpsService service =
                new SettlementOpsService(jdbc, mock(DoubleEntryLedgerService.class));

        assertThatThrownBy(() -> service.requireApproved("SET-1", "checker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not awaiting approval");
    }
}
