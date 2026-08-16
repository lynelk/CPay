package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class FinanceDailyCloseGateTest {

    private static final Date BUSINESS_DATE = Date.valueOf("2026-08-15");

    @Test
    void dailyCloseApprovesWhenAllPreconditionsHoldAndLedgerIsBalanced() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(closeRowWithFlags(true, true, true, true, true, true)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0)
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FinanceOperationsController controller =
                new FinanceOperationsController(jdbcTemplate, new ObjectMapper());

        controller.decideDailyClose(
                1L, Map.of("status", "APPROVED", "actor", "finance-admin"));

        Object[] args = capturedUpdateArgs(jdbcTemplate);
        assertThat(args[0]).isEqualTo("APPROVED");
    }

    @Test
    void dailyCloseIsBlockedWhenAStoredPreconditionIsNotMet() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(
                                closeRowWithFlags(
                                        true, true, true, true, true, false /* no signoff */)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0)
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FinanceOperationsController controller =
                new FinanceOperationsController(jdbcTemplate, new ObjectMapper());

        controller.decideDailyClose(
                1L, Map.of("status", "APPROVED", "actor", "finance-admin"));

        Object[] args = capturedUpdateArgs(jdbcTemplate);
        assertThat(args[0]).isEqualTo("BLOCKED");
        assertThat((String) args[7]).contains("finance owner has not signed off");
    }

    @Test
    void dailyCloseIsBlockedWhenTrialBalanceRunIsUnbalanced() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(closeRowWithFlags(true, true, true, true, true, true)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0)
                .thenReturn(1); // one unbalanced trial-balance run
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FinanceOperationsController controller =
                new FinanceOperationsController(jdbcTemplate, new ObjectMapper());

        controller.decideDailyClose(
                1L, Map.of("status", "CLOSED", "actor", "finance-admin"));

        Object[] args = capturedUpdateArgs(jdbcTemplate);
        assertThat(args[0]).isEqualTo("BLOCKED");
        assertThat((String) args[7]).contains("unbalanced trial-balance run");
    }

    @Test
    void dailyCloseIsBlockedWhenHighSeverityReconciliationExceptionIsOpen() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(closeRowWithFlags(true, true, true, true, true, true)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(2)
                .thenReturn(0); // two open high-severity exceptions
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FinanceOperationsController controller =
                new FinanceOperationsController(jdbcTemplate, new ObjectMapper());

        controller.decideDailyClose(
                1L, Map.of("status", "APPROVED", "actor", "finance-admin"));

        Object[] args = capturedUpdateArgs(jdbcTemplate);
        assertThat(args[0]).isEqualTo("BLOCKED");
        assertThat((String) args[7]).contains("high-severity reconciliation exception");
    }

    @Test
    void dailyCloseIsBlockedWhenSubmittedPreconditionsOverrideStoredTruthfully() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // Stored row claims all preconditions, but the submitted decision says signoff missing.
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(closeRowWithFlags(true, true, true, true, true, true)));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any()))
                .thenReturn(0)
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        FinanceOperationsController controller =
                new FinanceOperationsController(jdbcTemplate, new ObjectMapper());

        controller.decideDailyClose(
                1L,
                Map.of(
                        "status",
                        "APPROVED",
                        "actor",
                        "finance-admin",
                        "financeOwnerSignedOff",
                        false));

        Object[] args = capturedUpdateArgs(jdbcTemplate);
        assertThat(args[0]).isEqualTo("BLOCKED");
        assertThat((String) args[7]).contains("finance owner has not signed off");
    }

    private static Map<String, Object> closeRowWithFlags(
            boolean providerStatements,
            boolean reconciliationImport,
            boolean unmatchedItems,
            boolean highSeverityControls,
            boolean makerChecker,
            boolean financeOwner) {
        return Map.of(
                "id",
                1L,
                "business_date",
                BUSINESS_DATE,
                "provider_statements_received",
                providerStatements,
                "reconciliation_import_completed",
                reconciliationImport,
                "unmatched_items_reviewed",
                unmatchedItems,
                "high_severity_controls_resolved",
                highSeverityControls,
                "maker_checker_approvals_complete",
                makerChecker,
                "finance_owner_signed_off",
                financeOwner);
    }

    private static Object[] capturedUpdateArgs(JdbcTemplate jdbcTemplate) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("SET status = ?"), captor.capture());
        return captor.getValue();
    }
}
