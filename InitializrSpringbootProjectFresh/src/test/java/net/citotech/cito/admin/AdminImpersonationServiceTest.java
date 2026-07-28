package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.AccessDeniedException;

/**
 * Covers audit O4: a time-boxed, permission-gated, fully-audited admin impersonation session.
 * The most safety-critical case is {@link #mutatingActionIsBlockedEvenThoughTheMerchantResolvesDuringImpersonation()} -
 * it proves that even once a merchant has been resolved via an active impersonation session
 * (as a read endpoint would), {@link AdminImpersonationService#requireNotImpersonating} still
 * refuses a mutating/money-moving action for that same admin.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AdminImpersonationServiceTest {

    @Test
    void startRejectsAnAdminWithoutTheImpersonationPermission() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        doThrow(new AccessDeniedException("Admin role is required"))
            .when(permissions).require(anyString(), anyString(), anyString());

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);

        assertThatThrownBy(() -> service.start(1L, 42L, "Investigating support ticket #123"))
            .isInstanceOf(AccessDeniedException.class);

        // No permission -> nothing should ever be written.
        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(auditService);
    }

    @Test
    void startRejectsABlankReason() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);

        assertThatThrownBy(() -> service.start(1L, 42L, "   "))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("reason");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void startRejectsANullReason() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);

        assertThatThrownBy(() -> service.start(1L, 42L, null))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("reason");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void startInsertsASessionRowAndRecordsADetailedAuditEntry() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(jdbcTemplate.update(contains("INSERT INTO admin_impersonation_sessions"),
                any(MapSqlParameterSource.class), any(KeyHolder.class), any(String[].class)))
            .thenAnswer(invocation -> {
                KeyHolder holder = invocation.getArgument(2);
                holder.getKeyList().add(Map.of("GENERATED_KEY", 501L));
                return 1;
            });

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);
        long sessionId = service.start(1L, 42L, "Investigating support ticket #123");

        assertThat(sessionId).isEqualTo(501L);
        verify(permissions).require(eq(AdminImpersonationService.PERMISSION_CODE), anyString(), contains("42"));
        verify(auditService).record(eq(AdminImpersonationService.PERMISSION_CODE), eq("IMPERSONATION_START"),
            contains("42"),
            argThat((String summary) -> summary.contains("admin_user_id=1")
                && summary.contains("merchant_id=42")
                && summary.contains("reason=Investigating support ticket #123")
                && summary.contains("expires_at=")));
    }

    @Test
    void endUpdatesTheSessionAndRecordsAuditWhenARowIsAffected() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(jdbcTemplate.update(contains("UPDATE admin_impersonation_sessions"), any(MapSqlParameterSource.class)))
            .thenReturn(1);

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);
        service.end(501L, 1L, null);

        verify(auditService).record(eq(AdminImpersonationService.PERMISSION_CODE), eq("IMPERSONATION_END"),
            contains("501"), contains("MANUAL_END"));
    }

    @Test
    void endIsANoOpWhenNoActiveSessionMatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        AdminPermissionService permissions = mock(AdminPermissionService.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(jdbcTemplate.update(contains("UPDATE admin_impersonation_sessions"), any(MapSqlParameterSource.class)))
            .thenReturn(0);

        AdminImpersonationService service = new AdminImpersonationService(jdbcTemplate, permissions, auditService);
        service.end(501L, 1L, null);

        verifyNoInteractions(auditService);
    }

    @Test
    void findActiveReturnsEmptyWhenNoSessionRowExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        assertThat(service.findActive(1L)).isEmpty();
    }

    @Test
    void findActiveReturnsTheSessionWhenNotYetExpired() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubActiveSession(jdbcTemplate, 1L, 42L, Instant.now().plusSeconds(600));

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        AdminImpersonationService.ImpersonationSession session = service.findActive(1L).orElseThrow();
        assertThat(session.merchantId()).isEqualTo(42L);
        assertThat(session.adminUserId()).isEqualTo(1L);
        verify(jdbcTemplate, never()).update(contains("EXPIRED"), any(MapSqlParameterSource.class));
    }

    @Test
    void findActiveTreatsAnExpiredSessionAsInactiveAndMarksItExpired() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubActiveSession(jdbcTemplate, 1L, 42L, Instant.now().minusSeconds(60));

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        assertThat(service.findActive(1L)).isEmpty();
        verify(jdbcTemplate).update(contains("EXPIRED"), any(MapSqlParameterSource.class));
    }

    @Test
    void resolveImpersonatedMerchantIdReturnsEmptyOnceTheSessionHasExpired() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubActiveSession(jdbcTemplate, 1L, 42L, Instant.now().minusSeconds(60));

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        assertThat(service.resolveImpersonatedMerchantId(1L)).isEmpty();
    }

    @Test
    void requireNotImpersonatingAllowsTheActionWhenThereIsNoActiveSession() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        assertThatCode(() -> service.requireNotImpersonating(1L, "PAYOUT_APPROVE")).doesNotThrowAnyException();
    }

    /**
     * The safety-critical case: an admin impersonating merchant 42 can still have that merchant
     * resolved for a read (exactly what a statement-export/balance/transaction-status endpoint
     * would do), but the same admin must still be refused when attempting a mutating, money-moving
     * action such as approving a payout - impersonation is read-mostly and can never stand in for
     * the merchant's own real session or v2 request signing.
     */
    @Test
    void mutatingActionIsBlockedEvenThoughTheMerchantResolvesDuringImpersonation() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubActiveSession(jdbcTemplate, 1L, 42L, Instant.now().plusSeconds(600));

        AdminImpersonationService service = new AdminImpersonationService(
            jdbcTemplate, mock(AdminPermissionService.class), mock(AdminAuditService.class));

        // A read endpoint resolves the impersonated merchant just fine...
        assertThat(service.resolveImpersonatedMerchantId(1L)).contains(42L);

        // ...but a mutating/money-moving action attempted by that same admin must still be blocked.
        assertThatThrownBy(() -> service.requireNotImpersonating(1L, "PAYOUT_APPROVE"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessageContaining("PAYOUT_APPROVE");
    }

    private void stubActiveSession(NamedParameterJdbcTemplate jdbcTemplate, long adminUserId, long merchantId,
            Instant expiresAt) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(501L);
            when(row.getLong("admin_user_id")).thenReturn(adminUserId);
            when(row.getLong("merchant_id")).thenReturn(merchantId);
            when(row.getString("reason")).thenReturn("Investigating support ticket #123");
            when(row.getTimestamp("started_at")).thenReturn(Timestamp.from(Instant.now().minusSeconds(60)));
            when(row.getTimestamp("expires_at")).thenReturn(Timestamp.from(expiresAt));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }

        when(jdbcTemplate.query(anyString(),
                argThat((MapSqlParameterSource p) -> p != null && java.util.Objects.equals(adminUserId, p.getValue("admin_user_id"))),
                any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }
}
