/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author josephtabajjwa
 */
public class HttpRequestResponse {
    int statusCode;
    String response;
    String requestData = "";
    String url;
    Map<String, String> requestHeaders;
    Map<String, String> responseHeaders;
    String errorMessage;

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }
    
    

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(Map<String, String> requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public Map<String, String> getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(Map<String, String> responseHeaders) {
        this.responseHeaders = responseHeaders;
    }
    
    public String toString() {
        String request_headers = "";
        String response_headers = "";
        if (requestHeaders != null) {
            for (Map.Entry<String, String> h : this.getRequestHeaders().entrySet()) {
                request_headers += h.getKey()+": "+h.getValue()+"\n";
            }
        }
        
        if (responseHeaders != null) {
            for (Map.Entry<String, String> h_ : this.getResponseHeaders().entrySet()) {
                response_headers += h_.getKey()+": "+h_.getValue()+"\n";
            }
        }
        
        return "\n****URL****\n"+this.getUrl()+"\n\n"
                + "****Status****\n"+this.getStatusCode()+"\n\n"
                + "****Request Headers****\n"+request_headers+"\n\n"
                + "****Request Data****\n"+this.getRequestData()+"\n\n"
                + "****Respoonse Headers****\n"+response_headers+"\n\n"
                + "****Respones Data****\n"+this.getResponse()+"\n\n"
                + "****Error Message****\n"+this.getErrorMessage()+"\n\n";
    }
    
    
    
    public class Header {
        String name;
        String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        public String getValue( ) {
            return this.name;
        }
        public String getName() {
            return this.name;
        }
    }
    
}
