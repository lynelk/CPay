package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

class InitialPasswordChangeControllerTest {

    @Test
    void replacesTemporaryPasswordAndRevokesSessions() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        SessionRevocationService sessions = mock(SessionRevocationService.class);
        when(jdbcTemplate.queryForList(
                        startsWith("SELECT id, status, password"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id",
                                        7L,
                                        "status",
                                        "ACTIVE",
                                        "password",
                                        PasswordUtils.hashPassword("old-test-password"),
                                        "must_change_password",
                                        1)));
        when(jdbcTemplate.update(
                        startsWith("UPDATE admins SET password"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        InitialPasswordChangeController controller =
                new InitialPasswordChangeController(jdbcTemplate, sessions);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = (MockHttpSession) request.getSession(true);
        session.setAttribute(
                InitialPasswordChangeController.PENDING_ADMIN_ID_SESSION_ATTRIBUTE, 7L);

        String result =
                controller.completeInitialPasswordChange(
                        Map.of(
                                "new_password",
                                "New-Secure-Password-2",
                                "confirm_password",
                                "New-Secure-Password-2"),
                        request);

        assertThat(new JSONObject(result).getString("code")).isEqualTo("000");
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate)
                .update(startsWith("UPDATE admins SET password"), parameters.capture());
        assertThat(
                        PasswordUtils.verifyPassword(
                                "New-Secure-Password-2",
                                String.valueOf(parameters.getValue().getValue("password"))))
                .isTrue();
        verify(sessions).revokeAllForAdmin(7L);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void requiresTheRestrictedSession() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        InitialPasswordChangeController controller =
                new InitialPasswordChangeController(
                        jdbcTemplate, mock(SessionRevocationService.class));

        String result =
                controller.completeInitialPasswordChange(
                        Map.of(
                                "new_password",
                                "New-Secure-Password-2",
                                "confirm_password",
                                "New-Secure-Password-2"),
                        new MockHttpServletRequest());

        assertThat(new JSONObject(result).getString("code"))
                .isEqualTo("PASSWORD_CHANGE_SESSION_REQUIRED");
    }
}
