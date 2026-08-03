package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citotech.cito.ledger.DoubleEntryLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Maker-checker coverage for settlement batch close (audit finding: batch close was a single-actor
 * write). The submit moves the batch to PENDING_APPROVAL only if it is not already CLOSED, and the
 * checker approval can only close a pending batch whose requester differs from the approver.
 */
@SuppressWarnings("rawtypes")
class SettlementOpsServiceTest {

    @Test
    void requestBatchCloseMovesABatchToPendingApproval() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.requestBatchClose("SET-1", "finance-maker");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("PENDING_APPROVAL"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void requestBatchCloseDefaultsABlankRequesterToSystem() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.requestBatchClose("SET-1", null);

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains(":requested_by"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void approveBatchCloseWithADifferentActorClosesTheBatch() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.approveBatchClose("SET-1", "finance-checker");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("close_requested_by<>"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void approveBatchCloseWithTheSameActorAsRequesterReturnsZero() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.approveBatchClose("SET-1", "finance-maker");

        assertThat(updated).isZero();
    }

    @Test
    void rejectBatchCloseRecordsTheReasonWithoutClosingTheBatch() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.rejectBatchClose("SET-1", "finance-checker", "float mismatch");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("close_rejection_reason"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void closeBatchIsABackwardCompatibleAliasForRequestBatchClose() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        int updated = service.closeBatch("SET-1", "old-caller");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("PENDING_APPROVAL"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void requireApprovedThrowsWhenTheBatchIsNotPendingOrTheActorMatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        when(jdbcTemplate.queryForObject(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        org.mockito.ArgumentMatchers.eq(String.class)))
                .thenReturn("OPEN");
        SettlementOpsService service =
                new SettlementOpsService(jdbcTemplate, mock(DoubleEntryLedgerService.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service.requireApproved("SET-1", "finance-checker"))
                .isInstanceOf(net.citotech.cito.gateway.PaymentGatewayException.class)
                .hasMessageContaining("not awaiting approval");
    }
}
