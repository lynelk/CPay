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
public class GatewayChargeDetails {
    String customerChargeMethod;
    
    String customerInboundChargeMethod;
    String customerOutboundChargeMethod;
    Double customerInboundCharge;
    Double customerOutboundCharge;
    
    String costOfPayInMethod;
    String costOfPayOutMethod;
    Double costOfInboundPayment;
    Double costOfOutboundPayment;

    public String getCustomerInboundChargeMethod() {
        return customerInboundChargeMethod;
    }

    public void setCustomerInboundChargeMethod(String customerInboundChargeMethod) {
        this.customerInboundChargeMethod = customerInboundChargeMethod;
    }

    public String getCustomerOutboundChargeMethod() {
        return customerOutboundChargeMethod;
    }

    public void setCustomerOutboundChargeMethod(String customerOutboundChargeMethod) {
        this.customerOutboundChargeMethod = customerOutboundChargeMethod;
    }

    public Double getCustomerInboundCharge() {
        return customerInboundCharge;
    }

    public void setCustomerInboundCharge(Double customerInboundCharge) {
        this.customerInboundCharge = customerInboundCharge;
    }

    public Double getCustomerOutboundCharge() {
        return customerOutboundCharge;
    }

    public void setCustomerOutboundCharge(Double customerOutboundCharge) {
        this.customerOutboundCharge = customerOutboundCharge;
    }
    
    

    public String getCustomerChargeMethod() {
        return customerChargeMethod;
    }

    public void setCustomerChargeMethod(String customerChargeMethod) {
        this.customerChargeMethod = customerChargeMethod;
    }

    public String getCostOfPayInMethod() {
        return costOfPayInMethod;
    }

    public void setCostOfPayInMethod(String costOfPayInMethod) {
        this.costOfPayInMethod = costOfPayInMethod;
    }

    public String getCostOfPayOutMethod() {
        return costOfPayOutMethod;
    }

    public void setCostOfPayOutMethod(String costOfPayOutMethod) {
        this.costOfPayOutMethod = costOfPayOutMethod;
    }

    public Double getCostOfInboundPayment() {
        return costOfInboundPayment;
    }

    public void setCostOfInboundPayment(Double costOfInboundPayment) {
        this.costOfInboundPayment = costOfInboundPayment;
    }

    public Double getCostOfOutboundPayment() {
        return costOfOutboundPayment;
    }

    public void setCostOfOutboundPayment(Double costOfOutboundPayment) {
        this.costOfOutboundPayment = costOfOutboundPayment;
    }
    
    
}
