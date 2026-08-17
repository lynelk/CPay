package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * P0 §2: admin permission enforcement. Proves the service no longer trusts any ROLE_ADMIN blindly:
 * a principal is allowed only when one of their actual roles holds the permission in {@code
 * admin_permissions}, and denied - with an audit row - otherwise.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AdminPermissionServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private AdminAuditService auditService;
    private AdminPermissionService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        auditService = mock(AdminAuditService.class);
        service = new AdminPermissionService(jdbcTemplate, auditService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireAllowsWhenTheRoleHoldsThePermission() {
        authenticate("SUPER_ADMIN");
        stubPermission("SUPER_ADMIN", "DAILY_CLOSE", true);

        assertThatCode(() -> service.require("DAILY_CLOSE", "daily-close", "merchant:42"))
                .doesNotThrowAnyException();

        verify(auditService)
                .record(
                        eq("DAILY_CLOSE"),
                        eq("daily-close"),
                        eq("merchant:42"),
                        argThat(
                                (String summary) ->
                                        summary.startsWith("allowed;roles=SUPER_ADMIN")));
    }

    @Test
    void requireRefusesWhenNoHeldRoleHasThePermission() {
        authenticate("SUPPORT_AGENT");
        stubPermission("SUPPORT_AGENT", "DAILY_CLOSE", false);

        assertThatThrownBy(() -> service.require("DAILY_CLOSE", "daily-close", "merchant:42"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("DAILY_CLOSE");

        verify(auditService)
                .record(
                        eq("DAILY_CLOSE"),
                        eq("daily-close"),
                        eq("merchant:42"),
                        argThat(
                                (String summary) ->
                                        summary.contains("denied:permission-not-granted")));
    }

    @Test
    void requireRefusesWhenTheRoleDoesNotExistInThePermissionTable() {
        authenticate("OPERATIONS_ADMIN");
        stubPermission("OPERATIONS_ADMIN", "MERCHANT_PRODUCTION_ACTIVATION", false);

        assertThatThrownBy(
                        () ->
                                service.require(
                                        "MERCHANT_PRODUCTION_ACTIVATION", "activate", "merchant:9"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireRefusesWhenThePrincipalHoldsNoAdminRole() {
        // A non-role authority (e.g. an OAuth scope) means the principal holds no admin role at
        // all.
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "someone", "x", List.of(new SimpleGrantedAuthority("SCOPE_read"))));

        assertThatThrownBy(() -> service.require("DAILY_CLOSE", "daily-close", "merchant:42"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Admin role is required");

        verify(jdbcTemplate, never())
                .queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    @Test
    void requireRecordsAuditEvenWhenRefused() {
        authenticate("SUPPORT_AGENT");
        stubPermission("SUPPORT_AGENT", "DAILY_CLOSE", false);

        assertThatThrownBy(() -> service.require("DAILY_CLOSE", "daily-close", "merchant:42"))
                .isInstanceOf(AccessDeniedException.class);

        verify(auditService)
                .record(eq("DAILY_CLOSE"), eq("daily-close"), eq("merchant:42"), anyString());
    }

    @Test
    void hasPermissionLooksUpTheGrantTable() {
        stubPermission("FINANCE_CHECKER", "SETTLEMENT_APPROVAL", true);

        assertThat(service.hasPermission("FINANCE_CHECKER", "SETTLEMENT_APPROVAL")).isTrue();

        verify(jdbcTemplate)
                .queryForObject(
                        argThat((String sql) -> sql.contains("admin_permissions")),
                        any(MapSqlParameterSource.class),
                        eq(Long.class));
    }

    @Test
    void seedDefaultPermissionsIsIdempotent() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        service.seedDefaultPermissions();

        // The permission code is carried in the parameters, not the SQL literal.
        verify(jdbcTemplate)
                .update(
                        contains("INSERT IGNORE INTO admin_permissions"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        "BALANCE_BACKFILL".equals(p.getValue("permission_code"))
                                                && "ADMIN".equals(p.getValue("role_name"))));
    }

    private void authenticate(String roleName) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                roleName.toLowerCase(),
                                "x",
                                List.of(new SimpleGrantedAuthority("ROLE_" + roleName))));
    }

    private void stubPermission(String roleName, String permissionCode, boolean granted) {
        when(jdbcTemplate.queryForObject(
                        argThat(
                                (String sql) ->
                                        sql.contains("admin_permissions")
                                                && sql.contains("role_name = :role_name")),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        p != null
                                                && roleName.equals(p.getValue("role_name"))
                                                && permissionCode.equals(
                                                        p.getValue("permission_code"))),
                        eq(Long.class)))
                .thenReturn(granted ? 1L : 0L);
    }
}
