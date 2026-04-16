/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 *
 * @author josephtabajjwa
 */
public class SmsGateway {
    NamedParameterJdbcTemplate jdbcTemplate;
    String url;
    String params;
    String method;
    String gateway_name;
    Double cost;
    public static String gateway_id = "SmsGateway";
    
    static public String BALANCE_TYPE = "sms_balance";
    
    public static String gateway_currency_code = "UGXSMS";
    
    public SmsGateway(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.initializeSettings();
    }
    
    static public String getGatewayCurrencyCode() {
        return gateway_currency_code;
    }
    
    static public String getGatewayId() {
        return gateway_id;
    }
    
    void initializeSettings() {
        url = Common.getSettings("sms_api_url", jdbcTemplate)
                    .getSetting_value();
        params = Common.getSettings("sms_api_parameters", jdbcTemplate)
                    .getSetting_value();
        method = Common.getSettings("sms_api_http_method", jdbcTemplate)
                    .getSetting_value();
        gateway_name = Common.getSettings("sms_gateway_name", jdbcTemplate)
                    .getSetting_value();
        cost = this.getCost();
    }
    
    public String getGatewayName() {
        return gateway_name;
    }
    
    MerchantSms sendSms(MerchantSms sms) {
        try {
            Map<String, String> headers = new HashMap<>();
            
            String data = "";
            String[] params_array = params.split(",");
            String final_params = "";
            for (String param : params_array) {
                String p = param.replace("{CONTENT}", sms.content);
                p = p.replace("{MSISDNS}", sms.recipients);
                final_params += p+"&";
            }
            if (!final_params.isEmpty()) {
                final_params = final_params.substring(0, (final_params.length()-1));
            }
            String url_string;
            if (method.equals("GET")) {
                url_string = this.url+"?"+final_params;
            } else {
                //If post method
                headers.put("Content-Type", "x-www-form-urlencoded");
                data = final_params;
                url_string = this.url;
            }
            
            //Now generate the response.
            HttpRequestResponse rs = Common.doHttpRequest(method, url_string, data, headers);
            if (rs == null) {
                
                sms.setTrace(url_string+""+headers.toString()+""+data);
                sms.setStatus("ERROR");
                sms.setGw_response("HTTP Response set to null.");
               
                return sms;
            }
            
            if (rs.getStatusCode() != 200) {
                
                sms.setStatus("ACCEPTED");
                sms.setTrace(rs.toString());
                sms.setGw_response(rs.getResponse());
                return sms;
                
            } else {
                sms.setStatus("ERROR");
                sms.setTrace(rs.toString());
                sms.setGw_response(rs.getResponse());
                
                return sms;
            }
        } catch (Exception ex) {
            
            sms.setStatus("ERROR");
            sms.setTrace(ex.getMessage());
            sms.setGw_response("");

            return sms;
        }
    }
    
    public Double getRevenue(Double charge) {
        return (charge - cost);
    }
    
    public Double getCost() {
        String cost = Common.getSettings("sms_gateway_cost", jdbcTemplate)
                    .getSetting_value();
        if (cost.isEmpty()) {
            return 0.0;
        } else {
            return Double.parseDouble(cost);
        }
    }
    
    public Double getCharge() {
        String charge = Common.getSettings("sms_customer_charge", jdbcTemplate)
                    .getSetting_value();
        if (charge.isEmpty()) {
            return 0.0;
        } else {
            return Double.parseDouble(charge);
        }
    }
    
    
    /*
    * Get merchant charge only if available, otherwise 
    * get it from the default settings.
    */
    public Double getCharge(Long merchant_id) {
        Setting merchant_charge = Common.getMerchantSettings("sms_customer_charge", 
                merchant_id, 
                jdbcTemplate);
        
        if (merchant_charge == null || merchant_charge.getSetting_value().isEmpty()) {
            String charge = Common.getSettings("sms_customer_charge", jdbcTemplate)
                    .getSetting_value();
            if (charge.isEmpty()) {
                return 0.0;
            } else {
                return Double.parseDouble(charge);
            }
        } else {
            return Double.parseDouble(merchant_charge.getSetting_value());
        }
    }
}
