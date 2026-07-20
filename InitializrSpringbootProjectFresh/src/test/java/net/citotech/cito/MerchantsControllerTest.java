package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

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
        request.getSession().setAttribute("user", adminUser("ACCESS_ADMIN"));

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

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void editMerchantAcceptsNumericMerchantAndAdminIds() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(), (List) List.of(), (List) List.of());

        List<MapSqlParameterSource> updates = new ArrayList<>();
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(1));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(1));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class));
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>any())).thenReturn(1);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        MerchantsController controller = new MerchantsController();
        controller.jdbcTemplate = jdbcTemplate;
        ReflectionTestUtils.setField(controller, "transactionManager", transactionManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", adminUser("UPDATE_MERCHANT"));

        String response = controller.editMerchant("""
            {
              "id":44,
              "name":"Joseph",
              "short_name":"Joseph",
              "status":"ACTIVE",
              "account_type":"business",
              "allowed_apis":["MOBILE_MONEY_PAYIN","MOBILE_MONEY_PAYOUT"],
              "admins":[{
                "privileges":["ACCESS_ADMIN"],
                "phone":"0783086794",
                "name":"Joseph Tabajjwa",
                "id":76,
                "generate_pw":false,
                "delete":false,
                "email":"joseph.tabajjwa@gmail.com",
                "status":"ACTIVE"
              }],
              "generate_password":false,
              "generate_new_keys":false,
              "account_number":"1000000"
            }
            """, request, new MockHttpServletResponse());

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(updates.stream().filter(params -> params.hasValue("id") && params.getValue("id") instanceof String)
            .map(params -> (String) params.getValue("id")))
            .contains("44")
            .contains("76");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void addMerchantPersistsSubmittedAdminEmail() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(0), (List) List.of(0), (List) List.of(), (List) List.of());

        List<MapSqlParameterSource> updates = new ArrayList<>();
        int[] generatedId = {2000};
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(1));
            KeyHolder holder = invocation.getArgument(2);
            holder.getKeyList().add(Map.of("GENERATED_KEY", BigInteger.valueOf(generatedId[0]++)));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class));
        doAnswer(invocation -> {
            updates.add(invocation.getArgument(1));
            return 1;
        }).when(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
        when(jdbcTemplate.update(anyString(), org.mockito.ArgumentMatchers.<Map<String, ?>>any())).thenReturn(1);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        MerchantsController controller = new MerchantsController();
        controller.jdbcTemplate = jdbcTemplate;
        ReflectionTestUtils.setField(controller, "transactionManager", transactionManager);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute("user", adminUser("CREATE_MERCHANT"));

        String response = controller.addMerchant("""
            {
              "name":"Example Merchant",
              "short_name":"EXM",
              "status":"ACTIVE",
              "account_type":"business",
              "generate_new_keys":false,
              "allowed_apis":["MOBILE_MONEY_PAYIN"],
              "admins":[{
                "name":"Jane Doe",
                "email":"jane.doe@merchant.test",
                "phone":"256700000000",
                "status":"ACTIVE",
                "privileges":["ACCESS_ADMIN"]
              }]
            }
            """, request, new MockHttpServletResponse());

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(updates.stream()
            .filter(params -> params.hasValue("email"))
            .map(params -> (String) params.getValue("email"))
            .toList())
            .contains("jane.doe@merchant.test")
            .doesNotContain("Jane Doe");
        assertThat(updates.stream()
            .filter(params -> params.hasValue("merchant_id") && params.hasValue("email"))
            .map(params -> params.getValue("merchant_id"))
            .toList())
            .contains(BigInteger.valueOf(2000));
    }

    private User adminUser(String... privileges) {
        List<UserPrivilege> granted = java.util.Arrays.stream(privileges).map(privilegeName -> {
            UserPrivilege privilege = new UserPrivilege();
            privilege.setPrivilege(privilegeName);
            return privilege;
        }).toList();

        User user = new User();
        user.setName("Test Admin");
        user.setEmail("admin@example.test");
        user.setPrivileges(granted);
        return user;
    }
}
