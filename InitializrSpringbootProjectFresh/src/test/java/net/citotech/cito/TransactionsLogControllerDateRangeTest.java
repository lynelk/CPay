package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.List;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.UserPrivilege;
import net.citotech.cito.transactions.TransactionQueryService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers audit F6 after the query extraction: malformed date ranges are rejected by
 * {@link TransactionQueryService} before JDBC is touched, while the controller retains the same
 * legacy response contract.
 */
class TransactionsLogControllerDateRangeTest {

    @Test
    void rejectsAMalformedDateRangeBeforeTouchingTheDatabase() throws Exception {
        TransactionsLogController controller = new TransactionsLogController();
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionQueryService queryService = new TransactionQueryService(jdbcTemplate);
        Field field = TransactionsLogController.class.getDeclaredField("transactionQueryService");
        field.setAccessible(true);
        field.set(controller, queryService);

        UserPrivilege privilege = new UserPrivilege();
        privilege.setPrivilege("ACCESS_TRANSACTION_LOG");
        MerchantUser user = new MerchantUser();
        user.setPrivileges(List.of(privilege));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession(true).setAttribute("merchantUser", user);

        JSONObject searchRules = new JSONObject();
        searchRules.put("start_date", "not-a-date");
        searchRules.put("end_date", "also-not-a-date");
        searchRules.put("status", "");
        searchRules.put("tx_type", "");
        JSONObject searchValue = new JSONObject();
        searchValue.put("category", JSONObject.NULL);
        searchValue.put("value", JSONObject.NULL);
        JSONObject body = new JSONObject();
        body.put("search_rules", searchRules);
        body.put("searchingValue", searchValue);
        body.put("pageSize", 50);
        body.put("currentPage", 0);

        String response =
                controller.getMerchantTransactions(
                        body.toString(), request, new MockHttpServletResponse());

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("101");
        assertThat(json.getString("message")).contains("YYYY-MM-DD");
    }
}
