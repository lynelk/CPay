package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * P0 §2: maker-checker approval-request lifecycle. The safety-critical cases are the maker/checker
 * separation - a maker can never approve or reject their own request - and the requirement that
 * every transition is fully audited with the P0 field set.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AdminApprovalServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private AdminPermissionService permissions;
    private AdminAuditService auditService;
    private AdminApprovalService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        permissions = mock(AdminPermissionService.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminApprovalService(jdbcTemplate, permissions, auditService);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "ops.admin",
                                "x",
                                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    void createInsertsARowAndReturnsTheGeneratedId() {
        when(jdbcTemplate.update(
                        contains("INSERT INTO approval_requests"),
                        any(MapSqlParameterSource.class),
                        any(KeyHolder.class),
                        any(String[].class)))
                .thenAnswer(
                        invocation -> {
                            KeyHolder holder = invocation.getArgument(2);
                            holder.getKeyList().add(Map.of("GENERATED_KEY", 77L));
                            return 1;
                        });

        long requestId =
                service.create(
                        "DAILY_CLOSE",
                        "merchant",
                        "42",
                        Map.of("currency", "UGX"),
                        "prev-hash",
                        "new-hash",
                        "req-1",
                        "24");

        assertThat(requestId).isEqualTo(77L);
        verify(permissions).require(eq("APPROVAL_REQUEST_CREATE"), anyString(), contains("42"));
        verify(auditService)
                .record(
                        eq("APPROVAL_REQUEST_CREATE"),
                        eq("APPROVAL_REQUEST_CREATE"),
                        contains("42"),
                        contains("approval_type=DAILY_CLOSE"),
                        any(AdminAuditService.AuditContext.class));
    }

    @Test
    void createRequiresTheMakerPermission() {
        doThrow(new AccessDeniedException("Admin role is required"))
                .when(permissions)
                .require(anyString(), anyString(), anyString());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        "DAILY_CLOSE", "merchant", "42", null, null, null, null, null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void createRejectsBlankRequiredFields() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        " ", "merchant", "42", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvalType");
    }

    @Test
    void makerCannotApproveTheirOwnRequest() {
        Map<String, Object> pending = pendingRow("ops.admin", null);
        stubFindById(pending);

        assertThatThrownBy(() -> service.approve(1L, "ops.admin", "ok"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot approve their own");

        verify(jdbcTemplate, never()).update(contains("SET request_status = 'APPROVED'"), any(MapSqlParameterSource.class));
    }

    @Test
    void checkerApprovesPendingRequestAndAuditsWithFullContext() {
        Map<String, Object> pending = pendingRow("ops.admin", null);
        stubFindById(pending);
        when(jdbcTemplate.update(contains("SET request_status = 'APPROVED'"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        Map<String, Object> result = service.approve(1L, "finance.checker", "looks good");

        assertThat(result.get("requestStatus")).isEqualTo("APPROVED");
        assertThat(result.get("approvedBy")).isEqualTo("finance.checker");
        assertThat(result.get("newStateHash")).isEqualTo("new-hash");

        verify(auditService)
                .record(
                        eq("APPROVAL_REQUEST_APPROVE"),
                        eq("APPROVAL_REQUEST_APPROVE"),
                        contains("merchant:42"),
                        contains("approved_by=finance.checker"),
                        argThat(
                                (AdminAuditService.AuditContext ctx) ->
                                        ctx != null
                                                && "merchant".equals(ctx.resourceType())
                                                && "42".equals(ctx.resourceId())
                                                && "prev-hash".equals(ctx.previousStateHash())
                                                && "new-hash".equals(ctx.newStateHash())
                                                && "looks good".equals(ctx.reasonText())
                                                && "req-1".equals(ctx.requestId())));
    }

    @Test
    void approvingAlreadyApprovedRequestIsIdempotent() {
        Map<String, Object> approved = pendingRow("ops.admin", "APPROVED");
        stubFindById(approved);

        Map<String, Object> result = service.approve(1L, "finance.checker", null);

        assertThat(result.get("request_status")).isEqualTo("APPROVED");
        verify(jdbcTemplate, never()).update(contains("SET request_status = 'APPROVED'"), any(MapSqlParameterSource.class));
    }

    @Test
    void approvingANonPendingRequestIsRefused() {
        stubFindById(pendingRow("ops.admin", "REJECTED"));

        assertThatThrownBy(() -> service.approve(1L, "finance.checker", "ok"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("REJECTED");
    }

    @Test
    void makerCannotRejectTheirOwnRequest() {
        stubFindById(pendingRow("ops.admin", null));

        assertThatThrownBy(() -> service.reject(1L, "ops.admin", "no"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("cannot reject their own");
    }

    @Test
    void rejectRequiresANonBlankReason() {
        stubFindById(pendingRow("ops.admin", null));

        assertThatThrownBy(() -> service.reject(1L, "finance.checker", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void checkerRejectsPendingRequestAndAuditsWithReason() {
        Map<String, Object> pending = pendingRow("ops.admin", null);
        stubFindById(pending);
        when(jdbcTemplate.update(contains("SET request_status = 'REJECTED'"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        Map<String, Object> result = service.reject(1L, "finance.checker", "variance too high");

        assertThat(result.get("requestStatus")).isEqualTo("REJECTED");
        assertThat(result.get("rejectedBy")).isEqualTo("finance.checker");

        verify(auditService)
                .record(
                        eq("APPROVAL_REQUEST_REJECT"),
                        eq("APPROVAL_REQUEST_REJECT"),
                        contains("merchant:42"),
                        contains("reason=variance too high"),
                        argThat(
                                (AdminAuditService.AuditContext ctx) ->
                                        ctx != null && "variance too high".equals(ctx.reasonText())));
    }

    @Test
    void requireNoPendingForBlocksWhenARequestIsPending() {
        when(jdbcTemplate.queryForObject(
                        contains("FROM approval_requests"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.requireNoPendingFor("merchant", "42", "DAILY_CLOSE"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void requireNoPendingForAllowsWhenNoRequestIsPending() {
        when(jdbcTemplate.queryForObject(
                        contains("FROM approval_requests"), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);

        service.requireNoPendingFor("merchant", "42", "DAILY_CLOSE");

        // No exception - the action may proceed.
    }

    @Test
    void getRequiresReadPermissionAndReturnsTheRequest() {
        Map<String, Object> pending = pendingRow("ops.admin", null);
        stubFindById(pending);

        Map<String, Object> result = service.get(1L);

        verify(permissions).require(eq("APPROVAL_REQUEST_READ"), anyString(), anyString());
        assertThat(result.get("request_reference")).isEqualTo("ARQ-REF");
    }

    @Test
    void getRefusesWhenNotFound() {
        stubFindById(null);

        assertThatThrownBy(() -> service.get(1L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void listRequiresReadPermission() {
        doThrow(new AccessDeniedException("Admin role is required"))
                .when(permissions)
                .require(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> service.list("PENDING_APPROVAL", null, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expiredRequestIsRefusedAndMarkedExpired() {
        Map<String, Object> pending = pendingRow("ops.admin", null);
        pending.put("expires_at", Timestamp.from(Instant.now().minusSeconds(60)));
        stubFindById(pending);

        assertThatThrownBy(() -> service.approve(1L, "finance.checker", "ok"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("expired");

        verify(jdbcTemplate).update(contains("SET request_status = 'EXPIRED'"), any(MapSqlParameterSource.class));
    }

    private void stubFindById(Map<String, Object> row) {
        if (row == null) {
            when(jdbcTemplate.queryForList(
                            contains("FROM approval_requests WHERE id = :id"),
                            any(MapSqlParameterSource.class)))
                    .thenReturn(List.of());
            return;
        }
        when(jdbcTemplate.queryForList(
                        contains("FROM approval_requests WHERE id = :id"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        p != null && java.util.Objects.equals(1L, p.getValue("id")))))
                .thenReturn(List.of(row));
    }

    private Map<String, Object> pendingRow(String requestedBy, String status) {
        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("id", 1L);
        row.put("request_reference", "ARQ-REF");
        row.put("approval_type", "DAILY_CLOSE");
        row.put("request_status", status == null ? "PENDING_APPROVAL" : status);
        row.put("resource_type", "merchant");
        row.put("resource_id", "42");
        row.put("requested_by", requestedBy);
        row.put("previous_state_hash", "prev-hash");
        row.put("new_state_hash", "new-hash");
        row.put("request_id", "req-1");
        row.put("expires_at", null);
        return row;
    }
}
