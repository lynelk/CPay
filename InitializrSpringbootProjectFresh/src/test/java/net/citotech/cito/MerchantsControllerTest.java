package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MerchantsControllerTest {

    @Test
    void getMerchantsAcceptsNumericPageSizeFromDataGrid() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<Merchant>>any()))
            .thenReturn(List.<Merchant>of());

        MerchantsController controller = new MerchantsController();
        controller.jdbcTemplate = jdbcTemplate;

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", adminUser());

        String response = controller.getMerchants(
            "{\"pageSize\":50,\"currentPage\":0,\"searchingValue\":{\"category\":\"all\",\"value\":\"all\"}}",
            request,
            new MockHttpServletResponse());

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(json.getJSONArray("data")).isEmpty();
        verify(jdbcTemplate).query(
            contains("LIMIT 50"),
            any(MapSqlParameterSource.class),
            org.mockito.ArgumentMatchers.<RowMapper<Merchant>>any());
    }

    private User adminUser() {
        UserPrivilege privilege = new UserPrivilege();
        privilege.setPrivilege("ACCESS_ADMIN");

        User user = new User();
        user.setPrivileges(List.of(privilege));
        return user;
    }
}