/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author josephtabajjwa
 */
public class Balance {
    String baseCurrency;
    String code;
    Double amount;
    String gateway_id;
    String[] balance_type;
    
    public static String BALANCE_TYPE_MTNMM_BALANCE[] = {"mtnmm_balance", "UGX MTN MM"};
    public static String BALANCE_TYPE_SAFARICOMMM_BALANCE[] = {"safaricom_balance", "KES MPESA"};
    public static String BALANCE_TYPE_AIRTELMM_BALANCE[] = {"airtelmm_balance", "UGX AIRTEL MM"};
    public static String BALANCE_TYPE_AIRTELMM_OPENAPI_BALANCE[] = {"airtelmm_balance", /*airteloapimm_balance",*/ "UGX AIRTEL MM"};
    public static String BALANCE_TYPE_SMS_BALANCE[] = {"sms_balance", "UGX SMS"};
    
    
    public Balance(String code, Double amount, String gateway_id) {
        this.code = code;
        this.amount = amount;
        this.gateway_id = gateway_id;
    }

    public String[] getBalance_type() {
        return balance_type;
    }

    public void setBalance_type(String[] balance_type) {
        this.balance_type = balance_type;
    }
    
    public static String[] getBalanceTypeByGatewayId(String gateway_id) {
        if (gateway_id.equals("MTNMoMoPaymentGateway")) {
            return BALANCE_TYPE_MTNMM_BALANCE;
        }
        if (gateway_id.equals("AirtelMoneyPaymentGateway")) {
            return BALANCE_TYPE_AIRTELMM_BALANCE;
        }
        if (gateway_id.equals("AirtelMoneyOpenApiPaymentGateway")) {
            return BALANCE_TYPE_AIRTELMM_OPENAPI_BALANCE;
        }
        if (gateway_id.equals("SafariComPaymentGateway")) {
            return BALANCE_TYPE_SAFARICOMMM_BALANCE;
        }
        return null;
    }

    
    public String getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(String baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getGateway_id() {
        return gateway_id;
    }

    public void setGateway_id(String gateway_id) {
        this.gateway_id = gateway_id;
    }
    
}
