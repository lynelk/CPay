package net.citotech.cito.Model;

import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
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
import java.nio.file.Files;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafariComPaymentGateway extends PaymentGateway {
    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    String global_url = "https://sandbox.safaricom.co.ke";
    String env = "mtnuganda";//sandbox
    String base_currency = "UGX";
    String segment = "collection";//disbursement";

    static public String BALANCE_TYPE = "safaricom_balance";

    String api_consumer_key = "kqxFcX8qkJHmQgEK";
    String api_consumer_secret = "P4GoccSlHkFb6tauA84L2s27Np5JRCb9";

    String callbackBaseUrl = "";

    String shortcode = "";
    String passKey = "";
    String initiatorUsername = "";
    String initiatorPassword = "";

    public String app_setting_app_ur = "";

    public static String[] prefix = {"25470","25471","25472", "25474", "25479", "25411"};

    public static String gateway_id = "SafariComPaymentGateway";

    public static String gateway_currency_code = "MPESAMM";

    @Value( "${custom.lockfiledirectory}" )
    private String lockfiledirectory;



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
            String timestamp = getTimeStamp();
            String password = Common.base64Encode(this.shortcode+this.passKey+timestamp);
            jdata.put("SecurityCredential", password);
            jdata.put("CommandID", "AccountBalance");
            jdata.put("Remarks", "Get Account Balance ");
            jdata.put("PartyA", this.shortcode);
            jdata.put("IdentifierType", "2");
            jdata.put("Initiator", "Cpay");
            jdata.put("QueueTimeOutURL", this.callbackBaseUrl+"safariCom");
            jdata.put("ResultURL", this.callbackBaseUrl+"safariCom");
            //callbackBaseUrl
            String data = jdata.toString();

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();

            HttpRequestResponse rs = Common.doHttpRequest("GET", url_string, data, headers);
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
        } catch (IOException ex) {
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


            HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, data, headers);
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
            ex.printStackTrace();
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        } catch (IOException | NoSuchAlgorithmException | NoSuchProviderException ex) {
            ex.printStackTrace();
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
            jdata.put("AccountReference", payer/*ref*/); //Changing to payer
            jdata.put("TransactionDesc", narrative);
            //callbackBaseUrl
            String data = jdata.toString();

            String url_string = this.global_url+"/mpesa/stkpush/v1/processrequest";

            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();


            HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, data, headers);
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
            ex.printStackTrace();
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace(ex.getMessage());
            return gwResponse;
        } catch (IOException ex) {
            ex.printStackTrace();
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

            HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, data, headers);
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
            cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            File file = ResourceUtils.getFile(new ClassPathResource("keystore/ProductionCertificate.cer").getPath());
            FileInputStream fin = new FileInputStream(file);
            CertificateFactory f = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate)f.generateCertificate(fin);
            PublicKey pk = certificate.getPublicKey();
            cipher.init(Cipher.ENCRYPT_MODE, pk, new SecureRandom());
            byte[] cipherText = cipher.doFinal(input);
            return Base64.getEncoder().encodeToString(cipherText);
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (CertificateException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public AccountInfo getAccountInfo(String msisdn) {
        return null;
    }

    private void checkStatusResponseStorage(String ConversationID, String txRef) {

        //Now save the new token
        try {
            JSONObject newTokenO = new JSONObject();
            newTokenO.put("txRef", txRef);
            newTokenO.put("ConversationID", ConversationID);
            String separator = File.separator;
            String filePath = /*lockfiledirectory+*/ConversationID+".json";

            File resource = new File(filePath);
            if (resource.createNewFile()) {
                Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE,
                        "SAFARICOM ConversationID File "+filePath+" has been created.");
            }
            if (!resource.exists()) {
                Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE,
                        "SAFARICOM ConversationID File "+filePath+" has not been created.");
                return;
            }
            Files.writeString(resource.toPath(), newTokenO.toString());
        } catch (IOException | JSONException ex) {
            Logger.getLogger(SafariComPaymentGateway.class.getName())
                    .log(Level.SEVERE, ex.getMessage(), ex);
            return;
        }
    }

    public SafariComPaymentGateway.Token getToken() throws IOException {
        String filePath = lockfiledirectory+Common.CLASS_PATH_SAFARICOM_TOKEN_FILE;
        File resource = new File(filePath);
        if (resource.createNewFile()) {
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE,
                    "MPESA Token File "+filePath+" has been created.");
        }

        //File resource = new ClassPathResource(Common.CLASS_PATH_MTN_TOKEN_FILE).getFile();

        String token = new String(
                Files.readAllBytes(resource.toPath())
        );

        token = token.trim();
        Logger.getLogger(SettingsController.class.getName())
                .log(Level.INFO, "Error "+token, " Is Empty " );
        JSONObject r = null;
        try {
            if (token.isEmpty()) {
                //No token, request for a new one
                return this.requestToken();
            }

            r = new JSONObject(token);
            if (r.isNull(this.segment)) {
                return this.requestToken();
            }

            JSONObject rO = r.getJSONObject(this.segment);
            LocalDateTime whenCreated = LocalDateTime.parse(rO.getString("created_on"),
                    Common.getDateTimeFormater());
            SafariComPaymentGateway.Token t = new SafariComPaymentGateway.Token(rO.getString("token"), whenCreated);

            //First check if the token is still valid
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastCreatedPlus = whenCreated.plusMinutes(1);//3599
            if (now.isAfter(lastCreatedPlus)) {
                return this.requestToken();
            }

            return t;
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, null, ex.getMessage() );
            return null;
        }
    }

    public SafariComPaymentGateway.Token requestToken() throws JSONException {
        Map<String, String> headers = new HashMap<>();
        //headers.put("Content-Type", "application/json");
        if (segment.equals("collection")) {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_consumer_key+":"+this.api_consumer_secret));
        } else {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_consumer_key+":"+this.api_consumer_secret));
        }

        String url_string = this.global_url+"/oauth/v1/generate?grant_type=client_credentials";

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

            JSONObject newToken = new JSONObject();
            newToken.put("token", accessToken);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            newToken.put("created_on", d.format(formatter));

            String filePath = lockfiledirectory+Common.CLASS_PATH_SAFARICOM_TOKEN_FILE;
            Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE,
                    "SAFARICOM Token "+filePath);

            //Now save the new token
            try {
                File resource = new File(filePath);
                if (resource.createNewFile()) {
                    Logger.getLogger(SafariComPaymentGateway.class.getName()).log(Level.SEVERE,
                            "SAFARICOM Token File "+filePath+" has been created.");
                }

                //File resource = new ClassPathResource(Common.CLASS_PATH_MTN_TOKEN_FILE).getFile();

                String token = new String(
                        Files.readAllBytes(resource.toPath())
                );

                if (token.isEmpty()) {
                    //new Token
                    JSONObject newTokenO = new JSONObject();
                    newTokenO.put(this.segment, newToken);
                    Files.writeString(resource.toPath(), newTokenO.toString());
                } else {
                    JSONObject newTokenO = new JSONObject(token);
                    newTokenO.put(this.segment, newToken);
                    Files.writeString(resource.toPath(), newTokenO.toString());
                }

            } catch (IOException ex) {
                Logger.getLogger(MTNMoMoPaymentGateway.class.getName())
                        .log(Level.SEVERE, ex.getMessage(), ex);
                return null;
            }
            return new SafariComPaymentGateway.Token(accessToken, d);
        }
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
