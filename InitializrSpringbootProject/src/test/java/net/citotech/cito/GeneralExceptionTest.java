package net.citotech.cito;

import org.junit.jupiter.api.Test;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

class GeneralExceptionTest {

    // -------------------------------------------------------------------------
    // Error constants
    // -------------------------------------------------------------------------

    @Test
    void errorConstantsAreNotEmpty() {
        assertNotNull(GeneralException.ERRORS_100);
        assertFalse(GeneralException.ERRORS_100.isEmpty());
        assertNotNull(GeneralException.ERRORS_107);
        assertFalse(GeneralException.ERRORS_107.isEmpty());
    }

    @Test
    void formattableErrorContainsPlaceholder() {
        assertTrue(GeneralException.ERRORS_101.contains("%s"),
                "ERRORS_101 should contain a format placeholder");
        assertTrue(GeneralException.ERRORS_104.contains("%s"),
                "ERRORS_104 should contain a format placeholder");
    }

    // -------------------------------------------------------------------------
    // getError
    // -------------------------------------------------------------------------

    @Test
    void getErrorReturnsValidJson() throws Exception {
        String json = GeneralException.getError("100", "Something went wrong");
        JSONObject obj = new JSONObject(json);
        assertEquals("ERROR", obj.getString("state"));
        assertEquals("100", obj.getString("code"));
        assertEquals("Something went wrong", obj.getString("message"));
    }

    @Test
    void getErrorHandlesSpecialCharactersInMessage() throws Exception {
        String msg = "Account <admin> does not exist & was not found.";
        String json = GeneralException.getError("109", msg);
        JSONObject obj = new JSONObject(json);
        assertEquals(msg, obj.getString("message"));
    }

    @Test
    void getErrorWithEmptyCodeAndMessage() throws Exception {
        String json = GeneralException.getError("", "");
        JSONObject obj = new JSONObject(json);
        assertEquals("ERROR", obj.getString("state"));
        assertEquals("", obj.getString("code"));
        assertEquals("", obj.getString("message"));
    }

    // -------------------------------------------------------------------------
    // getSafaricomResponse
    // -------------------------------------------------------------------------

    @Test
    void getSafaricomResponseIncludesAllFields() throws Exception {
        String json = GeneralException.getSafaricomResponse("OK", "000", "Processed");
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertEquals("000", obj.getString("code"));
        assertEquals("Processed", obj.getString("message"));
    }

    @Test
    void getSafaricomResponseWithErrorState() throws Exception {
        String json = GeneralException.getSafaricomResponse("ERROR", "500", "Gateway timeout");
        JSONObject obj = new JSONObject(json);
        assertEquals("ERROR", obj.getString("state"));
    }

    // -------------------------------------------------------------------------
    // getApiTxMessage (error variant)
    // -------------------------------------------------------------------------

    @Test
    void getApiTxMessageContainsTxDetails() throws Exception {
        net.citotech.cito.Model.GateWayResponse gw = new net.citotech.cito.Model.GateWayResponse();
        gw.setStatus("FAILED");
        gw.setTransactionStatus("PENDING");
        gw.setNetworkId("NET_001");
        gw.setMessage("Network error");

        String json = GeneralException.getApiTxMessage("142", "Transaction not processed", gw);
        JSONObject obj = new JSONObject(json);
        assertEquals("ERROR", obj.getString("state"));
        assertEquals("142", obj.getString("code"));
        assertTrue(obj.has("txDetails"));

        JSONObject tx = obj.getJSONObject("txDetails");
        assertEquals("FAILED", tx.getString("status"));
        assertEquals("PENDING", tx.getString("transactionStatus"));
        assertEquals("NET_001", tx.getString("networkRef"));
        assertEquals("Network error", tx.getString("message"));
    }
}
