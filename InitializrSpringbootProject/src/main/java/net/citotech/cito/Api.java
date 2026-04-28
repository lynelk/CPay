/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import net.citotech.cito.Model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import net.citotech.cito.security.CallbackUrlValidator;
import net.citotech.cito.security.SignatureVerificationService;
import net.citotech.cito.service.RateLimiterService;

/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/api")
public class Api {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    RateLimiterService rateLimiterService;
    
    @Value( "${custom.gatewaystate}" )
    private String gatewaystate;

    @Value( "${custom.lockfiledirectory}" )
    private String lockfiledirectory;
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/doMobileMoneyPayIn")
    @CrossOrigin
    public String doMobileMoneyPayIn (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                    .getError("124", 
                            String.format(GeneralException.ERRORS_124, ""));
            }
            
            List<String> fields = new ArrayList<>();
            fields.add("amount");
            fields.add("description");
            fields.add("reference");
            fields.add("merchant_number");
            fields.add("payer_number");
            fields.add("callback_url");
            fields.add("signature");
            
            List<String> missingFields = missingJsonFields(fields, sObject);
            if (missingFields.size() > 0) {
                String missing_f = "";
                for (String s : missingFields ) {
                    missing_f += s+", ";
                }
                missing_f = missing_f.substring(0, (missing_f.length()-2));
                return GeneralException
                    .getError("114", 
                            String.format(GeneralException.ERRORS_114, missing_f));
            }
            String amount_string = sObject.getString("amount");
            Double amount;
            try{
                amount = Double.parseDouble(amount_string);
            } catch (NumberFormatException e) {
                return GeneralException
                    .getError("123", String.format(GeneralException.ERRORS_123,amount_string));
            }

            if (amount <= 0) {
                return GeneralException.getError("123", String.format(GeneralException.ERRORS_123, amount_string));
            }
            
            String description = sObject.getString("description");
            String reference = sObject.getString("reference");
            String merchant_number = sObject.getString("merchant_number");
            String payer_number = sObject.getString("payer_number");
            String signatureBase64 = sObject.getString("signature");
            //String signature = Common.base64Decode(signatureBase64);
            String callback_url = sObject.getString("callback_url");
            String origanting_ip = Common.getIpAddress(request);

            // Validate callback_url to prevent SSRF
            String callbackUrlError = CallbackUrlValidator.validate(callback_url);
            if (callbackUrlError != null) {
                return GeneralException.getError("124", callbackUrlError);
            }

            // Rate limiting per merchant_number
            if (!rateLimiterService.tryConsume(merchant_number)) {
                response.setStatus(429);
                return GeneralException.getError("145", GeneralException.ERRORS_145);
            }
            
            //Get this merchant
            Merchant merchant = Common.getMerchantByAccountNumber(merchant_number+"", jdbcTemplate);
            if (merchant == null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Merchant", merchant_number));
            }
            
            //Check if the user's account is suspended
            if (merchant.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }

            // Check IP whitelist per merchant
            Setting ipWhitelistSetting = Common.getMerchantSettings("api_allowed_ips", merchant.getId(), jdbcTemplate);
            if (ipWhitelistSetting != null && !ipWhitelistSetting.getSetting_value().isEmpty()) {
                String allowedIps = ipWhitelistSetting.getSetting_value();
                boolean ipAllowed = false;
                for (String ip : allowedIps.split(",")) {
                    if (ip.trim().equals(origanting_ip)) {
                        ipAllowed = true;
                        break;
                    }
                }
                if (!ipAllowed) {
                    return GeneralException.getError("139", String.format(GeneralException.ERRORS_139, origanting_ip));
                }
            }
            
            //Verify signature
            String signedData = merchant_number+payer_number+amount_string+reference+description;
            String sigError = SignatureVerificationService.verify(merchant, signedData, signatureBase64);
            if (sigError != null) {
                return sigError;
            }
            
            //Now check if the merchant is not suspended
            if (!merchant.getStatus().equals("ACTIVE")) {
                return GeneralException
                    .getError("119", GeneralException.ERRORS_119);
            }
            
            //Check if this API is allowed.
            String[] allowed_apis = merchant.getAllowed_apis();
            Boolean isAllowedToAccessApi = false;
            for (String api : allowed_apis) {
                if (api.equals(Common.API_MOBILE_MONEY_PAYIN)) {
                    isAllowedToAccessApi = true;
                }
            }
            
            if (!isAllowedToAccessApi) {
                return GeneralException
                    .getError("120", String.format(GeneralException.ERRORS_120, 
                            Common.API_MOBILE_MONEY_PAYIN));
            }
            
            //First check if stock account was configured transaction
            Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
            Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
            if (getStockAccount == null || getStockAccount.getSetting_value().isEmpty()) {
               return GeneralException
                .getError("112", GeneralException.ERRORS_112);
            }
            
            if (getRevenueAccount == null || getRevenueAccount.getSetting_value().isEmpty()) {
               return GeneralException
                .getError("117", GeneralException.ERRORS_117);
            }

            //If it's stock account, this operation is not permitted
            String stock_account_number = getStockAccount.getSetting_value().trim();
            if (merchant.getAccount_number().equals(stock_account_number)) {
                return GeneralException
                    .getError("113", GeneralException.ERRORS_113);
            }
            
            //First determine the gateway by msisdn
            String gateway_id = DoPayGateway.getGatewayIdByMsisdn(payer_number, jdbcTemplate);
            if (gateway_id == null) {
                return GeneralException
                    .getError("118", String.format(GeneralException.ERRORS_118, 
                            payer_number));
            }
            
            //Get this merchant by id.
            Transaction newTx = new Transaction();
            newTx.setGateway_id(gateway_id);
            newTx.setOriginal_amount(amount);
            newTx.setPayer_number(payer_number);
            newTx.setStatus("PENDING");
            newTx.setMerchant_id(merchant.getId()+"");
            newTx.setTx_description(merchant.getShort_name());
            newTx.setTx_merchant_description(description);
            newTx.setTx_type(Transaction.TX_TYPE_PAYIN);
            String tx_id = Common.generateUuid();
            if (gateway_id.equals(AirtelMoneyOpenApiPaymentGateway.gateway_id)) {
                tx_id = getAirtelOpenApiId(merchant_number);
            }
            newTx.setTx_unique_id(tx_id);
            newTx.setTx_merchant_ref(reference);
            newTx.setCallback_url(callback_url);
            newTx.setOriginate_ip(origanting_ip);
            //First get the charging method
            GatewayChargeDetails gwChargingDetails = DoPayGateway
                    .getGatewayChargeDetailsById(jdbcTemplate, 
                            gateway_id,
                            merchant.getId());
            newTx.setCharging_method(gwChargingDetails.getCustomerInboundChargeMethod());
            Double charges = DoPayGateway.getCustomerInboundCharges(amount, gwChargingDetails);
            Double tx_cost = DoPayGateway.getCostOfInboundCharges(amount, gwChargingDetails);
            newTx.setCharges(charges);
            newTx.setTx_cost(tx_cost);
            newTx.setTx_request_trace("");
            newTx.setTx_update_trace("");
            newTx.setTx_gateway_ref("");
            
            String result = Common.doPayIn(newTx,
                merchant,
                jdbcTemplate,
                transactionManager);
            
            return result;
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    List<String> missingJsonFields(List<String> fields, JSONObject jObject) {
        List<String> missing = new ArrayList<String>();
        for (String field : fields) {
            if (jObject.isNull(field)) {
                missing.add(field);
            }
        }
        return missing;
    }
    
    
    
    
    
    /*
    * Initiate a Mobile Money payout request
    */
    @PostMapping(path="/doMobileMoneyPayOut")
    @CrossOrigin
    public String doMobileMoneyPayOut (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                    .getError("124", 
                            String.format(GeneralException.ERRORS_124, ""));
            }
            
            List<String> fields = new ArrayList<>();
            fields.add("amount");
            fields.add("description");
            fields.add("reference");
            fields.add("merchant_number");
            fields.add("payee_number");
            fields.add("callback_url");
            fields.add("signature");
            
            List<String> missingFields = missingJsonFields(fields, sObject);
            if (missingFields.size() > 0) {
                String missing_f = "";
                for (String s : missingFields ) {
                    missing_f += s+", ";
                }
                missing_f = missing_f.substring(0, (missing_f.length()-2));
                return GeneralException
                    .getError("114", 
                            String.format(GeneralException.ERRORS_114, missing_f));
            }
            String amount_string = sObject.getString("amount");
            Double amount;
            try{
                amount = Double.parseDouble(amount_string);
            } catch (NumberFormatException e) {
                return GeneralException
                    .getError("123", String.format(GeneralException.ERRORS_123,amount_string));
            }

            if (amount <= 0) {
                return GeneralException.getError("123", String.format(GeneralException.ERRORS_123, amount_string));
            }
            
            String description = sObject.getString("description");
            String reference = sObject.getString("reference");
            String merchant_number = sObject.getString("merchant_number");
            String payee_number = sObject.getString("payee_number");
            String signatureBase64 = sObject.getString("signature");
            String callback_url = sObject.getString("callback_url");
            String origanting_ip = Common.getIpAddress(request);

            // Validate callback_url to prevent SSRF
            String callbackUrlError = CallbackUrlValidator.validate(callback_url);
            if (callbackUrlError != null) {
                return GeneralException.getError("124", callbackUrlError);
            }

            // Rate limiting per merchant_number
            if (!rateLimiterService.tryConsume(merchant_number)) {
                response.setStatus(429);
                return GeneralException.getError("145", GeneralException.ERRORS_145);
            }
            
            //Get this merchant
            Merchant merchant = Common.getMerchantByAccountNumber(merchant_number+"", jdbcTemplate);
            if (merchant == null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Merchant", merchant_number));
            }
            
            //Check if the user's account is suspended
            if (merchant.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }

            // Check IP whitelist per merchant
            Setting ipWhitelistSetting = Common.getMerchantSettings("api_allowed_ips", merchant.getId(), jdbcTemplate);
            if (ipWhitelistSetting != null && !ipWhitelistSetting.getSetting_value().isEmpty()) {
                String allowedIps = ipWhitelistSetting.getSetting_value();
                boolean ipAllowed = false;
                for (String ip : allowedIps.split(",")) {
                    if (ip.trim().equals(origanting_ip)) {
                        ipAllowed = true;
                        break;
                    }
                }
                if (!ipAllowed) {
                    return GeneralException.getError("139", String.format(GeneralException.ERRORS_139, origanting_ip));
                }
            }
            
            //Verify signature
            String signedData = merchant_number+payee_number+amount_string+reference+description;
            String sigError = SignatureVerificationService.verify(merchant, signedData, signatureBase64);
            if (sigError != null) {
                return sigError;
            }
            
            //Now check if the merchant is not suspended
            if (!merchant.getStatus().equals("ACTIVE")) {
                return GeneralException
                    .getError("119", GeneralException.ERRORS_119);
            }
            
            //Check if this API is allowed.
            String[] allowed_apis = merchant.getAllowed_apis();
            Boolean isAllowedToAccessApi = false;
            for (String api : allowed_apis) {
                if (api.equals(Common.API_MOBILE_MONEY_PAYOUT)) {
                    isAllowedToAccessApi = true;
                }
            }
            
            if (!isAllowedToAccessApi) {
                return GeneralException
                    .getError("120", String.format(GeneralException.ERRORS_120, 
                            Common.API_MOBILE_MONEY_PAYOUT));
            }
            
            //First check if stock account was configured transaction
            Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
            Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
            if (getStockAccount == null || getStockAccount.getSetting_value().isEmpty()) {
               return GeneralException
                .getError("112", GeneralException.ERRORS_112);
            }
            
            if (getRevenueAccount == null || getRevenueAccount.getSetting_value().isEmpty()) {
               return GeneralException
                .getError("117", GeneralException.ERRORS_117);
            }
            
            Setting getSuspenseAccount = Common.getSettings("suspense_account", jdbcTemplate);
            if (getSuspenseAccount == null || getSuspenseAccount.getSetting_value().isEmpty()) {
                return GeneralException
                        .getError("127", GeneralException.ERRORS_127);
            }

            //If it's stock account, this operation is not permitted
            String stock_account_number = getStockAccount.getSetting_value().trim();
            Merchant float_stock_account = Common.getMerchantByAccountNumber(
                    stock_account_number,
                    jdbcTemplate);
            
            if (merchant.getAccount_number().equals(stock_account_number)) {
                return GeneralException
                    .getError("113", GeneralException.ERRORS_113);
            }
            
            //suspense_account
            String suspense_account_number = getSuspenseAccount.getSetting_value().trim();
            Merchant suspense_stock_account = Common.getMerchantByAccountNumber(
                    suspense_account_number,
                    jdbcTemplate);
            
            //First determine the gateway by msisdn
            String gateway_id = DoPayGateway.getGatewayIdByMsisdn(payee_number, jdbcTemplate);
            if (gateway_id == null) {
                return GeneralException
                    .getError("118", String.format(GeneralException.ERRORS_118, 
                            payee_number));
            }
            
            
            //Get this merchant by id.
            Transaction newTx = new Transaction();
            newTx.setGateway_id(gateway_id);
            newTx.setOriginate_ip(origanting_ip);
            newTx.setOriginal_amount(amount);
            newTx.setPayer_number(payee_number);
            newTx.setStatus("PENDING");
            newTx.setMerchant_id(merchant.getId()+"");
            newTx.setTx_description(merchant.getShort_name());
            newTx.setTx_merchant_description(description);
            newTx.setTx_type(Transaction.TX_TYPE_PAYOUT);
            String tx_id = Common.generateUuid();
            if (gateway_id.equals(AirtelMoneyOpenApiPaymentGateway.gateway_id)) {
                tx_id = getAirtelOpenApiId(merchant_number);
            }
            newTx.setTx_unique_id(tx_id);
            newTx.setTx_merchant_ref(reference);
            newTx.setCallback_url(callback_url);
            //First get the charging method
            GatewayChargeDetails gwChargingDetails = DoPayGateway
                    .getGatewayChargeDetailsById(jdbcTemplate, gateway_id, merchant.getId());
            newTx.setCharging_method(gwChargingDetails.getCustomerOutboundChargeMethod());
            Double charges = DoPayGateway.getCustomerOutboundCharges(amount, gwChargingDetails);
            Double tx_cost = DoPayGateway.getCostOfOutboundCharges(amount, gwChargingDetails);
            
            //First check if their is enough balance.
            ArrayList<Balance> balances = Common.getMerchantBalances(merchant.getId()+"", 
                jdbcTemplate);
            
            for (Balance b : balances) {
                if (b.getGateway_id().equals(gateway_id)) {
                    if ((charges + amount) > b.getAmount()) {
                        //Insufficient funds.
                        return GeneralException
                            .getError("111", String.format(GeneralException.ERRORS_111, 
                                    b.getAmount(), b.getCode()+" Balances: "+balances.size()));
                    }
                }
            }
            
            newTx.setCharges(charges);
            newTx.setTx_cost(tx_cost);
            newTx.setTx_request_trace("");
            newTx.setTx_update_trace("");
            newTx.setTx_gateway_ref("");
            
            String result = Common.doPayOut(newTx,
                merchant,
                jdbcTemplate,
                transactionManager);
            return result;
            
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }

    String getAirtelOpenApiId(String merchant_number) {
        String tx_id = Common.generateUuid();
        if (tx_id.length() >= 20) {
            tx_id = merchant_number+"-"+tx_id.substring(0, 10);
        }
        return tx_id;
    }
    
    
    /*
    * API to check the status of an earlier submitted transaction
    */
    @PostMapping(path="/doTransactionCheckStatus")
    @CrossOrigin
    public String doTransactionCheckStatus (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                    .getError("124", 
                            String.format(GeneralException.ERRORS_124, ""));
            }
            
            List<String> fields = new ArrayList<>();
            fields.add("merchant_number");
            fields.add("reference");
            fields.add("signature");
            
            List<String> missingFields = missingJsonFields(fields, sObject);
            if (missingFields.size() > 0) {
                String missing_f = "";
                for (String s : missingFields ) {
                    missing_f += s+", ";
                }
                missing_f = missing_f.substring(0, (missing_f.length()-2));
                return GeneralException
                    .getError("114", 
                            String.format(GeneralException.ERRORS_114, missing_f));
            }
            
            String signatureBase64 = sObject.getString("signature");
            String ourTxReference = !sObject.isNull("uniqueTransactionId") ? 
                    sObject.getString("uniqueTransactionId") : "";
            
            String reference = sObject.getString("reference");
            String merchant_number = sObject.getString("merchant_number");
            
            
            //Get this merchant
            Merchant merchant = Common.getMerchantByAccountNumber(merchant_number+"", jdbcTemplate);
            if (merchant == null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Merchant", merchant_number));
            }
            
            //Check if the user's account is suspended
            if (merchant.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }
            
            //Verify signature
            String signedData = merchant_number+reference;
            String sigError = SignatureVerificationService.verify(merchant, signedData, signatureBase64);
            if (sigError != null) {
                return sigError;
            }
            
            //Now check if the merchant is not suspended
            if (!merchant.getStatus().equals("ACTIVE")) {
                return GeneralException
                    .getError("119", GeneralException.ERRORS_119);
            }
            
            //Check if this API is allowed.
            String[] allowed_apis = merchant.getAllowed_apis();
            Boolean isAllowedToAccessApi = false;
            for (String api : allowed_apis) {
                if (api.equals(Common.API_TRANSACTION_CHECKSTATUS)) {
                    isAllowedToAccessApi = true;
                }
            }
            
            if (!isAllowedToAccessApi) {
                return GeneralException
                    .getError("120", String.format(GeneralException.ERRORS_120, 
                            Common.API_TRANSACTION_CHECKSTATUS));
            }
            
            String sql = "SELECT * FROM `"+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+"`"
                    + " WHERE tx_merchant_ref=:tx_merchant_ref ";
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("tx_merchant_ref", reference);
            
            RowMapper rm = new RowMapper<Transaction>() {
            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Transaction t = new Transaction();
                    t.setId(rs.getLong("id"));
                    t.setCharging_method(rs.getString("charging_method"));
                    t.setCharges(rs.getDouble("charges"));
                    t.setOriginal_amount(rs.getDouble("original_amount"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setStatus(rs.getString("status"));
                    t.setMerchant_id(rs.getString("merchant_id"));
                    t.setTx_description(rs.getString("tx_description"));
                    t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                    t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                    t.setTx_request_trace(rs.getString("tx_request_trace"));
                    t.setTx_unique_id(rs.getString("tx_unique_id"));
                    t.setTx_update_trace(rs.getString("tx_update_trace"));
                    t.setPayer_number(rs.getString("payer_number"));
                    t.setTx_type(rs.getString("tx_type"));
                    t.setCallback_trace(rs.getString("callback_trace"));
                    t.setTx_merchant_ref(rs.getString("tx_merchant_ref"));
                    t.setOriginate_ip(rs.getString("originate_ip"));
                    return t;
                }
            };
            
            List<Transaction> listTransactions = jdbcTemplate.query(sql, parameters, rm);
            if (listTransactions.size() > 0) {
                Transaction t = listTransactions.get(0);
                GateWayResponse pResponse_ = new GateWayResponse();
                pResponse_.setHttpStatus("200");
                pResponse_.setStatus("OK");
                pResponse_.setRequestTrace("");
                pResponse_.setTransactionStatus(t.getStatus());
                pResponse_.setNetworkId(t.getTx_gateway_ref());
                pResponse_.setMessage("");
                pResponse_.setOurUniqueTxId(t.getTx_unique_id());
                return GeneralSuccessResponse
                    .getApiTxMessage("000", 
                            GeneralSuccessResponse.SUCCESS_000, 
                            pResponse_);
            } else {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Transaction", reference));
            }
            
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }


    /*
     * API to check the status of an earlier submitted transaction
     */
    @PostMapping(path="/doSafaricomPayCallback")

    public String doSafaricomPayCallback (@RequestBody String requestBody,
                                            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        Logger.getLogger(AuthenticationController.class.getName())
                .log(Level.INFO, "SAFARICOM API CALLBACK: "+requestBody, requestBody);
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                        .getError("124",
                                String.format(GeneralException.ERRORS_124, ""));
            }

            JSONObject body = sObject.getJSONObject("Body");
            if (body.isNull("stkCallback")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "stkCallback"));
            }
            JSONObject stkCallback = body.getJSONObject("stkCallback");
            String transactionRef = "";
            int ResultCode = 0;
            Double amount = 0.0;
            String transactionCompletionDate = "";
            String CheckoutRequestID = "";
            String networkRef_ = "";



            if (stkCallback.isNull("CheckoutRequestID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "MerchantRequestID"));
            }
            if (stkCallback.isNull("MerchantRequestID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "MerchantRequestID"));
            }
            if (stkCallback.isNull("ResultCode")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultCode"));
            }
            if (stkCallback.isNull("CallbackMetadata")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "CallbackMetadata"));
            }
            CheckoutRequestID = stkCallback.getString("CheckoutRequestID");
            final String networkRef = CheckoutRequestID;

            transactionRef = stkCallback.getString("MerchantRequestID");
            ResultCode = stkCallback.getInt("ResultCode");
            JSONObject CallbackMetadata = stkCallback.getJSONObject("CallbackMetadata");
            JSONArray Item =  CallbackMetadata.getJSONArray("Item");
            for (int i=0; i < Item.length(); i++) {
                JSONObject iTem = Item.getJSONObject(i);
                if (!iTem.isNull("Name")
                        && iTem.getString("Name").equals("Amount") ) {
                    amount = iTem.getDouble("Value");
                }
                if (!iTem.isNull("Name")
                        && iTem.getString("Name").equals("MpesaReceiptNumber") ) {
                    networkRef_ = iTem.getString("Value");
                }
                if (!iTem.isNull("Name")
                        && iTem.getString("Name").equals("TransactionDate") ) {
                    transactionCompletionDate = iTem.getString("Value");
                }
            }

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                    @Override
                    public String doInTransaction(TransactionStatus status) {
                        try {

                            Transaction tx = Common.getTxByNetworkRef(networkRef, jdbcTemplate);
                            if (tx == null) {
                                Logger.getLogger(AuthenticationController.class.getName())
                                        .log(Level.INFO, "SAFARICOM API CALLBACK - Transaction "+networkRef+" Doesnt exists: "+requestBody, requestBody);
                                return GeneralException
                                        .getError("109", String.format(GeneralException.ERRORS_109, "Transaction", networkRef));
                            }
                            tx.setStatus("SUCCESSFUL");
                            tx.setTx_update_trace(requestBody);
                            tx.setResolved_by("SYSTEM");
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK - Transaction "+networkRef+" exists: "+requestBody, requestBody);

                            //tx.setTx_gateway_ref(networkRef);
                            String results = Common.updateTx(tx,
                                    jdbcTemplate,
                                    transactionManager);


                            if (results.equals("success")) {
                                Logger.getLogger(AuthenticationController.class.getName())
                                        .log(Level.INFO, "SAFARICOM API CALLBACK - Transaction UPDATED: ", requestBody);
                                return GeneralSuccessResponse
                                        .getMessage("000","Request processed successfully");
                            } else {
                                Logger.getLogger(AuthenticationController.class.getName())
                                        .log(Level.INFO, "SAFARICOM API CALLBACK - Transaction UPDATE FAILED: ", requestBody);
                                return GeneralException
                                        .getError("109", GeneralException.ERRORS_142);
                            }

                        } catch (Exception e) {
                            //transactionManager.rollback(status);
                            status.setRollbackOnly();
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                            return GeneralException
                                    .getError("102", GeneralException.ERRORS_102);
                        }
                    }
                });

            //Transaction tx = Common.getTxByNetworkRef(CheckoutRequestID, jdbcTemplate);
            return result;

        }  catch (Exception ex) {

            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }


    /*
     * API to check the status of an earlier submitted transaction
     */
    @PostMapping(path="/doSafaricomPayInCallbackResults")

    public String doSafaricomPayInCallbackResults (@RequestBody String requestBody,
                                                    HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        Logger.getLogger(AuthenticationController.class.getName())
                .log(Level.INFO, "SAFARICOM PAYIN API CALLBACK - PAYOUT: "+requestBody, requestBody);

        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                        .getError("124",
                                String.format(GeneralException.ERRORS_124, ""));
            }


            if (sObject.isNull("Body")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "Body"));
            }

            JSONObject stkCallback;
            if (sObject.getJSONObject("Body").isNull("stkCallback")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "stkCallback"));
            }
            stkCallback = sObject.getJSONObject("Body").getJSONObject("stkCallback");
            String transactionRef = "";
            int ResultCode = 0;
            String ResultDesc = "";
            String CheckoutRequestID = "";
            String networkRef = "";
            String msisdn = "";


            if (stkCallback.isNull("CheckoutRequestID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "CheckoutRequestID"));
            }

            if (stkCallback.isNull("ResultCode")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultCode"));
            }

            if (stkCallback.isNull("ResultDesc")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultDesc"));
            }

            ResultCode = stkCallback.getInt("ResultCode");
            ResultDesc = stkCallback.getString("ResultDesc");
            CheckoutRequestID = stkCallback.getString("CheckoutRequestID");
            if (!stkCallback.isNull("CallbackMetadata")) {
                if (!stkCallback.isNull("Item")) {
                    JSONArray items = stkCallback.getJSONArray("Item");
                    for (int i=0; i < items.length(); i++) {
                        JSONObject iTemObject = items.getJSONObject(i);
                        if (!iTemObject.isNull("Name")
                            && !iTemObject.isNull("Value")) {
                            String itemName = iTemObject.getString("Name");
                            String itemValue = iTemObject.getString("Value");

                            if (itemName.equals("MpesaReceiptNumber")) {
                                networkRef = itemValue;
                            }
                        }
                    }
                }
            }


            final String networkRefFinal = networkRef;
            final String CheckoutRequestIDFinal = CheckoutRequestID;

            final int ResultCodeFinal = ResultCode;
            final String ResultDescFinal = ResultDesc;
            /*
            if (ResultCode != 0) {
                return GeneralException
                        .getError("143",
                                GeneralException.ERRORS_143+" ");
            } */
            //Continue to process the transaction

            //Get the reference from the stored tmp file

            /*
            String reference = getPayoutConversationIdToken( ConversationID );
            if (reference.isEmpty()) {
                Logger.getLogger(AuthenticationController.class.getName())
                        .log(Level.SEVERE, "GENERAL INTERNAL ERROR: ConversationID "+ConversationID+" not found", "");
                return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
            }
            */
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        Transaction tx = Common.getTxBySafaricomRef(CheckoutRequestIDFinal, jdbcTemplate);
                        if (tx == null) {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK COLLECTIONS- Transaction "+CheckoutRequestIDFinal+" Doesnt exists: "+requestBody, requestBody);
                            return GeneralException
                                    .getError("109", String.format(GeneralException.ERRORS_109, "Transaction", networkRefFinal));
                        }
                        if (tx.equals("SUCCESSFUL") || tx.equals("FAILED")) {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK COLLECTIONS- Transaction "+CheckoutRequestIDFinal+" Doesnt exists: "+requestBody, requestBody);
                            return GeneralException
                                    .getError("144", String.format(GeneralException.ERRORS_144, "Transaction", CheckoutRequestIDFinal));
                        }
                        if (ResultCodeFinal == 0) {
                            tx.setStatus("SUCCESSFUL");
                        } else {
                            tx.setStatus("FAILED");
                        }
                        tx.setFinalStatusSet(true);
                        tx.setTx_update_trace(requestBody);
                        tx.setResolved_by("SYSTEM");
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.INFO, "SAFARICOM API CALLBACK COLLECTIONS - Transaction "+CheckoutRequestIDFinal+" exists: "+requestBody, requestBody);

                        tx.setTx_gateway_ref(networkRefFinal);
                        String results = Common.updateTx(tx,
                                jdbcTemplate,
                                transactionManager);

                        if (tx.getStatus().equals("SUCCESSFUL") || tx.getStatus().equals("FAILED")) {
                            Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);
                            TxCallback txCallback = new TxCallback(tx, merchant);
                            txCallback.start(jdbcTemplate, transactionManager);
                        }

                        if (results.equals("success")) {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK COLLECTIONS - Transaction UPDATED: ", requestBody);
                            return GeneralSuccessResponse
                                    .getMessage("000","Request processed successfully");
                        } else {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK COLLECTIONS - Transaction UPDATE FAILED: ", requestBody);
                            return GeneralException
                                    .getError("109", GeneralException.ERRORS_142);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                        return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                    }
                }
            });
            //transactionManager.commit();
            //Transaction tx = Common.getTxByNetworkRef(CheckoutRequestID, jdbcTemplate);
            return result;

        }  catch (Exception ex) {

            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }

    /*
     * API to check the status of an earlier submitted transaction
     */
    @PostMapping(path="/doSafaricomPayOutCallbackResults")

    public String doSafaricomPayOutCallbackResults (@RequestBody String requestBody,
                                             HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        Logger.getLogger(AuthenticationController.class.getName())
                .log(Level.INFO, "SAFARICOM API CALLBACK - PAYOUT: "+requestBody, requestBody);

        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                        .getError("124",
                                String.format(GeneralException.ERRORS_124, ""));
            }


            if (sObject.isNull("Result")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "Result"));
            }

            JSONObject stkCallback = sObject.getJSONObject("Result");
            String transactionRef = "";
            int ResultCode = 0;
            int ResultType = 0;
            String ConversationID = "";
            String OriginatorConversationID = "";



            if (stkCallback.isNull("ConversationID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ConversationID"));
            }
            if (stkCallback.isNull("OriginatorConversationID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "OriginatorConversationID"));
            }
            if (stkCallback.isNull("ResultCode")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultCode"));
            }
            if (stkCallback.isNull("ResultType")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultType"));
            }

            ResultCode = stkCallback.getInt("ResultCode");
            ResultType = stkCallback.getInt("ResultType");
            ConversationID = stkCallback.getString("ConversationID");
            final String networkRef = ConversationID;
            final String Conversation_ID = ConversationID;

            final int ResultCodeFinal = ResultCode;
            final int ResultTypeFinal = ResultType;
            /*
            if (ResultCode != 0) {
                return GeneralException
                        .getError("143",
                                GeneralException.ERRORS_143+" ");
            } */
            //Continue to process the transaction

            //Get the reference from the stored tmp file

            /*
            String reference = getPayoutConversationIdToken( ConversationID );
            if (reference.isEmpty()) {
                Logger.getLogger(AuthenticationController.class.getName())
                        .log(Level.SEVERE, "GENERAL INTERNAL ERROR: ConversationID "+ConversationID+" not found", "");
                return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
            }
            */
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        Transaction tx = Common.getTxBySafaricomRef(Conversation_ID, jdbcTemplate);
                        if (tx == null) {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT- Transaction "+Conversation_ID+" Doesnt exists: "+requestBody, requestBody);
                            return GeneralException
                                    .getError("109", String.format(GeneralException.ERRORS_109, "Transaction", networkRef));
                        }
                        if (ResultCodeFinal == 0 && ResultTypeFinal == 0) {
                            tx.setStatus("SUCCESSFUL");
                        } else {
                            tx.setStatus("FAILED");
                        }
                        tx.setFinalStatusSet(true);
                        tx.setTx_update_trace(requestBody);
                        tx.setResolved_by("SYSTEM");
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction "+Conversation_ID+" exists: "+requestBody, requestBody);

                        //tx.setTx_gateway_ref(networkRef);
                        String results = Common.updateTx(tx,
                                jdbcTemplate,
                                transactionManager);

                        if (tx.getStatus().equals("SUCCESSFUL") || tx.getStatus().equals("FAILED")) {
                            Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);
                            TxCallback txCallback = new TxCallback(tx, merchant);
                            txCallback.start(jdbcTemplate, transactionManager);
                        }

                        if (results.equals("success")) {
                            getPayoutConversationIdDeleteFile(Conversation_ID);
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction UPDATED: ", requestBody);
                            return GeneralSuccessResponse
                                    .getMessage("000","Request processed successfully");
                        } else {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction UPDATE FAILED: ", requestBody);
                            return GeneralException
                                    .getError("109", GeneralException.ERRORS_142);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                        return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                    }
                }
            });
            //transactionManager.commit();
            //Transaction tx = Common.getTxByNetworkRef(CheckoutRequestID, jdbcTemplate);
            return result;

        }  catch (Exception ex) {

            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }


    /*
     * API to check the status of an earlier submitted transaction
     */
    @PostMapping(path="/doSafaricomPayOutCallback")

    public String doSafaricomPayOutCallback (@RequestBody String requestBody,
                                          HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        Logger.getLogger(AuthenticationController.class.getName())
                .log(Level.INFO, "SAFARICOM API CALLBACK - PAYOUT: "+requestBody, requestBody);
       if (true) {
           return "Callback received";
       }
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                        .getError("124",
                                String.format(GeneralException.ERRORS_124, ""));
            }


            if (sObject.isNull("Result")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "Result"));
            }

            JSONObject stkCallback = sObject.getJSONObject("Result");
            String transactionRef = "";
            int ResultCode = 0;
            int ResultType = 0;
            String ConversationID = "";
            String OriginatorConversationID = "";



            if (stkCallback.isNull("ConversationID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ConversationID"));
            }
            if (stkCallback.isNull("OriginatorConversationID")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "OriginatorConversationID"));
            }
            if (stkCallback.isNull("ResultCode")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultCode"));
            }
            if (stkCallback.isNull("ResultType")) {
                return GeneralException
                        .getError("114",
                                String.format(GeneralException.ERRORS_114, "ResultType"));
            }

            ResultCode = stkCallback.getInt("ResultCode");
            ResultType = stkCallback.getInt("ResultType");
            ConversationID = stkCallback.getString("ConversationID");
            final String networkRef = ConversationID;
            final String Conversation_ID = ConversationID;

            final int ResultCodeFinal = ResultCode;
            /*
            if (ResultCode != 0) {
                return GeneralException
                        .getError("143",
                                GeneralException.ERRORS_143+" ");
            } */
            //Continue to process the transaction

            //Get the reference from the stored tmp file

            String reference = getPayoutConversationIdToken( ConversationID );
            if (reference.isEmpty()) {
                Logger.getLogger(AuthenticationController.class.getName())
                        .log(Level.SEVERE, "GENERAL INTERNAL ERROR: ConversationID "+ConversationID+" not found", "");
                return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
            }

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        Transaction tx = Common.getTxBySafaricomRef(reference, jdbcTemplate);
                        if (tx == null) {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT- Transaction "+reference+" Doesnt exists: "+requestBody, requestBody);
                            return GeneralException
                                    .getError("109", String.format(GeneralException.ERRORS_109, "Transaction", networkRef));
                        }
                        if (ResultCodeFinal != 0) {
                            tx.setStatus("FAILED");
                        } else {
                            tx.setStatus("SUCCESSFUL");
                        }
                        tx.setFinalStatusSet(true);
                        tx.setTx_update_trace(requestBody);
                        tx.setResolved_by("SYSTEM");
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction "+reference+" exists: "+requestBody, requestBody);

                        //tx.setTx_gateway_ref(networkRef);
                        String results = Common.updateTx(tx,
                                jdbcTemplate,
                                transactionManager);


                        if (results.equals("success")) {
                            getPayoutConversationIdDeleteFile(Conversation_ID);
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction UPDATED: ", requestBody);
                            return GeneralSuccessResponse
                                    .getMessage("000","Request processed successfully");
                        } else {
                            Logger.getLogger(AuthenticationController.class.getName())
                                    .log(Level.INFO, "SAFARICOM API CALLBACK DISBURSEMENT - Transaction UPDATE FAILED: ", requestBody);
                            return GeneralException
                                    .getError("109", GeneralException.ERRORS_142);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                        return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                    }
                }
            });
            //transactionManager.commit();
            //Transaction tx = Common.getTxByNetworkRef(CheckoutRequestID, jdbcTemplate);
            return result;

        }  catch (Exception ex) {

            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }

    public String getPayoutConversationIdToken(String ConversationID) throws IOException {
        String separator = File.separator;
        String filePath = /*lockfiledirectory+*/ConversationID+".json";
        File resource = new File(filePath);
        if (!resource.exists()) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, "ConversationID: "+resource.getAbsolutePath()+" DOES NOT EXISTS", "" );
            return "";
        }

        //File resource = new ClassPathResource(Common.CLASS_PATH_MTN_TOKEN_FILE).getFile();
        String data = new String(
                Files.readAllBytes(resource.toPath())
        );

        Logger.getLogger(SettingsController.class.getName())
                .log(Level.INFO, "ConversationID Data Stored "+data, " " );
        JSONObject r = null;
        try {
            if (data.isEmpty()) {
                //No token, request for a new one
                return "";
            }

            r = new JSONObject(data);
            if (!r.isNull("txRef") && !r.isNull("ConversationID")) {
                if (r.getString("ConversationID").equals(ConversationID)) {
                    return r.getString("txRef");
                }
            }
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, "ID "+ConversationID+" does not match in "+data, "");
            return "";
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, null, ex.getMessage() );
            return "";
        }
    }

    private void getPayoutConversationIdDeleteFile(String ConversationID) throws IOException {
        String separator = File.separator;
        String filePath = /*lockfiledirectory +*/ ConversationID + ".json";
        File resource = new File(filePath);
        if (resource.exists()) {
            if (resource.delete()) {
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, "FILE: " + filePath + " deleted", "");
            }
            return;
        }
    }
    
    /*
    * API to retrieve merchant balances
    */
    @PostMapping(path="/doGetBalances")

    public String doGetBalances (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                    .getError("124", 
                            String.format(GeneralException.ERRORS_124, ""));
            }
            
            List<String> fields = new ArrayList<>();
            fields.add("merchant_number");
            fields.add("signature");
            
            List<String> missingFields = missingJsonFields(fields, sObject);
            if (missingFields.size() > 0) {
                String missing_f = "";
                for (String s : missingFields ) {
                    missing_f += s+", ";
                }
                missing_f = missing_f.substring(0, (missing_f.length()-2));
                return GeneralException
                    .getError("114", 
                            String.format(GeneralException.ERRORS_114, missing_f));
            }
            
            String signatureBase64 = sObject.getString("signature");
            String merchant_number = sObject.getString("merchant_number");
            
            
            //Get this merchant
            Merchant merchant = Common.getMerchantByAccountNumber(merchant_number+"", jdbcTemplate);
            if (merchant == null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Merchant", merchant_number));
            }
            
            //Check if the user's account is suspended
            if (merchant.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }
            
            //Verify signature
            String signedData = merchant_number;
            String sigError = SignatureVerificationService.verify(merchant, signedData, signatureBase64);
            if (sigError != null) {
                return sigError;
            }
            
            //Now check if the merchant is not suspended
            if (!merchant.getStatus().equals("ACTIVE")) {
                return GeneralException
                    .getError("119", GeneralException.ERRORS_119);
            }
            
            //Check if this API is allowed.
            String[] allowed_apis = merchant.getAllowed_apis();
            Boolean isAllowedToAccessApi = false;
            for (String api : allowed_apis) {
                if (api.equals(Common.API_BALANCE_CHECK)) {
                    isAllowedToAccessApi = true;
                }
            }
            
            if (!isAllowedToAccessApi) {
                return GeneralException
                    .getError("120", String.format(GeneralException.ERRORS_120, 
                            Common.API_BALANCE_CHECK));
            }
            
            List<Balance> balances = Common.getMerchantBalances(merchant.getId()+"", 
                    jdbcTemplate);
            
            JSONArray jArray = new JSONArray();
            for (Balance b : balances) {
                JSONObject bObject = new JSONObject();
                bObject.put("name", b.getCode());
                bObject.put("amount", b.getAmount());
                //bObject.put("type", b.getBalance_type()[0]);
                bObject.put("base_currency", b.getBalance_type()[1]);
                jArray.put(bObject);
            }
            
            return GeneralSuccessResponse
                    .getApiTxBalances("000", 
                            GeneralSuccessResponse.SUCCESS_000, 
                            jArray);
            
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    /*
    * API to send SMS
    */
    @PostMapping(path="/doSendSms")

    public String doSendSms (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            //Ensure that we have valid JSON data.
            JSONObject sObject;
            try {
                sObject = new JSONObject(requestBody);
            } catch (JSONException e) {
                return GeneralException
                    .getError("124", 
                            String.format(GeneralException.ERRORS_124, ""));
            }
            
            List<String> fields = new ArrayList<>();
            fields.add("merchant_number");
            fields.add("recipients");
            fields.add("content");
            fields.add("signature");
            
            List<String> missingFields = missingJsonFields(fields, sObject);
            if (missingFields.size() > 0) {
                String missing_f = "";
                for (String s : missingFields ) {
                    missing_f += s+", ";
                }
                missing_f = missing_f.substring(0, (missing_f.length()-2));
                return GeneralException
                    .getError("114", 
                            String.format(GeneralException.ERRORS_114, missing_f));
            }
            
            String content = sObject.getString("content");
            String recipients = sObject.getString("recipients");
            String merchant_number = sObject.getString("merchant_number");
            String signatureBase64 = sObject.getString("signature");
            String send_time = "";
            
            if (!sObject.isNull("send_time")) {
                send_time = sObject.getString("send_time");
                
                //Check send_time is passed
                SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date d1 = sdformat.parse(send_time);
                Date d2 = new Date();

                if (d2.compareTo(d1) > 0) {
                    return GeneralException
                        .getError("135", GeneralException.ERRORS_135);
                }
            } else {
                SimpleDateFormat sdformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                send_time = sdformat.format(new Date());
            }
            
            //Get this merchant
            Merchant merchant = Common.getMerchantByAccountNumber(merchant_number+"", jdbcTemplate);
            if (merchant == null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "Merchant", merchant_number));
            }
            
            //Check if the user's account is suspended
            if (merchant.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }
            
            //Verify signature
            String signedData = merchant_number+content+recipients;
            //Logger.getLogger(Common.class.getName()).log(Level.SEVERE, "SignedData: "+signedData, "");
            //Get Merchant public key
            if (merchant.getPublic_key() == null || merchant.getPublic_key().isEmpty()) {
                //Now keys configured then
                return GeneralException
                    .getError("115", GeneralException.ERRORS_115);
            }
            
            //Now verify signature.
            Signature sign = Signature.getInstance("SHA256withRSA");
            String base64_public_key = merchant.getPublic_key();
            base64_public_key = base64_public_key.replace("-----BEGIN PUBLIC KEY-----\n", "");
            String base64_cleaned = base64_public_key.replace("\n-----END PUBLIC KEY-----\n", "");
            PublicKey publicKey = Common.getPublicKeyFromBase64String(base64_cleaned);
            sign.initVerify(publicKey);
            sign.update(signedData.getBytes());
            
            //Try to decode base64 to byte array
            byte[] signature_content;
            try{
                signature_content = Base64.getDecoder().decode(signatureBase64);
            } catch (Exception e) {
                return GeneralException
                    .getError("122", GeneralException.ERRORS_122);
            }
            
            if (signature_content.length < 256) {
                return GeneralException
                    .getError("122", GeneralException.ERRORS_122);
            }
            
            if (!sign.verify(signature_content)) {
                return GeneralException
                    .getError("116", GeneralException.ERRORS_116);
            }
            
            //Now check if the merchant is not suspended
            if (!merchant.getStatus().equals("ACTIVE")) {
                return GeneralException
                    .getError("119", GeneralException.ERRORS_119);
            }
            
            //Check if this API is allowed.
            String[] allowed_apis = merchant.getAllowed_apis();
            Boolean isAllowedToAccessApi = false;
            for (String api : allowed_apis) {
                if (api.equals(Common.API_SEND_SMS)) {
                    isAllowedToAccessApi = true;
                }
            }
            
            if (!isAllowedToAccessApi) {
                return GeneralException
                    .getError("120", String.format(GeneralException.ERRORS_120, 
                            Common.API_SEND_SMS));
            }
            
            MerchantSms newSms = new MerchantSms();
            newSms.setCreated_by("SYSTEM API");
            newSms.setContent(content);
            newSms.setMerchant_id(BigInteger.valueOf(merchant.getId()));
            newSms.setSend_time(send_time);
            newSms.setGw_response("");
            newSms.setStatus("PENDING");
            newSms.setTrace("");
            
             //Now get recipients
            int total_recipients = 0;
            String[] recipientsArray = recipients.split(",");
            
            total_recipients = recipientsArray.length;
            SmsGateway smsgw = new SmsGateway(jdbcTemplate);
            newSms.setSmsgw(smsgw.getGatewayName());
            
            double charge = smsgw.getCharge(merchant.getId());
            newSms.setCharge(charge);
            newSms.setCost(smsgw.getCost());
            final double total_amount = (charge * total_recipients);
            
            ArrayList<Balance> balances = Common.getMerchantBalances(merchant.getId()+"", 
                    jdbcTemplate);
            //Check whether the user has enough funds
            for (Balance b : balances) {
                if (b.getGateway_id().equals(SmsGateway.getGatewayId())) {
                    if (b.getAmount() < total_amount) {
                        return GeneralException
                            .getError("111", 
                                String.format(GeneralException.ERRORS_111, b.getAmount(), "SMS Account"));
                    }
                }
            }
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        //Now add the user to database
                        String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_SMS+" "
                            +" SET `merchant_id`=:merchant_id,"
                            +" `cost`=:cost, "
                            +" `charge`=:charge, "
                            +" `created_by`=:created_by,"
                            +" `status`=:status,"
                            +" `total_recipients`=:total_recipients,"
                            +" `content`=:content,"
                            +" `gw_response`=:gw_response,"
                            +" `smsgw`=:smsgw,"
                            +" `trace`=:trace,"
                            +" `send_time`=:send_time,"
                            +" `total_amount`=:total_amount,"
                            +" `recipients`=:recipients";
                        MapSqlParameterSource parameters = new MapSqlParameterSource();
                        newSms.setRecipients(recipients);
                        newSms.setTotal_recipients(recipientsArray.length);
                        newSms.setTotal_amount(total_amount);

                        MerchantSms newSms_ = newSms;
                        parameters.addValue("merchant_id", newSms_.getMerchant_id());
                        parameters.addValue("created_by", "SMS API");
                        parameters.addValue("status", newSms_.getStatus());
                        parameters.addValue("total_amount", newSms_.getTotal_amount());
                        parameters.addValue("charge", newSms_.getCharge());
                        parameters.addValue("cost", newSms_.getCost());
                        parameters.addValue("total_recipients", newSms_.getTotal_recipients());
                        parameters.addValue("trace", newSms_.getTrace());
                        parameters.addValue("content", newSms_.getContent());
                        parameters.addValue("gw_response", newSms_.getGw_response());
                        parameters.addValue("smsgw", newSms_.getSmsgw());
                        parameters.addValue("send_time", newSms_.getSend_time());
                        parameters.addValue("recipients", newSms_.getRecipients());
                        //Now save the SMS
                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        //long userId;
                        jdbcTemplate.update(sql, parameters, keyHolder);
                        //Now insert privileges
                        BigInteger smsId = (BigInteger)keyHolder.getKey();
                        
                        //TransactionManager.commit(status);
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                return GeneralSuccessResponse
                        .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
            
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, "GENERAL INTERNAL ERROR: "+ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/testCallbackReception")

    public String testCallbackReception (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            JSONObject sObject = new JSONObject(requestBody);
            Double amount = sObject.getDouble("amount");
            String description = sObject.getString("description");
            String reference = sObject.getString("reference");
            String payee_number = sObject.getString("payer_number");
            String signatureBase64 = sObject.getString("signature");
            String status = sObject.getString("status");
            String completed_on = sObject.getString("completed_on");
            String created_on = sObject.getString("created_on");
            String network_ref = sObject.getString("network_ref");
            
            return GeneralSuccessResponse
                .getMessage("000", GeneralSuccessResponse.SUCCESS_000+". Ref: "+reference);
            
        } catch (JSONException e) {
            return GeneralException
                .getError("124", 
                        String.format(GeneralException.ERRORS_124, requestBody));
        }
        
        
    }
}
