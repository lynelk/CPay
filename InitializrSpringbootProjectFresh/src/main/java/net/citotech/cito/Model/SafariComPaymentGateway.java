package net.citotech.cito.Model;

import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.ResourceUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.citotech.cito.gateway.ProviderConversationReferenceStoreRegistry;
import net.citotech.cito.gateway.ProviderToken;
import net.citotech.cito.gateway.ProviderTokenStoreRegistry;

public class SafariComPaymentGateway extends PaymentGateway {
    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    String global_url = "https://sandbox.safaricom.co.ke";
    String env = "mtnuganda";//sandbox
    String base_currency = "UGX";
    String segment = "collection";//disbursement";

    static public String BALANCE_TYPE = "safaricom_balance";

    String api_consumer_key = "";
    String api_consumer_secret = "";

    String callbackBaseUrl = "";

    String shortcode = "";
    String passKey = "";
    String initiatorUsername = "";
    String initiatorPassword = "";

    public String app_setting_app_ur = "";

    /** Daraja API version: "2" (default) or "3" */
    public String api_version = "2";

    public static String[] prefix = {"25470","25471","25472", "25474", "25479", "25411"};

    public static String gateway_id = "SafariComPaymentGateway";

    public static String gateway_currency_code = "MPESAMM";



    public void setApiDetails(String global_url,
                                String api_consumer_key,
                                String api_consumer_secret,
                                String shortcode,
                                String passKey,
                                String env,
                                String app_setting_app_ur
                                ) {

        this.app_setting_app_ur = app_setting_app_ur;
        this.global_url = global_url;
        this.api_consumer_key = api_consumer_key;
        this.api_consumer_secret = api_consumer_secret;
        this.env = env;
        this.passKey = passKey;
        this.shortcode = shortcode;

    }

    public void setApiDetails(String global_url,
                              String api_consumer_key,
                              String api_consumer_secret,
                              String initiatorUsername,
                              String initiatorPassword,
                              String shortcode,
                              String env,
                              String app_setting_app_ur
    ) {

        this.app_setting_app_ur = app_setting_app_ur;
        this.global_url = global_url;
        this.api_consumer_key = api_consumer_key;
        this.api_consumer_secret = api_consumer_secret;
        this.env = env;
        this.shortcode = shortcode;
        this.initiatorUsername = initiatorUsername;
        this.initiatorPassword = initiatorPassword;

    }

    public void setApiVersion(String version) {
        if (version != null && !version.isEmpty()) this.api_version = version;
    }

    static public String getGatewayCurrencyCode() {
        return gateway_currency_code;
    }

    static public String getGatewayId() {
        return gateway_id;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    public static boolean isValidMisdn(String msisdn) {
        Boolean fromRoutingTable = net.citotech.cito.gateway.ChannelRoutingRegistry.matchesConfiguredPrefix(gateway_id, msisdn);
        if (fromRoutingTable != null) {
            return fromRoutingTable;
        }
        for (int i=0; i <  prefix.length; i++) {
            String line = msisdn;
            String pattern = "^"+prefix[i]+"";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(line);
            if (m.find( )) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Double getBalance() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Double getBalance(String account) {

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            String url_string = "";
            if (this.segment.equals("collection")) {
                url_string = this.global_url+"/mpesa/accountbalance/v1/query";
            } else {
                url_string = this.global_url+"/mpesa/accountbalance/v1/query";
            }

            SafariComPaymentGateway.Token token;
            token = this.getToken();
            if (token == null) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("Failed to obtain token for "+this.segment);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace("");
                return 0.0;
            }

            headers.put("Authorization", "Bearer "+token.getToken());

            JSONObject jdata = new JSONObject();
            jdata.put("CommandID", "AccountBalance");
            jdata.put("Remarks", "Get Account Balance");
            jdata.put("PartyA", this.shortcode);
            jdata.put("IdentifierType", "4");
            jdata.put("Initiator", this.initiatorUsername.isEmpty() ? "Cpay" : this.initiatorUsername);
            jdata.put("SecurityCredential", getEncyptedPassword(this.initiatorPassword));
            jdata.put("QueueTimeOutURL", this.app_setting_app_ur+"api/doSafaricomAccountBalanceCallback");
            jdata.put("ResultURL", this.app_setting_app_ur+"api/doSafaricomAccountBalanceCallback");
            String data = jdata.toString();

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();

            HttpRequestResponse rs = executeWithTokenRetry("POST", url_string, data, headers, token);
            if (rs == null) {
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Failed to obtain transaction status from the network.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return 0.0;
            }

            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");

                String res = "";
                String transaction_status = "";
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("code")) {
                        res += "Code: "+rJson.getString("code")+" ";
                        if (rJson.getString("code").equals("RESOURCE_NOT_FOUND")) {
                            transaction_status = "FAILED";
                        }
                    }
                    if (!rJson.isNull("message")) {
                        res += "Message: "+rJson.getString("message");
                    }
                } else {
                    res = "No response data from the server.";
                }

                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus(transaction_status);
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(rs.toString());
                return 0.0;
            } else {
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    String tx_stataus = "";
                    if (!rJson.isNull("status")) {
                        tx_stataus = rJson.getString("status");
                        if (tx_stataus.toUpperCase().equals("SUCCESSFUL")) {
                            gwResponse.setTransactionStatus("SUCCESSFUL");
                        } else if (tx_stataus.toUpperCase().equals("FAILED")) {
                            gwResponse.setTransactionStatus("FAILED");
                        } else {
                            gwResponse.setTransactionStatus("UNDETERMINED");
                        }
                    }
                    if (!rJson.isNull("financialTransactionId")) {
                        gwResponse.setNetworkId(rJson.getString("financialTransactionId"));
                    }
                }

                gwResponse.setRequestTrace(rs.toString());
                return 0.0;
            }
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, ex.getMessage(), "");
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return 0.0;
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, ex.getMessage(), "");
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return 0.0;
        }
    }

    @Override
    public GateWayResponse doPayOut(Double amount, String payee, String ref, String narrative) {

        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "disbursement";

            SafariComPaymentGateway.Token token;
            token = this.getToken();
            headers.put("Authorization", "Bearer "+token.getToken());
            JSONObject jdata = new JSONObject();
            jdata.put("InitiatorName", this.initiatorUsername);
            //String timestamp = getTimeStamp();
            String password = getEncyptedPassword(this.initiatorPassword);
            jdata.put("SecurityCredential", password);
            jdata.put("CommandID", "BusinessPayment");
            jdata.put("Amount", amount);
            jdata.put("PartyA", this.shortcode);
            jdata.put("PartyB", payee);
            jdata.put("ResultURL", app_setting_app_ur+"api/doSafaricomPayOutCallbackResults");
            jdata.put("QueueTimeOutURL", app_setting_app_ur+"api/doSafaricomPayOutCallbackResults");
            jdata.put("Occassion", ref);
            jdata.put("Remarks", narrative);
            //callbackBaseUrl
            String data = jdata.toString();

            String url_string = this.global_url+"/mpesa/b2c/v1/paymentrequest";

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();


            HttpRequestResponse rs = executeWithTokenRetry("POST", url_string, data, headers, token);
            if (rs == null) {

                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return gwResponse;
            }

            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");

                String res = "";
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("requestId")) {
                        res += "requestId: "+rJson.getString("requestId");
                    }
                    if (!rJson.isNull("errorCode")) {
                        res += "Code: "+rJson.getString("errorCode")+" ";
                    }
                    if (!rJson.isNull("errorMessage")) {
                        res += "Message: "+rJson.getString("errorMessage");
                    }
                }

                //gwResponse.setOurUniqueTxId();
                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                if (!rs.getResponse().isEmpty())  {
                    String res = "";
                    String ConversationID = "";
                    String ResponseDescription = "";
                    String OriginatorConversationID = "";
                    String ResponseCode = "";

                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("ConversationID")) {
                        ConversationID = rJson.getString("ConversationID");
                    }
                    if (!rJson.isNull("ResponseCode")) {
                        ResponseCode = rJson.getString("ResponseCode");
                    }
                    if (!rJson.isNull("OriginatorConversationID")) {
                        OriginatorConversationID = rJson.getString("OriginatorConversationID");
                    }
                    if (!rJson.isNull("ResponseDescription")) {
                        ResponseDescription = rJson.getString("ResponseDescription");
                    }

                    if (ResponseCode.equals("0")) {
                        gwResponse.setHttpStatus(rs.getStatusCode()+"");
                        gwResponse.setMessage("Request submitted to the network successfully.");
                        gwResponse.setStatus("OK");
                        gwResponse.setTransactionStatus("PENDING");
                        gwResponse.setNetworkId(ConversationID);
                        gwResponse.setSafaricomRequestReference(ConversationID);
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    } else {
                        gwResponse.setMessage(ResponseDescription);
                        gwResponse.setStatus("ERROR");
                        gwResponse.setTransactionStatus("FAILED");
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    }
                }

                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
        } catch (JSONException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        }
    }

    public GateWayResponse doReversal(String originalTransactionId, Double amount, String narrative)
            throws NoSuchAlgorithmException, NoSuchProviderException {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "disbursement";

            SafariComPaymentGateway.Token token = this.getToken();
            if (token == null) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("Failed to obtain token for reversal");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            }
            headers.put("Authorization", "Bearer "+token.getToken());

            JSONObject jdata = new JSONObject();
            jdata.put("Initiator", this.initiatorUsername);
            String encryptedPassword = getEncyptedPassword(this.initiatorPassword);
            jdata.put("SecurityCredential", encryptedPassword);
            jdata.put("CommandID", "TransactionReversal");
            jdata.put("TransactionID", originalTransactionId);
            jdata.put("Amount", amount);
            jdata.put("ReceiverParty", this.shortcode);
            jdata.put("ReceiverIdentifierType", "11");
            jdata.put("ResultURL", app_setting_app_ur+"api/doSafaricomReversalCallback");
            jdata.put("QueueTimeOutURL", app_setting_app_ur+"api/doSafaricomReversalCallback");
            jdata.put("Remarks", narrative);
            jdata.put("Occasion", narrative);

            String data = jdata.toString();
            String url_string = this.global_url+"/mpesa/reversal/v1/request";

            GateWayResponse gwResponse = new GateWayResponse();
            HttpRequestResponse rs = executeWithTokenRetry("POST", url_string, data, headers, token);
            if (rs == null) {
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return gwResponse;
            }

            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                String res = "";
                if (!rs.getResponse().isEmpty()) {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("requestId")) res += "requestId: "+rJson.getString("requestId");
                    if (!rJson.isNull("errorCode")) res += " Code: "+rJson.getString("errorCode");
                    if (!rJson.isNull("errorMessage")) res += " Message: "+rJson.getString("errorMessage");
                }
                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                if (!rs.getResponse().isEmpty()) {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    String ResponseCode = rJson.isNull("ResponseCode") ? "" : rJson.getString("ResponseCode");
                    String ResponseDescription = rJson.isNull("ResponseDescription") ? "" : rJson.getString("ResponseDescription");
                    String ConversationID = rJson.isNull("ConversationID") ? "" : rJson.getString("ConversationID");
                    if (ResponseCode.equals("0")) {
                        gwResponse.setHttpStatus(rs.getStatusCode()+"");
                        gwResponse.setMessage("Reversal request submitted successfully.");
                        gwResponse.setStatus("OK");
                        gwResponse.setTransactionStatus("PENDING");
                        gwResponse.setNetworkId(ConversationID);
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    } else {
                        gwResponse.setMessage(ResponseDescription);
                        gwResponse.setStatus("ERROR");
                        gwResponse.setTransactionStatus("FAILED");
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    }
                }
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Reversal request submitted.");
                gwResponse.setStatus("OK");
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
        } catch (JSONException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        } catch (IOException ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        }
    }

    private String getTimeStamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime dt = LocalDateTime.now();
        return dt.format(formatter);
    }

    @Override
    public GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "collection";

            SafariComPaymentGateway.Token token;
            token = this.getToken();
            headers.put("Authorization", "Bearer "+token.getToken());
            JSONObject jdata = new JSONObject();
            jdata.put("BusinessShortCode", this.shortcode);
            String timestamp = getTimeStamp();
            String password = Common.base64Encode(this.shortcode+this.passKey+timestamp);
            jdata.put("Password", password);
            jdata.put("Timestamp", timestamp);
            jdata.put("TransactionType", "CustomerPayBillOnline");
            jdata.put("Amount", amount);
            jdata.put("PartyA", payer);
            jdata.put("PartyB", this.shortcode);
            jdata.put("PhoneNumber", payer);
            jdata.put("CallBackURL", app_setting_app_ur+"api/doSafaricomPayInCallbackResults");
            //jdata.put("CallBackURL", app_setting_app_ur+"api/doSafaricomPayCallback");
            jdata.put("AccountReference", ref.length() > 12 ? ref.substring(0, 12) : ref);
            jdata.put("TransactionDesc", narrative);
            //callbackBaseUrl
            String data = jdata.toString();

            String url_string = this.global_url+"/mpesa/stkpush/v1/processrequest";

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();


            HttpRequestResponse rs = executeWithTokenRetry("POST", url_string, data, headers, token);
            if (rs == null) {

                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return gwResponse;
            }

            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");

                String res = "";
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("requestId")) {
                        res += "requestId: "+rJson.getString("requestId");
                    }
                    if (!rJson.isNull("errorCode")) {
                        res += "Code: "+rJson.getString("errorCode")+" ";
                    }
                    if (!rJson.isNull("errorMessage")) {
                        res += "Message: "+rJson.getString("errorMessage");
                    }
                }

                //gwResponse.setOurUniqueTxId();
                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                if (!rs.getResponse().isEmpty())  {
                    String res = "";
                    String ResponseCode = "";
                    String ResponseDescription = "";
                    String MerchantRequestID = "";
                    String CheckoutRequestID = "";

                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("CheckoutRequestID")) {
                        CheckoutRequestID = rJson.getString("CheckoutRequestID");
                    }
                    if (!rJson.isNull("ResponseCode")) {
                        ResponseCode = rJson.getString("ResponseCode");
                    }
                    if (!rJson.isNull("MerchantRequestID")) {
                        MerchantRequestID = rJson.getString("MerchantRequestID");
                    }
                    if (!rJson.isNull("ResponseDescription")) {
                        ResponseDescription = rJson.getString("ResponseDescription");
                    }

                    if (ResponseCode.equals("0")) {
                        gwResponse.setHttpStatus(rs.getStatusCode()+"");
                        gwResponse.setMessage("Request submitted to the network successfully.");
                        gwResponse.setStatus("OK");
                        gwResponse.setTransactionStatus("PENDING");
                        gwResponse.setNetworkId(CheckoutRequestID);
                        gwResponse.setSafaricomRequestReference(CheckoutRequestID);
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    } else {
                        gwResponse.setMessage(ResponseDescription);
                        gwResponse.setSafaricomRequestReference(CheckoutRequestID);
                        gwResponse.setStatus("ERROR");
                        gwResponse.setTransactionStatus("FAILED");
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    }
                }
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
        } catch (JSONException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        } catch (IOException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        }
    }

    @Override
    public GateWayResponse checkStatus(String ref) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            String url_string = "";
            if (this.segment.equals("collection")) {
                url_string = this.global_url+"/mpesa/stkpushquery/v1/query";
            } else {
                url_string = this.global_url+"/mpesa/transactionstatus/v1/query";
            }

            SafariComPaymentGateway.Token token;
            token = this.getToken();
            if (token == null) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("Failed to obtain token for "+this.segment);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            }

            headers.put("Authorization", "Bearer "+token.getToken());

            JSONObject jdata = new JSONObject();
            String timestamp = getTimeStamp();
            String data = "";
            if (this.segment.equals("collection")) {
                String password = Common.base64Encode(this.shortcode + this.passKey + timestamp);
                jdata.put("BusinessShortCode", this.shortcode);
                jdata.put("Password", password);
                jdata.put("CheckoutRequestID", ref);
                jdata.put("Timestamp", timestamp);
                //callbackBaseUrl
                data = jdata.toString();
            } else {
                jdata.put("Initiator", this.initiatorUsername);
                String password = getEncyptedPassword(this.initiatorPassword);
                jdata.put("SecurityCredential", password);
                jdata.put("CommandID", "TransactionStatusQuery");
                jdata.put("TransactionID", ref);
                jdata.put("PartyA", this.shortcode);
                jdata.put("IdentifierType", "4");
                jdata.put("ResultURL", app_setting_app_ur+"api/doSafaricomPayOutCallback");
                jdata.put("QueueTimeOutURL", app_setting_app_ur+"api/doSafaricomPayOutCallback");
                jdata.put("Remarks", "check status");
                jdata.put("Occasion", "Transaction check status");
                data = jdata.toString();
            }

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();

            HttpRequestResponse rs = executeWithTokenRetry("POST", url_string, data, headers, token);
            if (rs == null) {
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Failed to obtain transaction status from the network.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return gwResponse;
            }

            String res = "";
            String transaction_status = "";
            String ResultCode = "";
            String CheckoutRequestID = "";
            String MerchantRequestID = "";
            String ResponseDescription = "";
            String ResponseCode = "";
            String tx_stataus = "";
            String ConversationID = "";//Most for Payouts

            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");

                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());

                    if (!rJson.isNull("ResultCode")) {
                        res += "ResultCode: "+rJson.getString("ResultCode")+" ";
                        ResultCode = rJson.getString("ResultCode");
                    }
                    if (!rJson.isNull("ResponseCode")) {
                        res += "ResponseCode: "+rJson.getString("ResponseCode")+" ";
                        ResponseCode = rJson.getString("ResponseCode");
                    }
                    if (!rJson.isNull("ResultCode")) {
                        res += "ResponseDescription: "+rJson.getString("ResponseDescription")+" ";
                        ResponseDescription = rJson.getString("ResponseDescription");
                    }

                } else {
                    res = "No response data from the server.";
                }

                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus(transaction_status);
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request response returned successfully.");
                gwResponse.setStatus("OK");
                String message = "";
                if (!rs.getResponse().isEmpty())  {
                    Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), "");
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    //
                    if (!rJson.isNull("ConversationID")) {
                        res += "ConversationID: "+rJson.getString("ConversationID")+" ";
                        ConversationID = rJson.getString("ConversationID");
                        if (this.segment.equals("disbursement")) {
                            checkStatusResponseStorage(ConversationID, ref);
                        }
                    }
                    if (!rJson.isNull("ResultCode")) {
                        res += "ResultCode: "+rJson.getString("ResultCode")+" ";
                        ResultCode = rJson.getString("ResultCode");
                    }
                    if (!rJson.isNull("ResponseCode")) {
                        res += "ResponseCode: "+rJson.getString("ResponseCode")+" ";
                        ResponseCode = rJson.getString("ResponseCode");
                    }
                    if (!rJson.isNull("ResultCode")) {
                        res += "ResponseDescription: "+rJson.getString("ResponseDescription")+" ";
                        ResponseDescription = rJson.getString("ResponseDescription");
                    }
                    if (!rJson.isNull("CheckoutRequestID")) {
                        res += "CheckoutRequestID: "+rJson.getString("CheckoutRequestID")+" ";
                        CheckoutRequestID = rJson.getString("CheckoutRequestID");
                        gwResponse.setNetworkId(CheckoutRequestID);
                    }
                    if (!rJson.isNull("ResultDesc")) {
                        message = rJson.getString("ResultDesc");
                    }
                    //CheckoutRequestID
                    res += ResponseDescription;

                    if (ResultCode.equals("0") && ResponseCode.equals("0")) {
                        gwResponse.setTransactionStatus("SUCCESSFUL");
                    } if ( ResponseCode.equals("0") && !ResultCode.equals("0")) {
                        gwResponse.setTransactionStatus("FAILED");
                        gwResponse.setMessage(message);
                    } else {
                        gwResponse.setTransactionStatus("UNDETERMINED");
                    }
                    gwResponse.setMessage(res);

                }

                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, ex.getMessage(), "");
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return gwResponse;
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, ex.getMessage(), "");
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return gwResponse;
        }
    }

    String getEncyptedPassword(String password) throws NoSuchAlgorithmException, NoSuchProviderException {
        byte[] input = password.getBytes();

        Cipher cipher = null;
        try {
            cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            File file = ResourceUtils.getFile(new ClassPathResource("keystore/ProductionCertificate.cer").getPath());
            FileInputStream fin = new FileInputStream(file);
            CertificateFactory f = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate)f.generateCertificate(fin);
            PublicKey pk = certificate.getPublicKey();
            cipher.init(Cipher.ENCRYPT_MODE, pk, new SecureRandom());
            byte[] cipherText = cipher.doFinal(input);
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (NoSuchPaddingException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        } catch (FileNotFoundException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        } catch (IllegalBlockSizeException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        } catch (CertificateException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        } catch (BadPaddingException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        } catch (InvalidKeyException e) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
        return "";
    }

    @Override
    public AccountInfo getAccountInfo(String msisdn) {
        return null;
    }

    private void checkStatusResponseStorage(String ConversationID, String txRef) {
        // C1: this used to write a plaintext <ConversationID>.json file to local disk (via
        // Common.safeLockFile) that Api.getPayoutConversationIdToken read back to resolve a
        // TransactionStatusQuery callback's ConversationID to our own transaction reference.
        // That file is genuinely read (Api.java's live /doSafaricomPayOutCallback handler
        // depends on it) so it isn't dead weight to delete outright - but a per-instance local
        // file can't be the source of truth once there is more than one app instance/pod, so
        // the mapping now lives in the DB-backed provider_conversation_references table (see
        // ProviderConversationReferenceStoreService), the same pattern already used for
        // provider tokens in getToken() below.
        ProviderConversationReferenceStoreRegistry.save(gateway_id, ConversationID, txRef);
    }

    public SafariComPaymentGateway.Token getToken() throws IOException {
        // Tokens live only in the encrypted provider_tokens DB store (see ProviderTokenStoreService) -
        // no plaintext on-disk cache.
        Optional<ProviderToken> databaseToken = ProviderTokenStoreRegistry.findValid(gateway_id, this.segment, tokenEnvironment());
        if (databaseToken.isPresent()) {
            return new SafariComPaymentGateway.Token(databaseToken.get().getTokenValue(), LocalDateTime.now());
        }
        return this.requestToken();
    }

    public SafariComPaymentGateway.Token requestToken() throws JSONException {
        Map<String, String> headers = new HashMap<>();
        //headers.put("Content-Type", "application/json");
        if (segment.equals("collection")) {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_consumer_key+":"+this.api_consumer_secret));
        } else {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_consumer_key+":"+this.api_consumer_secret));
        }

        String oauthVersion = "3".equals(this.api_version) ? "v2" : "v1";
        String url_string = this.global_url+"/oauth/"+oauthVersion+"/generate?grant_type=client_credentials";

        HttpRequestResponse rs = Common.doHttpRequest("GET", url_string, "", headers);
        if (rs == null) {
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "Failed to get token. ", "");
            return null;
        }

        if (rs.getStatusCode() != 200) {
            String error = rs.toString();
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
            return null;
        } else {
            JSONObject jsToken = new JSONObject(rs.getResponse());
            String accessToken = jsToken.getString("access_token");
            String expires_in = jsToken.getString("expires_in");
            LocalDateTime d = LocalDateTime.now();
            long expiresSeconds = parseExpirySeconds(expires_in);
            ProviderTokenStoreRegistry.save(
                    gateway_id,
                    this.segment,
                    tokenEnvironment(),
                    accessToken,
                    Instant.now().plus(expiresSeconds, ChronoUnit.SECONDS));
            return new SafariComPaymentGateway.Token(accessToken, d);
        }
    }

    // Audit C2: single-flight token-refresh lock table. Instances of this gateway are constructed
    // per merchant channel config rather than managed as Spring singletons (mirroring how
    // ProviderTokenStoreRegistry itself is only ever reached through static methods, never an
    // injected instance) - so concurrent 401s for the same gateway/segment/environment can easily
    // land on separate instances, and a plain instance field would not coordinate them. The lock
    // table is static and keyed by gateway id + segment + environment, a small, fixed set of
    // combinations, so it cannot grow unbounded.
    private static final ConcurrentHashMap<String, ReentrantLock> TOKEN_REFRESH_LOCKS = new ConcurrentHashMap<>();

    private static ReentrantLock tokenRefreshLock(String gatewayId, String segment, String environment) {
        return TOKEN_REFRESH_LOCKS.computeIfAbsent(gatewayId + "|" + segment + "|" + environment,
                key -> new ReentrantLock());
    }

    /**
     * Audit C2: executes the request and, if the provider responds with 401 even though our own
     * TTL-based getToken() considered the token still valid (revoked early, clock skew, or a
     * provider-side session invalidation), forces a fresh token via {@link #forceRefreshToken}
     * and retries exactly once with the refreshed Authorization header - rather than failing a
     * transaction we could still complete.
     */
    private HttpRequestResponse executeWithTokenRetry(String method, String url, String data,
            Map<String, String> headers, SafariComPaymentGateway.Token token) throws JSONException {
        HttpRequestResponse response = Common.doHttpRequest(method, url, data, headers);
        if (response != null && response.getStatusCode() == 401 && token != null) {
            SafariComPaymentGateway.Token refreshed = forceRefreshToken(token.getToken());
            if (refreshed != null) {
                headers.put("Authorization", "Bearer " + refreshed.getToken());
                response = Common.doHttpRequest(method, url, data, headers);
            }
        }
        return response;
    }

    /**
     * Audit C2: forces a fresh token for this gateway/segment/environment, single-flighted so only
     * one concurrent caller actually calls the provider's token endpoint. A caller that arrives
     * while another thread's refresh is already in flight waits for the lock, then re-checks the
     * DB-backed token store - since requestToken() always saves its result there - and reuses it if
     * it differs from the token that just failed, instead of requesting a second fresh token itself.
     */
    private SafariComPaymentGateway.Token forceRefreshToken(String failedTokenValue) throws JSONException {
        ReentrantLock lock = tokenRefreshLock(gateway_id, this.segment, tokenEnvironment());
        lock.lock();
        try {
            Optional<ProviderToken> current =
                    ProviderTokenStoreRegistry.findValid(gateway_id, this.segment, tokenEnvironment());
            if (current.isPresent() && !current.get().getTokenValue().equals(failedTokenValue)) {
                return new SafariComPaymentGateway.Token(current.get().getTokenValue(), LocalDateTime.now());
            }
            return requestToken();
        } finally {
            lock.unlock();
        }
    }

    private long parseExpirySeconds(String expiresIn) {
        try {
            return Long.parseLong(expiresIn);
        } catch (Exception ignored) {
            return 3300;
        }
    }

    private String tokenEnvironment() {
        if (this.mode != null && this.mode.toUpperCase().contains("PROD")) {
            return "PRODUCTION";
        }
        if (this.global_url != null && !this.global_url.toLowerCase().contains("sandbox")) {
            return "PRODUCTION";
        }
        return "SANDBOX";
    }

    public class Token {
        String token;
        LocalDateTime created_on;

        public Token(String token, LocalDateTime created_on) {
            this.token = token;
            this.created_on = created_on;
        }

        public String getToken() {
            return this.token;
        }

        public String toString() {
            return "Token: "+this.token+"\nCreated On: "+this.created_on.format(Common.getDateTimeFormater());
        }

    }
}

