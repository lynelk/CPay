package net.citotech.cito.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import net.citotech.cito.AuthenticationController;
import net.citotech.cito.Model.User;
import org.json.JSONObject;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = AuthenticationController.class)
public class ForcedPasswordChangeAdvice implements ResponseBodyAdvice<String> {

    public static final String RESPONSE_CODE = "PASSWORD_CHANGE_REQUIRED";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ForcedPasswordChangeAdvice(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getMethod() != null
                && "authenticatedUser".equals(returnType.getMethod().getName());
    }

    @Override
    public String beforeBodyWrite(
            String body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return body;
        }
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        HttpSession session = httpRequest.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User user)) {
            return body;
        }
        if (user.getId() == null || !requiresPasswordChange(user.getId())) {
            return body;
        }

        long adminId = user.getId();
        session.invalidate();
        HttpSession restrictedSession = httpRequest.getSession(true);
        restrictedSession.setAttribute(
                InitialPasswordChangeController.PENDING_ADMIN_ID_SESSION_ATTRIBUTE, adminId);
        response.getHeaders().setCacheControl("no-store");

        return new JSONObject()
                .put("code", RESPONSE_CODE)
                .put(
                        "message",
                        "You must choose a new password before accessing the platform.")
                .toString();
    }

    private boolean requiresPasswordChange(long adminId) {
        List<Integer> values =
                jdbcTemplate.query(
                        "SELECT must_change_password FROM admins WHERE id=:id",
                        new MapSqlParameterSource("id", adminId),
                        (resultSet, rowNumber) -> resultSet.getInt("must_change_password"));
        return values.size() == 1 && values.get(0) == 1;
    }
}
