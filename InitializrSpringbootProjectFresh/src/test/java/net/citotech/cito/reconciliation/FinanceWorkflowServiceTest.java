package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Maker-checker coverage for the finance daily close (audit finding: daily close was a single-actor
 * write). The maker submit rejects out-of-tolerance variance before writing, and a checker approval
 * can only flip a PENDING_APPROVAL row whose requester differs from the approver.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class FinanceWorkflowServiceTest {

    @Test
    void submitWritesPendingApprovalWhenVarianceIsWithinTolerance() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubSummary(jdbcTemplate, 0, 0, 0, new BigDecimal("0"));
        stubSetting(jdbcTemplate, "finance_variance_tolerance_amount", "1000");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("PENDING_APPROVAL");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(42L);
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        long id = service.submitDailyClose("2026-08-01", "UGX", "finance-maker");

        assertThat(id).isEqualTo(42L);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("PENDING_APPROVAL"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void submitRejectsOutOfToleranceVarianceBeforeWriting() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubSummary(jdbcTemplate, 5, 2, 1, new BigDecimal("5000"));
        stubSetting(jdbcTemplate, "finance_variance_tolerance_amount", "1000");
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        assertThatThrownBy(() -> service.submitDailyClose("2026-08-01", "UGX", "finance-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("variance");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void submitFailsClosedWhenNoToleranceIsConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubSummary(jdbcTemplate, 5, 1, 1, new BigDecimal("250"));
        stubSetting(jdbcTemplate, "finance_variance_tolerance_amount", null);
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        assertThatThrownBy(() -> service.submitDailyClose("2026-08-01", "UGX", "finance-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("variance");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void approveWithADifferentActorUpdatesTheRowToClosed() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        int updated = service.approveDailyClose("2026-08-01", "UGX", "finance-checker");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("requested_by<>"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void approveWithTheSameActorAsMakerReturnsZeroAndIsSurfacedAsAnError() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        int updated = service.approveDailyClose("2026-08-01", "UGX", "finance-maker");

        assertThat(updated).isZero();
    }

    @Test
    void rejectRecordsTheReasonAndKeepsTheRowPendingForAResubmit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        int updated =
                service.rejectDailyClose("2026-08-01", "UGX", "finance-checker", "duplicate rows");

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("rejection_reason"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void reportSurfacesUnmatchedVarianceForTheFinanceDashboard() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubSummary(jdbcTemplate, 10, 2, 1, new BigDecimal("300"));
        FinanceWorkflowService service = new FinanceWorkflowService(jdbcTemplate);

        Map<String, Object> report = service.report("UGX");

        assertThat(report.get("matchedCount")).isEqualTo(10);
        assertThat(report.get("manualMatchCount")).isEqualTo(2);
        assertThat(report.get("exceptionCount")).isEqualTo(1);
        assertThat(report.get("unmatchedAmount")).isEqualTo(new BigDecimal("300"));
    }

    private void stubSummary(
            NamedParameterJdbcTemplate jdbcTemplate,
            int matched,
            int manualMatched,
            int exceptions,
            BigDecimal unmatchedAmount) {
        when(jdbcTemplate.queryForObject(
                        eq(
                                "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND match_status=:status"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(matched, manualMatched);
        when(jdbcTemplate.queryForObject(
                        eq(
                                "SELECT COUNT(*) FROM reconciliation_records WHERE currency=:currency AND exception_category IS NOT NULL"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(exceptions);
        when(jdbcTemplate.queryForObject(
                        eq(
                                "SELECT COALESCE(SUM(amount),0) FROM reconciliation_records WHERE currency=:currency AND match_status=:status"),
                        any(MapSqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(unmatchedAmount);
    }

    private void stubSetting(NamedParameterJdbcTemplate jdbcTemplate, String key, String value) {
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(value == null ? List.of() : List.of(value));
    }
}
