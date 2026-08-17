package net.citotech.cito.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.AdminApprovalService;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.admin.AdminPermissionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * P1 §1: settlement batch lifecycle enforcement.
 *
 * <p>Verifies the enforced state machine (OPEN -> CALCULATED -> REVIEW_PENDING -> APPROVED -> PAID
 * -> RECONCILED -> CLOSED with EXCEPTION as a side state), the maker-checker hand-off through
 * {@link AdminApprovalService}, and the close gates for blocking variance and unresolved
 * high-severity reconciliation exceptions.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class SettlementLifecycleServiceTest {

    private static final long SETTLEMENT_ID = 7L;
    private static final String REQUEST_ID = "REQ-1";

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final AdminApprovalService approvalService = mock(AdminApprovalService.class);
    private final AdminPermissionService permissions = mock(AdminPermissionService.class);
    private final AdminAuditService auditService = mock(AdminAuditService.class);
    private final SettlementLifecycleService service =
            new SettlementLifecycleService(
                    jdbcTemplate, approvalService, permissions, auditService);

    @Test
    void calculateMovesAnOpenSettlementToCalculated() {
        stubSettlement("OPEN", "0");

        Map<String, Object> result = service.calculate(SETTLEMENT_ID, "finance-maker", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_calculated");
        assertThat(result).containsEntry("id", SETTLEMENT_ID);
        verify(jdbcTemplate)
                .update(
                        contains("UPDATE finance_settlement_batches"),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO settlement_state_transitions"),
                        any(MapSqlParameterSource.class));
        verify(auditService)
                .record(
                        eq(SettlementLifecycleService.PERMISSION_MANAGE),
                        eq("settlement-calculate"),
                        eq("settlement:7"),
                        anyString(),
                        any(AdminAuditService.AuditContext.class));
    }

    @Test
    void submitForReviewCreatesApprovalRequestAndTransitionsToReviewPending() {
        stubSettlement("CALCULATED", "0");
        when(approvalService.create(
                        eq(SettlementLifecycleService.APPROVAL_TYPE),
                        eq("SETTLEMENT"),
                        eq(String.valueOf(SETTLEMENT_ID)),
                        anyMap(),
                        anyString(),
                        anyString(),
                        eq(REQUEST_ID),
                        any()))
                .thenReturn(42L);

        Map<String, Object> result =
                service.submitForReview(SETTLEMENT_ID, "finance-maker", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_submitted_for_review");
        assertThat(result).containsEntry("approvalRequestId", 42L);
        verify(approvalService)
                .create(
                        eq(SettlementLifecycleService.APPROVAL_TYPE),
                        eq("SETTLEMENT"),
                        eq(String.valueOf(SETTLEMENT_ID)),
                        anyMap(),
                        anyString(),
                        anyString(),
                        eq(REQUEST_ID),
                        any());
        verify(jdbcTemplate)
                .update(
                        contains("UPDATE finance_settlement_batches"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void approveTransitionsReviewPendingSettlementViaChecker() {
        stubSettlement("REVIEW_PENDING", "0");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(settlementRow("REVIEW_PENDING", "0")), List.of(approvalRow(55L)));

        Map<String, Object> result =
                service.approve(SETTLEMENT_ID, "finance-checker", "looks good", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_approved");
        assertThat(result).containsEntry("approvalRequestId", 55L);
        verify(approvalService).approve(55L, "finance-checker", "looks good");
        verify(jdbcTemplate)
                .update(
                        contains("UPDATE finance_settlement_batches"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void rejectReviewReturnsSettlementToCalculated() {
        stubSettlement("REVIEW_PENDING", "0");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(settlementRow("REVIEW_PENDING", "0")), List.of(approvalRow(55L)));

        Map<String, Object> result =
                service.rejectReview(SETTLEMENT_ID, "finance-checker", "missing docs", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_review_rejected");
        verify(approvalService).reject(55L, "finance-checker", "missing docs");
    }

    @Test
    void approveRefusedWhenNoPendingApprovalRequestExists() {
        stubSettlement("REVIEW_PENDING", "0");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(settlementRow("REVIEW_PENDING", "0")), List.of());

        assertThatThrownBy(
                        () -> service.approve(SETTLEMENT_ID, "finance-checker", "note", REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("no pending SETTLEMENT_APPROVAL request");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void closeRefusesReconciledSettlementWithBlockingVariance() {
        stubSettlement("RECONCILED", "12.50");

        assertThatThrownBy(() -> service.close(SETTLEMENT_ID, "finance-maker", REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot close with blocking variance");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void closeRefusesReconciledSettlementWithOpenHighSeverityExceptions() {
        stubSettlement("RECONCILED", "0");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L);

        assertThatThrownBy(() -> service.close(SETTLEMENT_ID, "finance-maker", REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("unresolved high-severity reconciliation exception");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void closeMovesARecognizedSettlementToClosed() {
        stubSettlement("RECONCILED", "0");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        Map<String, Object> result = service.close(SETTLEMENT_ID, "finance-maker", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_closed");
        verify(jdbcTemplate)
                .update(
                        contains("UPDATE finance_settlement_batches"),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(
                        contains("INSERT INTO settlement_state_transitions"),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void transitionStatusRefusesDirectEntryIntoApprovalGuardedStates() {
        stubSettlement("CALCULATED", "0");

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        SETTLEMENT_ID,
                                        "REVIEW_PENDING",
                                        "finance-maker",
                                        null,
                                        REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot transition to REVIEW_PENDING directly");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void transitionStatusRefusesIllegalTransitionFromOpenToClosed() {
        stubSettlement("OPEN", "0");

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        SETTLEMENT_ID, "CLOSED", "finance-maker", null, REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot transition from OPEN to CLOSED");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void transitionStatusRefusesUnknownTargetStatus() {
        stubSettlement("OPEN", "0");

        assertThatThrownBy(
                        () ->
                                service.transitionStatus(
                                        SETTLEMENT_ID, "FROZEN", "finance-maker", null, REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("unknown settlement status");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void markPaidRefusesSettlementThatIsNotApproved() {
        stubSettlement("CALCULATED", "0");

        assertThatThrownBy(() -> service.markPaid(SETTLEMENT_ID, "finance-maker", REQUEST_ID))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot transition from CALCULATED to PAID");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reopenFromExceptionReturnsSettlementToCalculated() {
        stubSettlement("EXCEPTION", "12.50");

        Map<String, Object> result =
                service.reopenFromException(SETTLEMENT_ID, "finance-maker", "resolved", REQUEST_ID);

        assertThat(result).containsEntry("action", "settlement_reopened");
        verify(jdbcTemplate)
                .update(
                        contains("UPDATE finance_settlement_batches"),
                        any(MapSqlParameterSource.class));
    }

    private void stubSettlement(String status, String variance) {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(settlementRow(status, variance)));
    }

    private Map<String, Object> settlementRow(String status, String variance) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", SETTLEMENT_ID);
        row.put("settlement_reference", "STL-2026-08-17-001");
        row.put("business_date", "2026-08-17");
        row.put("currency_code", "UGX");
        row.put("net_amount", new BigDecimal("1250.00"));
        row.put("status", status);
        row.put("variance_amount", new BigDecimal(variance));
        return row;
    }

    private Map<String, Object> approvalRow(long id) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        return row;
    }
}
