/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author josephtabajjwa
 */
public class GeneralException {
    public static String ERRORS_100 = "Invalid JSON. Refer to the API.";
    public static String ERRORS_101 = "Missing JSON field %s.";
    public static String ERRORS_102 = "Internal application error.";
    public static String ERRORS_103 = "Authentication failed. Check your username and password.";
    public static String ERRORS_104 = "Account with email address (%s) does not exists.";
    public static String ERRORS_105 = "Email verification %s code does not match. Pleasae try again.";
    public static String ERRORS_106 = "Email verification (with code %s) request timed out. Try again please.";
    public static String ERRORS_107 = "You are not logged in. Login first.";
    public static String ERRORS_108 = "%s (%s) already exists.";
    public static String ERRORS_109 = "%s (%s) does not exist.";
    public static String ERRORS_110 = "You do not have permission to access or perform this action.";
    public static String ERRORS_111 = "Insufficient funds %s on %s account.";
    public static String ERRORS_112 = "Stock account is not configured.";
    public static String ERRORS_113 = "This operation is supposed to be done on Stock account.";
    public static String ERRORS_114 = "Missing required JSON fields: %s.";
    public static String ERRORS_115 = "API authentication error. No keys generated for API access.";
    public static String ERRORS_116 = "API authentication error. Signature verification failed - refer to the documentation.";
    public static String ERRORS_117 = "Revenue account is not configured.";
    public static String ERRORS_118 = "Payment gateway does not support payer/payee (%s).";
    public static String ERRORS_119 = "Account not active. Please contact an administrator for assistance.";
    public static String ERRORS_120 = "Not allowed access to %s API.";
    public static String ERRORS_121 = "Transaction with reference %s was already submitted.";
    public static String ERRORS_122 = "Invalid base64 signature. Refer to API on how to compute the signature.";
    public static String ERRORS_123 = "Invalid amount (%s). Refer to API document.";
    public static String ERRORS_124 = "Invalid JSON data in the body of the request.";
    public static String ERRORS_125 = "API method path (%s) not found.";
    public static String ERRORS_126 = "HTTP request method (%s) supported this API call.";
    public static String ERRORS_127 = "Suspense account not configured.";
    public static String ERRORS_128 = "You can't edit a payment in %s state.";
    public static String ERRORS_129 = "Beneficiary %s appears more than once in this payment.";
    public static String ERRORS_130 = "This transaction is already in %s state, you can't change it.";
    public static String ERRORS_131 = "Failed to process uploaded file %s.";
    public static String ERRORS_132 = "Unsupported Excel file %s.";
    public static String ERRORS_133 = "SMS Revenue account not configured.";
    public static String ERRORS_134 = "You can't use this type of account for this transaction.";
    public static String ERRORS_135 = "The send time provided is already passed.";
    public static String ERRORS_136 = "Invalid SMS settings. Missing %s setting value.";
    public static String ERRORS_137 = "Account suspended: No further action required. Contact an administrator for assistance";
    public static String ERRORS_138 = "Invalid authorization key (%s). Contact the administrator.";
    public static String ERRORS_139 = "The IP Address (%s) is not allowed to access this API.";
    public static String ERRORS_140 = "The internal_app_access_auths setting is not set.";
    public static String ERRORS_141 = "The internal_app_access_ips setting is not set.";
    public static String ERRORS_142 = "Transaction not process as expected.";
    public static String ERRORS_143 = "Transaction not was not successful.";
    public static String ERRORS_144 = "Transaction %s (%s) was already updated.";
    public static String ERRORS_145 = "Rate limit exceeded. Please retry after 60 seconds.";
    public static String ERRORS_146 = "Amount %s is out of the allowed range [%s, %s].";
    
    
    public static String getError(String code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "ERROR");
            obj.put("code", code);
            obj.put("message", message);
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }

    public static String getSafaricomResponse(String status, String code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", status);
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
            obj.put("state", "ERROR");
            obj.put("code", code);
            obj.put("message", message);
            JSONObject txObject = new JSONObject();
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
}
