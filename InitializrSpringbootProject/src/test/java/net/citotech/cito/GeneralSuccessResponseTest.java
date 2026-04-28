package net.citotech.cito;

import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import static org.junit.jupiter.api.Assertions.*;

class GeneralSuccessResponseTest {

    // -------------------------------------------------------------------------
    // String constants
    // -------------------------------------------------------------------------

    @Test
    void successConstantsAreNotEmpty() {
        assertNotNull(GeneralSuccessResponse.SUCCESS_000);
        assertFalse(GeneralSuccessResponse.SUCCESS_000.isEmpty());
        assertNotNull(GeneralSuccessResponse.SUCCESS_001);
        assertFalse(GeneralSuccessResponse.SUCCESS_001.isEmpty());
    }

    // -------------------------------------------------------------------------
    // getMessage
    // -------------------------------------------------------------------------

    @Test
    void getMessageReturnsValidJson() throws Exception {
        String json = GeneralSuccessResponse.getMessage("000", "Operation was successful.");
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertEquals("000", obj.getString("code"));
        assertEquals("Operation was successful.", obj.getString("message"));
    }

    @Test
    void getMessageHandlesSpecialCharacters() throws Exception {
        String msg = "User <admin> updated & saved.";
        String json = GeneralSuccessResponse.getMessage("000", msg);
        JSONObject obj = new JSONObject(json);
        assertEquals(msg, obj.getString("message"));
    }

    @Test
    void getMessageWithEmptyValues() throws Exception {
        String json = GeneralSuccessResponse.getMessage("", "");
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertEquals("", obj.getString("code"));
        assertEquals("", obj.getString("message"));
    }

    // -------------------------------------------------------------------------
    // getApiTxMessage (success variant)
    // -------------------------------------------------------------------------

    @Test
    void getApiTxMessageContainsTxDetails() throws Exception {
        GateWayResponse gw = new GateWayResponse();
        gw.setOurUniqueTxId("TX-001");
        gw.setStatus("SUCCESSFUL");
        gw.setTransactionStatus("COMPLETED");
        gw.setNetworkId("MTN-UG-001");
        gw.setMessage("Payment received");

        String json = GeneralSuccessResponse.getApiTxMessage("000", "Transaction processed", gw);
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertEquals("000", obj.getString("code"));
        assertTrue(obj.has("txDetails"));

        JSONObject tx = obj.getJSONObject("txDetails");
        assertEquals("TX-001", tx.getString("uniqueTransactionId"));
        assertEquals("SUCCESSFUL", tx.getString("status"));
        assertEquals("COMPLETED", tx.getString("transactionStatus"));
        assertEquals("MTN-UG-001", tx.getString("networkRef"));
        assertEquals("Payment received", tx.getString("message"));
    }

    @Test
    void getApiTxMessageWithEmptyGatewayResponse() throws Exception {
        GateWayResponse gw = new GateWayResponse();
        String json = GeneralSuccessResponse.getApiTxMessage("000", "OK", gw);
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertTrue(obj.has("txDetails"));
    }

    // -------------------------------------------------------------------------
    // getApiTxBalances
    // -------------------------------------------------------------------------

    @Test
    void getApiTxBalancesIncludesBalancesArray() throws Exception {
        JSONArray balances = new JSONArray();
        JSONObject b1 = new JSONObject();
        b1.put("currency", "UGX");
        b1.put("amount", 50000);
        balances.put(b1);

        String json = GeneralSuccessResponse.getApiTxBalances("000", "Balances retrieved", balances);
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertEquals("000", obj.getString("code"));
        assertTrue(obj.has("balances"));
        assertEquals(1, obj.getJSONArray("balances").length());
    }

    @Test
    void getApiTxBalancesWithEmptyArray() throws Exception {
        JSONArray empty = new JSONArray();
        String json = GeneralSuccessResponse.getApiTxBalances("000", "No balances", empty);
        JSONObject obj = new JSONObject(json);
        assertEquals(0, obj.getJSONArray("balances").length());
    }

    // -------------------------------------------------------------------------
    // getMessageOnInternalAppRes
    // -------------------------------------------------------------------------

    @Test
    void getMessageOnInternalAppResIncludesExtra() throws Exception {
        JSONObject extra = new JSONObject();
        extra.put("userId", 42);
        extra.put("action", "UPDATE");

        String json = GeneralSuccessResponse.getMessageOnInternalAppRes("000", "Done", extra);
        JSONObject obj = new JSONObject(json);
        assertEquals("OK", obj.getString("state"));
        assertTrue(obj.has("extra"));

        JSONObject returnedExtra = obj.getJSONObject("extra");
        assertEquals(42, returnedExtra.getInt("userId"));
        assertEquals("UPDATE", returnedExtra.getString("action"));
    }

    @Test
    void getMessageOnInternalAppResWithEmptyExtra() throws Exception {
        JSONObject empty = new JSONObject();
        String json = GeneralSuccessResponse.getMessageOnInternalAppRes("000", "Done", empty);
        JSONObject obj = new JSONObject(json);
        assertTrue(obj.has("extra"));
    }
}
