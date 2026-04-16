/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Model.GateWayResponse;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

/**
 *
 * @author josephtabajjwa
 */
public class GeneralSuccessResponse {
    public static String SUCCESS_000 = "Operation was successful.";
    public static String SUCCESS_001 = "Password reset was successful. Now go ahead and login with your new password.";
    
    public static String getMessage(String code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "OK");
            obj.put("code", code);
            obj.put("message", message);
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }
    
    
    public static String getApiTxMessage(String code, String message, GateWayResponse gwResponse) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "OK");
            obj.put("code", code);
            obj.put("message", message);
            JSONObject txObject = new JSONObject();
            txObject.put("uniqueTransactionId", gwResponse.getOurUniqueTxId());
            txObject.put("status", gwResponse.getStatus());
            txObject.put("transactionStatus", gwResponse.getTransactionStatus());
            txObject.put("networkRef", gwResponse.getNetworkId());
            txObject.put("message", gwResponse.getMessage());
            obj.put("txDetails", txObject);
            
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }
    
    public static String getApiTxBalances(String code, String message, JSONArray balances) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "OK");
            obj.put("code", code);
            obj.put("message", message);
            obj.put("balances", balances);
            
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }
    
    public static String getMessageOnInternalAppRes(String code, String message, JSONObject extra) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "OK");
            obj.put("code", code);
            obj.put("message", message);
            obj.put("extra", extra);
            
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }
}
