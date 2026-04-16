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
public class MerchantUser extends User implements java.io.Serializable {
    String merchant_number;
    String merchant_status;
    String merchant_name;
    String merchant_account_type;
    Long merchant_id;

    public String getMerchant_number() {
        return merchant_number;
    }

    public void setMerchant_number(String merchant_number) {
        this.merchant_number = merchant_number;
    }

    public String getMerchant_status() {
        return merchant_status;
    }

    public void setMerchant_status(String merchant_status) {
        this.merchant_status = merchant_status;
    }

    public String getMerchant_name() {
        return merchant_name;
    }

    public void setMerchant_name(String merchant_name) {
        this.merchant_name = merchant_name;
    }

    public String getMerchant_account_type() {
        return merchant_account_type;
    }

    public void setMerchant_account_type(String merchant_account_type) {
        this.merchant_account_type = merchant_account_type;
    }

    public Long getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(Long merchant_id) {
        this.merchant_id = merchant_id;
    }
    
    
}
