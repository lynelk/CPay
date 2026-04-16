/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

/**
 *
 * @author josephtabajjwa
 */
public class Statement {
    long id;
    long merchant_id;
    long transactions_log_id;
    String gateway_id;
    String description;
    Double amount;
    Double mtnmm_balance;
    Double airtelmm_balance;
    Double safaricom_balance;
    Double sms_balance;
    String tx_type;
    String created_on;
    String updated_on;
    String narritive;
    String recorded_by;
    String payer_number = "";

    public Double getSafaricom_balance() {
        return safaricom_balance;
    }

    public void setSafaricom_balance(Double safaricom_balance) {
        this.safaricom_balance = safaricom_balance;
    }

    public String getPayer_number() {
        return payer_number;
    }

    public void setPayer_number(String payer_number) {
        this.payer_number = payer_number;
    }

    public String getRecorded_by() {
        return recorded_by;
    }

    public void setRecorded_by(String recorded_by) {
        this.recorded_by = recorded_by;
    }
    
    

    public String getNarritive() {
        return narritive;
    }

    public void setNarritive(String narritive) {
        this.narritive = narritive;
    }

    public Double getSms_balance() {
        return sms_balance;
    }

    public void setSms_balance(Double sms_balance) {
        this.sms_balance = sms_balance;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
    
    public long getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(long merchant_id) {
        this.merchant_id = merchant_id;
    }

    public long getTransactions_log_id() {
        return transactions_log_id;
    }

    public void setTransactions_log_id(long transactions_log_id) {
        this.transactions_log_id = transactions_log_id;
    }

    public String getGateway_id() {
        return gateway_id;
    }

    public void setGateway_id(String gateway_id) {
        this.gateway_id = gateway_id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getMtnmm_balance() {
        return mtnmm_balance;
    }

    public void setMtnmm_balance(Double mtnmm_balance) {
        this.mtnmm_balance = mtnmm_balance;
    }

    public Double getAirtelmm_balance() {
        return airtelmm_balance;
    }

    public void setAirtelmm_balance(Double airtelmm_balance) {
        this.airtelmm_balance = airtelmm_balance;
    }

    public String getTx_type() {
        return tx_type;
    }

    public void setTx_type(String tx_type) {
        this.tx_type = tx_type;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public String getUpdated_on() {
        return updated_on;
    }

    public void setUpdated_on(String updated_on) {
        this.updated_on = updated_on;
    }
    
    public String toString() {
        return this.tx_type+": "+this.narritive+" : "+this.description+" : "+this.gateway_id
                +" : "+this.updated_on+" : "+this.amount+" : "+this.safaricom_balance;
    }
    
}
