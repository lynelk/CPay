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
public class GateWayResponse {
    String status = "";
    String transactionStatus = "";
    String message = "";
    String httpStatus = "";
    String requestTrace = "";
    String networkId = "";
    String ourUniqueTxId = "";
    String safaricomRequestReference = "";

    public String getSafaricomRequestReference() {
        return safaricomRequestReference;
    }

    public void setSafaricomRequestReference(String safaricomRequestReference) {
        this.safaricomRequestReference = safaricomRequestReference;
    }

    public String getOurUniqueTxId() {
        return ourUniqueTxId;
    }

    public void setOurUniqueTxId(String ourUniqueTxId) {
        this.ourUniqueTxId = ourUniqueTxId;
    }
    

    public String getNetworkId() {
        return networkId;
    }

    public void setNetworkId(String networkId) {
        this.networkId = networkId;
    }

    public String getRequestTrace() {
        return requestTrace;
    }

    public void setRequestTrace(String requestTrace) {
        this.requestTrace = requestTrace;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    public void setTransactionStatus(String transactionStatus) {
        this.transactionStatus = transactionStatus;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(String httpStatus) {
        this.httpStatus = httpStatus;
    }
    
    public String toString() {
        return status+": "+httpStatus+" "+ message +" "+transactionStatus;
    }
}
