package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ExclusiveAdminProvisionerTest {

    @Test
    void preservesKnownPrivilegesAndRemovesEveryOtherAdmin() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.update(
                        startsWith("DELETE FROM admins"), any(MapSqlParameterSource.class)))
                .thenReturn(2);
        when(jdbcTemplate.queryForList(
                        startsWith("SELECT id, email"), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of("id", 10L, "email", "admin@example.com"),
                                Map.of("id", 11L, "email", "old-one@example.com"),
                                Map.of("id", 12L, "email", "old-two@example.com")));
        when(jdbcTemplate.queryForList(
                        startsWith("SELECT DISTINCT privilege"),
                        any(MapSqlParameterSource.class),
                        eq(String.class)))
                .thenReturn(List.of("CUSTOM_PLATFORM_PRIVILEGE"));
        when(jdbcTemplate.queryForObject(
                        eq("SELECT COUNT(*) FROM admins"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        startsWith("SELECT COUNT(*) FROM admin_privileges"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(15);
        ExclusiveAdminProvisioner provisioner = new ExclusiveAdminProvisioner(jdbcTemplate);

        ExclusiveAdminProvisioner.ProvisionResult result =
                provisioner.apply(
                        "operation-1",
                        " ADMIN@example.com ",
                        "Platform Administrator",
                        "$2b$12$" + "a".repeat(53));

        assertThat(result.alreadyProcessed()).isFalse();
        assertThat(result.targetAdminId()).isEqualTo(10L);
        assertThat(result.removedAdminCount()).isEqualTo(2);
        assertThat(result.grantedPrivilegeCount()).isEqualTo(15);
        assertThat(result.revokedAdminIds()).containsExactly(10L, 11L, 12L);
        verify(jdbcTemplate)
                .update(
                        startsWith("UPDATE admin_impersonation_sessions"),
                        any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(eq("DELETE FROM admin_mfa_totp"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate)
                .update(startsWith("DELETE FROM admins"), any(MapSqlParameterSource.class));
    }
}
