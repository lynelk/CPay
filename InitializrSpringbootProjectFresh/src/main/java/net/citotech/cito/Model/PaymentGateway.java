/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import static net.citotech.cito.Model.MTNMoMoPaymentGateway.prefix;

/**
 *
 * @author josephtabajjwa
 */
public abstract class PaymentGateway {
    String gateway_id;
    String status;
    String mode;
    String api_url;
    String api_username;
    String api_password;
    String test_api_url;
    String test_api_username;
    String test_api_password;
    static public String[] prefix = {};
    
    public static boolean isValidMisdn(String msisdn) {
        for (String s : prefix) {
            if (msisdn.matches("/^"+s+"/")) {
                return true;
            }
        }
        return false;
    }
    
    public abstract Double getBalance();
    
    public abstract Double getBalance(String account);
    
    public abstract GateWayResponse doPayOut(Double amount, String payee, String ref, String narrative);
    
    public abstract GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative);
    
    public abstract GateWayResponse checkStatus(String ref);
    
    public abstract AccountInfo getAccountInfo(String msisdn);
    
    public class AccountInfo {
        String firstName = "";
        String lastName = "";
        String address = "";
        String account = "";
        String nationality = "";
        String msisdn = "";
        String status = "";
        String provided_name = "";

        public AccountInfo() {}

        public AccountInfo(String fName, String lName, String account) {
            this.firstName = fName;
            this.lastName = lName;
            this.account = account;
        }

        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getMsisdn() { return msisdn; }
        public void setMsisdn(String v) { this.msisdn = v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { this.status = v; }
        public String getProvided_name() { return provided_name; }
        public void setProvided_name(String v) { this.provided_name = v; }
    }
    
}

