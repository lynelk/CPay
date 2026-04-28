/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

/**
 *
 * @author josephtabajjwa
 */
public class AirtelMoneyOpenApiPaymentGateway extends PaymentGateway{
    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    //String global_url = "https://openapiuat.airtel.africa/";
    String global_url = "https://openapiuat.airtel.africa";
    String env = "mtnuganda";//sandbox
    String base_currency = "UGX";
    String country = "UG";
    String segment = "collection";//disbursement";
    
    String api_pin = "";
    
    static public String BALANCE_TYPE = "airtelmm_balance";//airteloapimm_balance";
    
    String api_collections_user = "";
    String api_collections_key = "";
    String api_collections_subscription = "";
    
    String api_disbursements_user = "";
    String api_disbursements_key = "";
    String api_disbursements_subscription = "";
    
    /*String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCkq3XbDI1s8Lu7SpUBP+bqOs/MC6PKWz\n" +
        "6n/0UkqTiOZqKqaoZClI3BUDTrSIJsrN1Qx7ivBzsaAYfsB0CygSSWay4iyUcnMVEDrNVO\n" +
        "JwtWvHxpyWJC5RfKBrweW9b8klFa/CfKRtkK730apy0Kxjg+7fF0tB4O3Ic9Gxuv4pFkbQ\n" +
        "IDAQAB"; // public key provided to encrypt data
    */
    String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCkq3XbDI1s8Lu7SpUBP+bqOs/MC6PKWz6n/0UkqTiOZqKqaoZClI3BUDTrSIJsrN1Qx7ivBzsaAYfsB0CygSSWay4iyUcnMVEDrNVOJwtWvHxpyWJC5RfKBrweW9b8klFa/CfKRtkK730apy0Kxjg+7fF0tB4O3Ic9Gxuv4pFkbQIDAQAB";
    
    public static String[] prefix = {"25675", "25670", "25676"};
    
    public static String gateway_id = "AirtelMoneyOpenApiPaymentGateway";
    
    public static String gateway_currency_code = "AIRTELMM";//AIRTELOAPIMM";
    
    @Value( "${custom.lockfiledirectory}" )
    private String lockfiledirectory;
    
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
    
    public void setApiDetails(String global_url, String api_username, 
            String api_password, String api_pin) {
        
        this.global_url = global_url;
        this.api_username = api_username;
        this.api_password = api_password;
        this.api_pin = api_pin;
        
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

    
    @Override
    public Double getBalance() {
        
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    /*
    * @Param account: Set this to disbursement | collections
    */
    @Override
    public Double getBalance(String account) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            String url_string = "";
            if (account.equals("collection")) {
                //headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
                url_string = this.global_url+"/standard/v1/users/balance";
            } else {
                //headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
                url_string = this.global_url+"/standard/v1/users/balance";
            }
            
            Token token;
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
            headers.put("X-Country", this.country);
            headers.put("X-Currency", this.base_currency);
           
            String data = "";
            
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
                //gwResponse.setTransactionStatus("PENDING");
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    
                    
                    String balance_string = "";
                    if (!rJson.isNull("data")) {
                        JSONObject balObject = rJson.getJSONObject("data");
                        if (!balObject.isNull("balance")) {
                            balance_string = balObject.getString("balance").replace(",", "");
                            double bal = Double.parseDouble(balance_string);
                            return bal;
                        }
                    }
                }
                
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
        payee = payee.substring(3);
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "disbursement";
            
            Token token;
            token = this.getToken();
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Country", this.country);
            headers.put("X-Currency", this.base_currency);
            
            JSONObject jdata = new JSONObject();
            
            
            String encryptPin = "";
            
            try {
                encryptPin = RSAUtil.encrypt(this.api_pin, this.publicKey);
                
            } catch (BadPaddingException ex) {
                
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage(ex.getMessage());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            } catch (IllegalBlockSizeException ex) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage(ex.getMessage());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            } catch (InvalidKeyException ex) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage(ex.getMessage());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            } catch (NoSuchPaddingException ex) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage(ex.getMessage());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            } catch (NoSuchAlgorithmException ex) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage(ex.getMessage());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            }
            
            jdata.put("pin", encryptPin);
            
            JSONObject jdataTransaction = new JSONObject();
            jdataTransaction.put("amount", amount);
            jdataTransaction.put("id", ref);
            jdata.put("transaction", jdataTransaction);
            
            JSONObject jdataPayee = new JSONObject();
            jdataPayee.put("msisdn", payee);
            jdata.put("payee", jdataPayee);
            jdata.put("reference", narrative);
            
            String data = jdata.toString();
            
            
            String url_string = this.global_url+"/standard/v1/disbursements/";
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, data, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus(null);
                gwResponse.setMessage("");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
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
                    if (!rJson.isNull("code")) {
                        res += "Code: "+rJson.getString("code")+" ";
                    }
                    if (!rJson.isNull("statusCode")) {
                        res += "Status Code: "+rJson.getString("statusCode")+" ";
                    }
                    if (!rJson.isNull("message")) {
                        res += "Message: "+rJson.getString("message");
                    }
                }
                
                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                if (!rs.getResponse().isEmpty())  {
                   
                    JSONObject rJson = new JSONObject(rs.getResponse());
                   
                    if (!rJson.isNull("status")) {
                        JSONObject statusObject = rJson.getJSONObject("status");
                        String status = statusObject.isNull("status") ? "" : statusObject.getString("status");
                        String code = statusObject.isNull("code") ? "" : statusObject.getString("code");
                        String result_code = statusObject.isNull("result_code") ? "" : statusObject.getString("result_code");
                        String message = statusObject.isNull("message") ? "" : statusObject.getString("message");
                        if (!rJson.isNull("data") && !rJson.getJSONObject("data").isNull("transaction")) {
                            JSONObject transaction =  rJson.getJSONObject("data")
                                    .getJSONObject("transaction");
                            String network_ref = "";
                            if (!transaction.isNull("reference_id")) {
                                network_ref = transaction.getString("reference_id");
                            }
                            if (result_code.toUpperCase().equals("ESB000010")
                                    && code.equals("200") && !network_ref.isEmpty()
                                    && message.contains("You have deposited")) {
                                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                                gwResponse.setMessage(message);
                                gwResponse.setStatus("OK");
                                gwResponse.setTransactionStatus("SUCCESSFUL");
                                gwResponse.setNetworkId(network_ref);
                                gwResponse.setRequestTrace(rs.toString());
                                return gwResponse;
                            }
                        }
                        if (result_code.toUpperCase().equals("ESB000010")) {
                            gwResponse.setHttpStatus(rs.getStatusCode()+"");
                            gwResponse.setMessage(message);
                            gwResponse.setStatus("OK");
                            gwResponse.setTransactionStatus("PENDING");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else if(isFailedErrorList(result_code)){
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else {
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        }
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
            //Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, null, ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return gwResponse;
        } catch (IOException ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
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
                //headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
                url_string = this.global_url+"/standard/v1/payments/"+ref+"/";
            } else {
                //headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
                url_string = this.global_url+"/standard/v1/disbursements/"+ref+"/";
            }
            
            Token token;
            token = this.getToken();
            if (token == null) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("Failed to obtain token for transaction status ");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            }
            
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Country", this.country);
            headers.put("X-Currency", this.base_currency);
           
            String data = "";
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("GET", url_string, data, headers);
            if (rs == null) {
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Failed to obtain transaction status from the network.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace(url_string+""+headers.toString()+""+data);
                return gwResponse;
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
                return gwResponse;
            } else {
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("data")) {
                        JSONObject dataObject = rJson.getJSONObject("data");
                        if (!dataObject.isNull("transaction")) {
                            JSONObject transactionObject = dataObject.getJSONObject("transaction");
                            String status = transactionObject.isNull("status") ? "" : transactionObject.getString("status");
                            String airtel_money_id = transactionObject.isNull("airtel_money_id") ? "" : transactionObject.getString("airtel_money_id");
                            String message = transactionObject.isNull("message") ? "" : transactionObject.getString("message");
                            String id = transactionObject.isNull("id") ? "" : transactionObject.getString("id");
                            String tx_status = "";
                            if (status.toUpperCase().equals("TS")) {
                                tx_status = "SUCCESSFUL";
                            } else if (status.toUpperCase().equals("TF")) {
                                tx_status = "FAILED";
                            } else if (status.toUpperCase().equals("TA")) {
                                tx_status = "UNDETERMINED";
                            } else if (status.toUpperCase().equals("TIP")) {
                                tx_status = "PENDING";
                            }
                            gwResponse.setTransactionStatus(tx_status);
                            gwResponse.setNetworkId(airtel_money_id);
                            gwResponse.setMessage(message);
                        }
                        
                        
                        gwResponse.setOurUniqueTxId(ref);
                        gwResponse.setRequestTrace(rs.toString());
                        return gwResponse;
                    }
                  
                    if (!rJson.isNull("status")) {
                        JSONObject statusObject = rJson.getJSONObject("status");
                        String status = statusObject.isNull("status") ? "" : statusObject.getString("status");
                        String code = statusObject.isNull("code") ? "" : statusObject.getString("code");
                        String result_code = statusObject.isNull("result_code") ? "" : statusObject.getString("result_code");
                        String message = statusObject.isNull("message") ? "" : statusObject.getString("message");
                        
                        if (result_code.toUpperCase().equals("ESB000045")) {
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        }
                        
                        if (result_code.toUpperCase().equals("ESB000010")) {
                            gwResponse.setHttpStatus(rs.getStatusCode()+"");
                            gwResponse.setMessage("Request submitted to the network successfully.");
                            gwResponse.setStatus("OK");
                            gwResponse.setTransactionStatus("PENDING");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else if(isFailedErrorList(result_code)){
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else {
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("PENDING");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        }
                    }
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
        } catch (IOException ex) {
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
    
    private Boolean isFailedErrorList(String error) {
        String[] a = {"ESB000004","ESB000008","ESB000011","ESB000014","ESB000033","ESB000034",
                "ESB000035","ESB000036","ESB000039", "ESB000045"
            };
        for (String a_ : a) {
            if (a_.equals(error))
                return true;
        }
        return false;
    }

    @Override
    public GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative) {
        payer = payer.substring(3);
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            
            Token token;
            token = this.getToken();
            if (token == null) {
                GateWayResponse gwResponse = new GateWayResponse();
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("Failed to obtain token for transaction status ");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace("");
                return gwResponse;
            }
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Country", this.country);
            headers.put("X-Currency", this.base_currency);
            
            JSONObject jdata = new JSONObject();
            
            JSONObject jdataTransaction = new JSONObject();
            jdataTransaction.put("amount", amount);
            jdataTransaction.put("country", this.country);
            jdataTransaction.put("currency", this.base_currency);
            jdataTransaction.put("id", ref);
            
            jdata.put("transaction", jdataTransaction);
            
            JSONObject jdataPayer = new JSONObject();
            jdataPayer.put("currency", this.base_currency);
            jdataPayer.put("country", this.country);
            jdataPayer.put("msisdn", payer);
            jdata.put("subscriber", jdataPayer);
            jdata.put("reference", narrative);
            
            String data = jdata.toString();
            
            String url_string = this.global_url+"/merchant/v1/payments/";
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, data, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus(null);
                gwResponse.setMessage("");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
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
                    if (!rJson.isNull("code")) {
                        res += "Code: "+rJson.getString("code")+" ";
                    }
                    if (!rJson.isNull("statusCode")) {
                        res += "Status Code: "+rJson.getString("statusCode")+" ";
                    }
                    if (!rJson.isNull("message")) {
                        res += "Message: "+rJson.getString("message");
                    }
                }
                
                gwResponse.setMessage(res);
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                if (!rs.getResponse().isEmpty())  {
                   
                    JSONObject rJson = new JSONObject(rs.getResponse());
                   
                    if (!rJson.isNull("status")) {
                        JSONObject statusObject = rJson.getJSONObject("status");
                        String status = statusObject.isNull("status") ? "" : statusObject.getString("status");
                        String code = statusObject.isNull("code") ? "" : statusObject.getString("code");
                        String result_code = statusObject.isNull("result_code") ? "" : statusObject.getString("result_code");
                        String message = statusObject.isNull("message") ? "" : statusObject.getString("message");
                        
                        if (result_code.toUpperCase().equals("ESB000010")) {
                            gwResponse.setHttpStatus(rs.getStatusCode()+"");
                            gwResponse.setMessage("Request submitted to the network successfully.");
                            gwResponse.setStatus("OK");
                            gwResponse.setTransactionStatus("PENDING");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else if(isFailedErrorList(result_code)){
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        } else {
                            gwResponse.setMessage("status:"+status+",Code: "+code+", result_code: "+result_code+", Message: "+message);
                            gwResponse.setStatus("ERROR");
                            gwResponse.setTransactionStatus("FAILED");
                            gwResponse.setRequestTrace(rs.toString());
                            return gwResponse;
                        }
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
            Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, null, ex);
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return gwResponse;
        } catch (IOException ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("");
            return gwResponse;
        }
    }

    @Override
    public PaymentGateway.AccountInfo getAccountInfo(String msisdn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    Boolean isTokenAboutToExpire() throws IOException {
        String filePath = lockfiledirectory+Common.CLASS_PATH_AIRTELOAPI_TOKEN_FILE;
        File resource = new File(filePath);  
        if (resource.createNewFile()) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, 
            "AirtelMoney Token File "+filePath+" has been created.");
        }
        
        //File resource = new ClassPathResource(Common.CLASS_PATH_MTN_TOKEN_FILE).getFile();
       
        String token = new String(
            Files.readAllBytes(resource.toPath())
        );
        
        if (token.isEmpty()) {
            return true;
        }
        
        JSONObject r = null;
        try {
            r = new JSONObject(token);
            
            //If no token for this segment
            if (r.isNull(this.segment)) {
                return true;
            }
            
            JSONObject rO = r.getJSONObject(this.segment);
            
            LocalDateTime whenCreated = LocalDateTime.parse(rO.getString("created_on"));
            Token t = new Token(rO.getString("token"), whenCreated);
            
            //Compare with when it was created
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastCreatedPlus = whenCreated.plusMinutes(1);
            if (lastCreatedPlus.isAfter(now)) {
                return true;
            } else {
                return false;
            }
            
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, ex.getMessage(), ex.getMessage());
            return true;
        }    
    }
    
    public Token getToken() throws IOException {
        String filePath = lockfiledirectory+Common.CLASS_PATH_AIRTELOAPI_TOKEN_FILE;
        File resource = new File(filePath);  
        if (resource.createNewFile()) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, 
            "AIrtelMoney Token File "+filePath+" has been created.");
        }
        
        //File resource = new ClassPathResource(Common.CLASS_PATH_MTN_TOKEN_FILE).getFile();
       
        String token = new String(
            Files.readAllBytes(resource.toPath())
        );
        
        token = token.trim();
        Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName())
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
            Token t = new Token(rO.getString("token"), whenCreated);
            
            //First check if the token is still valid
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastCreatedPlus = whenCreated.plusMinutes(1);
            if (now.isAfter(lastCreatedPlus)) {
                return this.requestToken();
            }
            
            return t;
        } catch (JSONException ex) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, null, ex.getMessage() );
            return null;
        }
    }
    
    
    
    public Token requestToken() throws JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        
        String url_string = this.global_url+"/auth/oauth2/token";
        
        JSONObject jsBody = new JSONObject();
        jsBody.put("client_id", this.api_username);
        jsBody.put("client_secret", this.api_password);
        jsBody.put("grant_type", "client_credentials");
        
        HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, jsBody.toString(), headers);
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
            String token_type = jsToken.getString("token_type");
            LocalDateTime d = LocalDateTime.now();
            
            JSONObject newToken = new JSONObject();
            newToken.put("token", accessToken);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            newToken.put("created_on", d.format(formatter));
            
            String filePath = lockfiledirectory+Common.CLASS_PATH_AIRTELOAPI_TOKEN_FILE;
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, 
                "AirtelMoney Token "+filePath);
            
            //Now save the new token
            try {
                File resource = new File(filePath);  
                if (resource.createNewFile()) {
                    Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, 
                    "AirtelMoney Token File "+filePath+" has been created.");
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
                Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName())
                        .log(Level.SEVERE, ex.getMessage(), ex);
                return null;
            }
            return new Token(accessToken, d);
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
