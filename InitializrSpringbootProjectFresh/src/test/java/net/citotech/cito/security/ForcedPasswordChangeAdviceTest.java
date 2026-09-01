package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.User;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

class ForcedPasswordChangeAdviceTest {

    @Test
    void replacesAuthenticatedSessionWithRestrictedPasswordChangeSession() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(1));
        ForcedPasswordChangeAdvice advice = new ForcedPasswordChangeAdvice(jdbcTemplate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession authenticatedSession = (MockHttpSession) request.getSession(true);
        User user = new User();
        user.setId(7L);
        user.setEmail("admin@example.com");
        authenticatedSession.setAttribute("user", user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String body =
                advice.beforeBodyWrite(
                        "{\"code\":\"000\"}",
                        mock(MethodParameter.class),
                        MediaType.APPLICATION_JSON,
                        StringHttpMessageConverter.class,
                        new ServletServerHttpRequest(request),
                        new ServletServerHttpResponse(response));

        assertThat(new JSONObject(body).getString("code"))
                .isEqualTo(ForcedPasswordChangeAdvice.RESPONSE_CODE);
        assertThat(authenticatedSession.isInvalid()).isTrue();
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute("user")).isNull();
        assertThat(
                        request.getSession(false)
                                .getAttribute(
                                        InitialPasswordChangeController
                                                .PENDING_ADMIN_ID_SESSION_ATTRIBUTE))
                .isEqualTo(7L);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }
}
