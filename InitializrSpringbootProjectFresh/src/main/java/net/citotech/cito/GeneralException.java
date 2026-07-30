package net.citotech.cito;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Model.GateWayResponse;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.MDC;

/**
 * @author josephtabajjwa
 */
public class GeneralException {
    public static String ERRORS_100 = "Invalid JSON. Refer to the API.";
    public static String ERRORS_101 = "Missing JSON field %s.";
    public static String ERRORS_102 = "Internal application error.";
    public static String ERRORS_103 = "Authentication failed. Check your username and password.";
    public static String ERRORS_104 = "Account with email address (%s) does not exists.";
    public static String ERRORS_105 = "Email verification request timed out. Please try again.";
    public static String ERRORS_106 =
            "Email verification code (%s) does not match. Please try again.";
    public static String ERRORS_107 = "You are not logged in. Login first.";
    public static String ERRORS_108 = "%s (%s) already exists.";
    public static String ERRORS_109 = "%s (%s) does not exist.";
    public static String ERRORS_110 =
            "You do not have permission to access or perform this action.";
    public static String ERRORS_111 = "Insufficient funds %s on %s account.";
    public static String ERRORS_112 = "Stock account is not configured.";
    public static String ERRORS_113 = "This operation is supposed to be done on Stock account.";
    public static String ERRORS_114 = "Missing required JSON fields: %s.";
    public static String ERRORS_115 = "API authentication error. No keys generated for API access.";
    public static String ERRORS_116 =
            "API authentication error. Signature verification failed - refer to the documentation.";
    public static String ERRORS_117 = "Revenue account is not configured.";
    public static String ERRORS_118 = "Payment gateway does not support payer/payee (%s).";
    public static String ERRORS_119 =
            "Account not active. Please contact an administrator for assistance.";
    public static String ERRORS_120 = "Not allowed access to %s API.";
    public static String ERRORS_121 = "Transaction with reference %s was already submitted.";
    public static String ERRORS_122 =
            "Invalid base64 signature. Refer to API on how to compute the signature.";
    public static String ERRORS_123 = "Invalid amount (%s). Refer to API document.";
    public static String ERRORS_124 = "Invalid JSON data in the body of the request.";
    public static String ERRORS_125 = "API method path (%s) not found.";
    public static String ERRORS_126 = "HTTP request method (%s) supported this API call.";
    public static String ERRORS_127 = "Suspense account not configured.";
    public static String ERRORS_128 = "You can't edit a payment in %s state.";
    public static String ERRORS_129 = "Beneficiary %s appears more than once in this payment.";
    public static String ERRORS_130 =
            "This transaction is already in %s state, you can't change it.";
    public static String ERRORS_131 = "Failed to process uploaded file %s.";
    public static String ERRORS_132 = "Unsupported Excel file %s.";
    public static String ERRORS_133 = "SMS Revenue account not configured.";
    public static String ERRORS_134 = "You can't use this type of account for this transaction.";
    public static String ERRORS_135 = "The send time provided is already passed.";
    public static String ERRORS_136 = "Invalid SMS settings. Missing %s setting value.";
    public static String ERRORS_137 =
            "Account suspended: No further action required. Contact an administrator for assistance";
    public static String ERRORS_138 = "Invalid authorization key (%s). Contact the administrator.";
    public static String ERRORS_139 = "The IP Address (%s) is not allowed to access this API.";
    public static String ERRORS_140 = "The internal_app_access_auths setting is not set.";
    public static String ERRORS_141 = "The internal_app_access_ips setting is not set.";
    public static String ERRORS_142 = "Transaction not process as expected.";
    public static String ERRORS_143 = "Transaction not was not successful.";
    public static String ERRORS_144 = "Transaction %s (%s) was already updated.";
    public static String ERRORS_145 = "Rate limit exceeded. Please retry after 60 seconds.";
    public static String ERRORS_146 = "Amount %s is out of the allowed range [%s, %s].";
    public static String ERRORS_147 =
            "Please verify your email address before logging in. Check your inbox for the verification code.";
    public static String ERRORS_148 = "Risk authorization declined this request: %s";
    public static String ERRORS_149 =
            "This payout exceeds the step-up MFA threshold. Enable MFA on your account before starting it.";

    public static String getError(String code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("state", "ERROR");
            obj.put("code", code);
            obj.put("message", message);
            // Additive fields per the error catalog (audit D3): a stable machine-readable code,
            // category, retryable flag, docs URL, and the request's correlation ID. Existing
            // consumers that only read state/code/message are unaffected.
            addErrorCatalogFields(obj, code);
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
            addErrorCatalogFields(obj, code);

        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }

    private static void addErrorCatalogFields(JSONObject obj, String code) throws JSONException {
        ErrorCatalog.Entry entry = ErrorCatalog.lookup(code);
        obj.put("error_code", entry.stableCode());
        obj.put("category", entry.categoryName());
        obj.put("retryable", entry.retryable());
        obj.put("docs_url", entry.docsUrl());
        String requestId = MDC.get("request_id");
        if (requestId != null) {
            obj.put("request_id", requestId);
        }
    }
}
