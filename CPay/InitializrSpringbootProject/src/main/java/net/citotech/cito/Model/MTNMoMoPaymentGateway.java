/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;
import net.citotech.cito.TransactionsLogController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.format.annotation.DateTimeFormat;

/**
 *
 * @author josephtabajjwa
 */
public class MTNMoMoPaymentGateway extends PaymentGateway{
    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    String global_url = "https://ericssonbasicapi1.azure-api.net";
    String env = "mtnuganda";//sandbox
    String base_currency = "UGX";
    String segment = "collection";//disbursement";
    
    static public String BALANCE_TYPE = "mtnmm_balance";
    
    String api_collections_user = "";
    String api_collections_key = "";
    String api_collections_subscription = "";

    String api_disbursements_user = "";
    String api_disbursements_key = "";
    String api_disbursements_subscription = "";
    
    public static String[] prefix = {"25677", "25678", "25676"};
    
    public static String gateway_id = "MTNMoMoPaymentGateway";
    
    public static String gateway_currency_code = "MTNMM";
    
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
    
    public void setApiDetails(String global_url, String api_collections_user, 
            String api_collections_key, String api_collections_subscription,
            String api_disbursements_user, String api_disbursements_key,
            String api_disbursements_subscription, String env, String base_currency) {
        
        this.global_url = global_url;
        this.api_collections_user = api_collections_user;
        this.api_collections_key = api_collections_key;
        this.api_collections_subscription = api_collections_subscription;
        this.api_disbursements_user = api_disbursements_user;
        this.api_disbursements_key = api_disbursements_key;
        this.api_disbursements_subscription = api_disbursements_subscription;
        this.base_currency = base_currency;
        this.env = env;
        
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
                headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
                url_string = this.global_url+"/"+this.segment+"/v1_0/account/balance";
            } else {
                headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
                url_string = this.global_url+"/"+this.segment+"/v1_0/account/balance";
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
            headers.put("X-Target-Environment", this.env);
           
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
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    
                    
                    String balance_string = "";
                    if (!rJson.isNull("availableBalance")) {
                        balance_string = rJson.getString("availableBalance");
                        double bal = Double.parseDouble(balance_string);
                        return bal;
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
        
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "disbursement";
            
            Token token;
            token = this.getToken();
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Reference-Id", ref);
            headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
            headers.put("X-Target-Environment", this.env);
            headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
            
            JSONObject jdata = new JSONObject();
            jdata.put("amount", amount);
            jdata.put("currency", this.base_currency);
            jdata.put("externalId", ref);
            
            JSONObject jdataPayer = new JSONObject();
            jdataPayer.put("partyIdType", "MSISDN");
            jdataPayer.put("partyId", payee);
            jdata.put("payee", jdataPayer);
            
            jdata.put("payerMessage", narrative);
            jdata.put("payeeNote", narrative);
            
            String data = jdata.toString();
            
            
            String url_string = this.global_url+"/"+this.segment+"/v1_0/transfer";
            
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
            
            if (rs.getStatusCode() != 202) {
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
    public GateWayResponse checkStatus(String ref) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            String url_string = "";
            if (this.segment.equals("collection")) {
                headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
                url_string = this.global_url+"/"+this.segment+"/v1_0/requesttopay/"+ref;
            } else {
                headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
                url_string = this.global_url+"/"+this.segment+"/v1_0/transfer/"+ref;
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
                return gwResponse;
            }
            
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Target-Environment", this.env);
           
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

    @Override
    public GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/json");
            this.segment = "collection";
            
            
            Token token;
            token = this.getToken();
            headers.put("Authorization", "Bearer "+token.getToken());
            headers.put("X-Reference-Id", ref);
            headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
            headers.put("X-Target-Environment", this.env);
            headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
            
            JSONObject jdata = new JSONObject();
            jdata.put("amount", amount);
            jdata.put("currency", this.base_currency);
            jdata.put("externalId", ref);
            
            JSONObject jdataPayer = new JSONObject();
            jdataPayer.put("partyIdType", "MSISDN");
            jdataPayer.put("partyId", payer);
            jdata.put("payer", jdataPayer);
            
            jdata.put("payerMessage", narrative);
            jdata.put("payeeNote", narrative);
            
            String data = jdata.toString();
            
            
            String url_string = this.global_url+"/"+this.segment+"/v1_0/requesttopay";
            
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
            
            if (rs.getStatusCode() != 202) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                String res = "";
                if (!rs.getResponse().isEmpty())  {
                    JSONObject rJson = new JSONObject(rs.getResponse());
                    if (!rJson.isNull("code")) {
                        res += "Code: "+rJson.getString("code")+" ";
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
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage("Request submitted to the network successfully.");
                gwResponse.setStatus("OK");
                gwResponse.setTransactionStatus("PENDING");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
        } catch (JSONException ex) {
            ex.printStackTrace();
            Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
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
    public AccountInfo getAccountInfo(String msisdn) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    Boolean isTokenAboutToExpire() throws IOException {
        String filePath = lockfiledirectory+Common.CLASS_PATH_MTN_TOKEN_FILE;
        File resource = new File(filePath);  
        if (resource.createNewFile()) {
            Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, 
            "MTN Token File "+filePath+" has been created.");
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
            LocalDateTime lastCreatedPlus = whenCreated.plusMinutes(55);
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
        String filePath = lockfiledirectory+Common.CLASS_PATH_MTN_TOKEN_FILE;
        File resource = new File(filePath);  
        if (resource.createNewFile()) {
            Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, 
            "MTN Token File "+filePath+" has been created.");
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
            Token t = new Token(rO.getString("token"), whenCreated);
            
            //First check if the token is still valid
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastCreatedPlus = whenCreated.plusMinutes(55);
            if (now.isAfter(lastCreatedPlus)) {
                return this.requestToken();
            }
            
            return t;
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, null, ex.getMessage() );
            return null;
        }
    }
    
    
    
    public Token requestToken() throws JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (segment.equals("collection")) {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_collections_user+":"+this.api_collections_key));
            headers.put("Ocp-Apim-Subscription-Key", this.api_collections_subscription);
        } else {
            headers.put("Authorization", "Basic "+Common.base64Encode(this.api_disbursements_user+":"+this.api_disbursements_key));
            headers.put("Ocp-Apim-Subscription-Key", this.api_disbursements_subscription);
        }
        
        String url_string = this.global_url+"/"+this.segment+"/token/";
        
        HttpRequestResponse rs = Common.doHttpRequest("POST", url_string, "", headers);
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
            
            String filePath = lockfiledirectory+Common.CLASS_PATH_MTN_TOKEN_FILE;
            Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, 
                "MTN Token "+filePath);
            
            //Now save the new token
            try {
                File resource = new File(filePath);  
                if (resource.createNewFile()) {
                    Logger.getLogger(MTNMoMoPaymentGateway.class.getName()).log(Level.SEVERE, 
                    "MTN Token File "+filePath+" has been created.");
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
