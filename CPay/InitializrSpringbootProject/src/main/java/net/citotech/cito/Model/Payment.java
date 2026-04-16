/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.util.List;

/**
 *
 * @author josephtabajjwa
 */
public class Payment {
    long id;
    String name;
    long merchant_id;
    String paymentId;
    String description;
    double total_amount;
    double total_charges;
    String status;
    String created_on;
    String created_by;
    
    List<Beneficiary> beneficiaries;

    public String getCreated_by() {
        return created_by;
    }

    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }

    public long getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(long merchant_id) {
        this.merchant_id = merchant_id;
    }
    
    public String getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(String paymentId) {
        this.paymentId = paymentId;
    }
    
    

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getTotal_amount() {
        return total_amount;
    }

    public void setTotal_amount(double total_amount) {
        this.total_amount = total_amount;
    }

    public double getTotal_charges() {
        return total_charges;
    }

    public void setTotal_charges(double total_charges) {
        this.total_charges = total_charges;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public List<Beneficiary> getBeneficiaries() {
        return beneficiaries;
    }

    public void setBeneficiaries(List<Beneficiary> beneficiaries) {
        this.beneficiaries = beneficiaries;
    }
    
    public String toString() {
        return "ID: "+this.paymentId+", Name: "+this.name+", Desc: "+this.description;
    }
    
}
