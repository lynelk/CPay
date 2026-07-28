package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import net.citotech.cito.security.LoginRateLimiter;
import net.citotech.cito.security.PasswordUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers audit P4: a merchant user whose email address hasn't been confirmed must be blocked at
 * login, even with a correct password and an ACTIVE account status - and a verified user must
 * still be able to log in exactly as before.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class AuthenticationControllerMerchantEmailVerificationTest {

    @Test
    void blocksLoginForAMerchantUserWithAnUnverifiedEmail() throws Exception {
        String response = authenticateAs(null);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("147");
    }

    @Test
    void allowsLoginForAMerchantUserWithAVerifiedEmail() throws Exception {
        String response = authenticateAs("2026-07-01 10:00:00");

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("000");
    }

    private String authenticateAs(String emailVerifiedAt) throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String hashedPassword = PasswordUtils.hashPassword("password123");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                RowMapper mapper = invocation.getArgument(2);
                if (sql.contains("merchant_admin_privileges")) {
                    return List.of();
                }
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("account_type")).thenReturn("business");
                when(rs.getLong("merchant_id")).thenReturn(7L);
                when(rs.getString("merchant_name")).thenReturn("Test Merchant");
                when(rs.getString("merchant_status")).thenReturn("ACTIVE");
                when(rs.getString("account_number")).thenReturn("1000003");
                when(rs.getString("name")).thenReturn("Jane");
                when(rs.getString("email")).thenReturn("jane@example.com");
                when(rs.getString("phone")).thenReturn("256700000000");
                when(rs.getString("password")).thenReturn(hashedPassword);
                when(rs.getLong("id")).thenReturn(99L);
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getString("created_on")).thenReturn("");
                when(rs.getString("updated_on")).thenReturn("");
                when(rs.getString("is_verification_timedout")).thenReturn("FALSE");
                when(rs.getString("email_verification_code")).thenReturn("");
                when(rs.getString("role")).thenReturn("OWNER");
                when(rs.getString("email_verified_at")).thenReturn(emailVerifiedAt);
                return List.of(mapper.mapRow(rs, 1));
            });

        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        when(rateLimiter.tryConsume(anyString(), anyString())).thenReturn(true);

        AuthenticationController controller = new AuthenticationController();
        controller.jdbcTemplate = jdbcTemplate;
        controller.rateLimiter = rateLimiter;

        Map<String, String> body = Map.of(
            "username", "jane@example.com",
            "password", "password123",
            "account_number", "1000003");

        return controller.authenticateMerchantUser(body, new MockHttpServletRequest(), new MockHttpServletResponse());
    }
}
