package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AdminRecoveryTokenIssuerTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    @Test
    void issuesHashedTokenOnlyForExistingActiveAdmin() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 7L,
                                        "email", "Admin@Example.com",
                                        "status", "ACTIVE")));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        AdminRecoveryTokenIssuer issuer = new AdminRecoveryTokenIssuer(jdbcTemplate, 15);

        Instant before = Instant.now();
        AdminRecoveryTokenIssuer.IssueResult result =
                issuer.issue(" ADMIN@example.com ", TOKEN_HASH.toUpperCase());

        assertThat(result).isEqualTo(AdminRecoveryTokenIssuer.IssueResult.ISSUED);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate)
                .update(startsWith("INSERT INTO password_reset_tokens"), parameters.capture());
        verify(jdbcTemplate).update(startsWith("UPDATE admins"), any(MapSqlParameterSource.class));
        assertThat(parameters.getValue().getValue("entity_type")).isEqualTo("ADMIN");
        assertThat(parameters.getValue().getValue("entity_id")).isEqualTo(7L);
        assertThat(parameters.getValue().getValue("email")).isEqualTo("admin@example.com");
        assertThat(parameters.getValue().getValue("token_hash")).isEqualTo(TOKEN_HASH);
        assertThat(parameters.getValue().getValue("request_ip")).isEqualTo("ops-admin-recovery");
        assertThat((Timestamp) parameters.getValue().getValue("expires_at"))
                .isAfter(Timestamp.from(before.plusSeconds(14 * 60L)));
    }

    @Test
    void doesNotCreateOrActivateMissingAdmin() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        AdminRecoveryTokenIssuer issuer = new AdminRecoveryTokenIssuer(jdbcTemplate, 15);

        AdminRecoveryTokenIssuer.IssueResult result = issuer.issue("admin@example.com", TOKEN_HASH);

        assertThat(result).isEqualTo(AdminRecoveryTokenIssuer.IssueResult.ACCOUNT_NOT_FOUND);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void rejectsInactiveAdminWithoutChangingStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 7L,
                                        "email", "admin@example.com",
                                        "status", "SUSPENDED")));
        AdminRecoveryTokenIssuer issuer = new AdminRecoveryTokenIssuer(jdbcTemplate, 15);

        AdminRecoveryTokenIssuer.IssueResult result = issuer.issue("admin@example.com", TOKEN_HASH);

        assertThat(result).isEqualTo(AdminRecoveryTokenIssuer.IssueResult.ACCOUNT_NOT_ACTIVE);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void treatsDuplicateDigestAsAlreadyProcessed() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 7L,
                                        "email", "admin@example.com",
                                        "status", "ACTIVE")));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));
        AdminRecoveryTokenIssuer issuer = new AdminRecoveryTokenIssuer(jdbcTemplate, 15);

        assertThat(issuer.issue("admin@example.com", TOKEN_HASH))
                .isEqualTo(AdminRecoveryTokenIssuer.IssueResult.ALREADY_PROCESSED);
    }
}
