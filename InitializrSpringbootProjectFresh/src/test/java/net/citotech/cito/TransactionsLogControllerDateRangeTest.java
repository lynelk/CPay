package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.UserPrivilege;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Covers audit F6: getMerchantTransactions' date-range search previously bound the raw request
 * string directly as the SQL parameter for a native datetime column comparison - a malformed value
 * would only ever surface as an opaque database error. It's now parsed into a java.sql.Timestamp
 * up front, so a bad value fails with a clear, specific error before ever reaching the database.
 */
class TransactionsLogControllerDateRangeTest {

    @Test
    void rejectsAMalformedDateRangeBeforeTouchingTheDatabase() throws Exception {
        TransactionsLogController controller = new TransactionsLogController();

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

        String response = controller.getMerchantTransactions(body.toString(), request, new MockHttpServletResponse());

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("101");
        assertThat(json.getString("message")).contains("YYYY-MM-DD");
    }
}
