/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.math.BigInteger;

/**
 *
 * @author josephtabajjwa
 */
public class MerchantSms {
    BigInteger id;
    BigInteger merchant_id;
    Double charge;
    Double cost;
    int total_recipients;
    String status;
    String trace;
    String content;
    String recipients;
    String gw_response;
    String smsgw;
    String created_on;
    String created_by;
    String send_time;
    Double total_amount;

    public Double getTotal_amount() {
        return total_amount;
    }

    public void setTotal_amount(Double total_amount) {
        this.total_amount = total_amount;
    }

    public String getSend_time() {
        return send_time;
    }

    public void setSend_time(String send_time) {
        this.send_time = send_time;
    }

    public String getCreated_by() {
        return created_by;
    }

    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public BigInteger getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(BigInteger merchant_id) {
        this.merchant_id = merchant_id;
    }

    public Double getCharge() {
        return charge;
    }

    public void setCharge(Double charge) {
        this.charge = charge;
    }

    public Double getCost() {
        return cost;
    }

    public void setCost(Double cost) {
        this.cost = cost;
    }

    public int getTotal_recipients() {
        return total_recipients;
    }

    public void setTotal_recipients(int total_recipients) {
        this.total_recipients = total_recipients;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public String getGw_response() {
        return gw_response;
    }

    public void setGw_response(String gw_response) {
        this.gw_response = gw_response;
    }

    public String getSmsgw() {
        return smsgw;
    }

    public void setSmsgw(String smsgw) {
        this.smsgw = smsgw;
    }
    
    
}
