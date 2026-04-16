/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import java.net.MalformedURLException;
import net.citotech.cito.AuthenticationController;
import static org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.xml;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 *
 * @author josephtabajjwa
 */
public class AirtelMoneyPaymentGateway extends PaymentGateway{
    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    String global_url = "";
    String api_username = "";
    String api_password = "";
    static public String BALANCE_TYPE = "airtelmm_balance";
   
    String base_currency = "UGX";
   
    public static String[] prefix = {"25675", "25670", "25676"};
    
    public static String gateway_id = "AirtelMoneyPaymentGateway";
    
    public static String gateway_currency_code = "AIRTELMM";
    
    String segment = "collection";//disbursement";
    
    
    static public String getGatewayCurrencyCode() {
        return gateway_currency_code;
    }
    
    static public String getGatewayId() {
        return gateway_id;
    }

    
    public static boolean isValidMisdn(String msisdn) {
        for (int i=0; i <  prefix.length; i++) {
            String line = msisdn;
            String pattern = "^"+prefix[i]+"";
            Pattern r = Pattern.compile(pattern);
            Matcher m = r.matcher(line);
            if (m.find( )) {
                return true;
            }
        }
        return false;
    }
    
    public void setApiDetails(String global_url, String api_username, 
            String api_password) {
        
        this.global_url = global_url;
        this.api_username = api_username;
        this.api_password = api_password;
        this.base_currency = base_currency;
        
    }
    
    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }
    
    
    @Override
    public AccountInfo getAccountInfo(String msisdn) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<AutoCreate><Request>";
        xml += "<APIUsername>"+this.api_username+"</APIUsername>";
        xml += "<APIPassword>"+this.api_password+"</APIPassword>";
        xml += "<Method>acgetmsisdnkycinfo</Method>";
        xml += "<Account>"+msisdn+"</Account>";
        xml += "</Request></AutoCreate>";
       
        this.xml_sent = xml;
       
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml");
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", this.global_url, xml, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(this.global_url+""+headers.toString()+""+xml);
                
                return null;
            }
            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                gwResponse.setMessage("Response Data: "+rs.getResponse());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return null;
            } else {
                //Now passthe response
                this.xml_returned = rs.getResponse();
                YoPaymentsResponse yo = new YoPaymentsResponse(this.xml_returned);
                yo.parse();
                
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage(yo.StatusMessage);
                gwResponse.setStatus(yo.Status);
                String tx_status = "";
                if (yo.TransactionStatus.equals("SUCCEEDED")) {
                    tx_status = "SUCCESSFUL";
                    AccountInfo mInfo = new AccountInfo(
                            yo.FirstName,
                            yo.LastName,
                            ""
                    );
                    
                    return mInfo;
                } else {
                    return null;
                }
            }
            
       } catch (Exception ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("FAILED");
            gwResponse.setRequestTrace("Request failed to external system failed.");
            return null;
       }
    }
    
    
    @Override
    public Double getBalance(String account) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<AutoCreate><Request>";
        xml += "<APIUsername>"+this.api_username+"</APIUsername>";
        xml += "<APIPassword>"+this.api_password+"</APIPassword>";
        xml += "<Method>acgetmsisdnkycinfo</Method>";
        xml += "<Account>"+account+"</Account>";
        xml += "</Request></AutoCreate>";
       
        this.xml_sent = xml;
       
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml");
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", this.global_url, xml, headers);
            
            if (rs == null) {
                
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(this.global_url+""+headers.toString()+""+xml);
                
                Logger.getLogger(AirtelMoneyPaymentGateway.class.getName()).log(Level.SEVERE, 
                        gwResponse.getRequestTrace(), "");
            
                return 0.0;
            }
            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                gwResponse.setMessage("Response Data: "+rs.getResponse());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                Logger.getLogger(AirtelMoneyPaymentGateway.class.getName()).log(Level.SEVERE, 
                        gwResponse.getRequestTrace(), "");
                return 0.0;
            } else {
                
                //Now passthe response
                this.xml_returned = rs.getResponse();
                YoPaymentsResponse yo = new YoPaymentsResponse(this.xml_returned);
                yo.parse();
                
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage(yo.StatusMessage);
                gwResponse.setStatus(yo.Status);
                //String tx_status = "";
                if (yo.TransactionStatus.equals("SUCCEEDED")) {
                    //tx_status = "SUCCESSFUL";
                    
                    if (!yo.Balance.isEmpty()) {
                        /*Logger.getLogger(AirtelMoneyPaymentGateway.class.getName()).log(Level.SEVERE, 
                        rs.toString(), "");*/
                        String balString = yo.Balance.replace(",", "");
                        double dBalance = Double.parseDouble(balString);
                        return dBalance;
                    }
                    return 0.0;
                } else {
                    return 0.0;
                }
            }
            
       } catch (Exception ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("FAILED");
            gwResponse.setRequestTrace("Request failed to external system failed.");
            return 0.0;
       } //To change body of generated methods, choose Tools | Templates.
    }
    

    @Override
    public GateWayResponse doPayOut(Double amount, String payee, String ref, String narrative) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<AutoCreate><Request>";
        xml += "<APIUsername>"+this.api_username+"</APIUsername>";
        xml += "<APIPassword>"+this.api_password+"</APIPassword>";
        xml += "<Method>acwithdrawfunds</Method>";
        xml += "<Amount>"+amount+"</Amount>";
        xml += "<Account>"+payee+"</Account>";
        xml += "<Narrative>"+narrative+"</Narrative>";
        xml += "<ExternalReference>"+ref+"</ExternalReference>";
        xml += "</Request></AutoCreate>";
       
        this.xml_sent = xml;
       
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml");
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", this.global_url, xml, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus("0");
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(this.global_url+""+headers.toString()+""+xml);
                
                return gwResponse;
            }
            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                gwResponse.setMessage("Response Data: "+rs.getResponse());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                //Now passthe response
                this.xml_returned = rs.getResponse();
                YoPaymentsResponse yo = new YoPaymentsResponse(this.xml_returned);
                yo.parse();
                
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage(yo.StatusMessage);
                gwResponse.setStatus(yo.Status);
                String tx_status = "";
                if (yo.TransactionStatus.equals("SUCCEEDED")) {
                    tx_status = "SUCCESSFUL";
                } else if (yo.TransactionStatus.equals("FAILED")) {
                    tx_status = "FAILED";
                } else if (yo.TransactionStatus.equals("PENDING")) {
                    tx_status = "PENDING";
                } else if (yo.TransactionStatus.equals("INDETERMINATE")) {
                    tx_status = "UNDETERMINED";
                } else {
                    tx_status = "UNDETERMINED";
                }
                Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "AIRTEL MONEY YO RESPONSE: "+yo.toString(), "");
                gwResponse.setTransactionStatus(tx_status);
                gwResponse.setNetworkId(yo.MNOTransactionReferenceId);
                gwResponse.setOurUniqueTxId(ref);
                gwResponse.setRequestTrace(rs.toString()+". \n\nYo! "+yo.toString());
                return gwResponse;
            }
            
       } catch (Exception ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("FAILED");
            gwResponse.setRequestTrace("Request failed to external system failed.");
            return gwResponse;
       }
    }
    

    @Override
    public GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<AutoCreate><Request>";
        xml += "<APIUsername>"+this.api_username+"</APIUsername>";
        xml += "<APIPassword>"+this.api_password+"</APIPassword>";
        xml += "<Method>acdepositfunds</Method>";
        xml += "<NonBlocking>TRUE</NonBlocking>";
        xml += "<Amount>"+amount+"</Amount>";
        xml += "<Account>"+payer+"</Account>";
        xml += "<Narrative>"+narrative+"</Narrative>";
        xml += "<ExternalReference>"+ref+"</ExternalReference>";
        xml += "</Request></AutoCreate>";
       
        this.xml_sent = xml;
       
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml");
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", this.global_url, xml, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus(null);
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("FAILED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(this.global_url+""+headers.toString()+""+xml);
                
                return gwResponse;
            }
            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                gwResponse.setMessage("Response Data: "+rs.getResponse());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                //Now passthe response
                this.xml_returned = rs.getResponse();
                YoPaymentsResponse yo = new YoPaymentsResponse(this.xml_returned);
                yo.parse();
                
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage(yo.StatusMessage);
                gwResponse.setStatus(yo.Status);
                String tx_status = "";
                if (yo.TransactionStatus.equals("SUCCEEDED")) {
                    tx_status = "SUCCESSFUL";
                } else if (yo.TransactionStatus.equals("FAILED")) {
                    tx_status = "FAILED";
                } else if (yo.TransactionStatus.equals("PENDING")) {
                    tx_status = "PENDING";
                } else if (yo.TransactionStatus.equals("INDETERMINATE")) {
                    tx_status = "UNDETERMINED";
                } else {
                    tx_status = "UNDETERMINED";
                }
                
                gwResponse.setTransactionStatus(tx_status);
                gwResponse.setNetworkId(yo.MNOTransactionReferenceId);
                gwResponse.setOurUniqueTxId(ref);
                gwResponse.setRequestTrace(rs.toString());
                
                
                return gwResponse;
            }
            
       } catch (Exception ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus("0");
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("INTERNAL ERROR");
            return gwResponse;
       }
    }

    @Override
    public Double getBalance() { 
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    class YoPaymentsResponse {
        public String Status = "";
        public String StatusMessage = "";
        public String StatusCode = "";
        public String ErrorMessage = "";
        public String TransactionStatus = "";
        public String TransactionReference = "";
        public String MNOTransactionReferenceId = "";
        public String xmlData = "";
        public String Balance = "";
        public String FirstName = "";
        public String LastName = "";

        public YoPaymentsResponse(String xmlData) {
            this.xmlData = xmlData;
        }
        
        public String toString() {
            String r = "Status: "+this.Status+"\n"
                    + "Status message: "+this.StatusMessage+"\n"
                    + "Status Code: "+this.StatusCode+"\n"
                    + "Error Message: "+this.ErrorMessage+"\n"
                    + "Transaction Status: "+this.TransactionStatus+"\n"
                    + "Transaction Reference: "+this.TransactionReference+"\n"
                    + "Transaction Balance: "+this.Balance+"\n"
                    + "Transaction FirstName: "+this.FirstName+"\n"
                    + "Transaction LastName: "+this.LastName+"\n"
                    + "MNOTransactionReferenceId: "+this.MNOTransactionReferenceId+"\n";
            return r;
        }
        
        void parse() {
            try {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(new InputSource(new StringReader(this.xmlData)));
                doc.getDocumentElement().normalize();
                NodeList nList = doc.getElementsByTagName("Response");
                
                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node nNode = nList.item(temp);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        if (eElement.getElementsByTagName("Status").getLength() > 0) {
                            this.Status = eElement
                              .getElementsByTagName("Status")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.Status = "";
                        }
                        if (eElement.getElementsByTagName("StatusCode").getLength() > 0) {
                            this.StatusCode = eElement
                              .getElementsByTagName("StatusCode")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.StatusCode = "";
                        }
                        if (eElement.getElementsByTagName("StatusMessage").getLength() > 0) {
                            this.StatusMessage = eElement
                              .getElementsByTagName("StatusMessage")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.StatusMessage = "";
                        }
                        
                        if (eElement.getElementsByTagName("TransactionStatus").getLength() > 0) {
                            this.TransactionStatus = eElement
                              .getElementsByTagName("TransactionStatus")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.TransactionStatus = "";  
                        }
                        if (eElement.getElementsByTagName("TransactionReference").getLength() > 0) {
                            this.TransactionReference = eElement
                              .getElementsByTagName("TransactionReference")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.TransactionReference = "";
                        }
                        if (eElement.getElementsByTagName("MNOTransactionReferenceId").getLength() > 0) {
                            this.MNOTransactionReferenceId = eElement
                              .getElementsByTagName("MNOTransactionReferenceId")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.MNOTransactionReferenceId = "";
                        }
                        
                        if (eElement.getElementsByTagName("ErrorMessage").getLength() > 0) {
                            this.ErrorMessage = eElement
                              .getElementsByTagName("ErrorMessage")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.ErrorMessage = "";
                        }
                        
                        if (eElement.getElementsByTagName("Balance").getLength() > 0) {
                            this.Balance = eElement
                              .getElementsByTagName("Balance")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.Balance = "";
                        }
                        
                        if (eElement.getElementsByTagName("FirstName").getLength() > 0) {
                            this.FirstName = eElement
                              .getElementsByTagName("FirstName")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.FirstName = "";
                        }
                        
                        if (eElement.getElementsByTagName("LastName").getLength() > 0) {
                            this.LastName = eElement
                              .getElementsByTagName("LastName")
                              .item(0)
                              .getTextContent();
                        } else {
                            this.LastName = "";
                        }
                    }
                }
                
            } catch (Exception ex) {
                this.ErrorMessage = ex.getMessage();
                this.TransactionStatus = "UNDETERMINED";
                this.Status = "ERROR";
            }
        }
    }
    

    @Override
    public GateWayResponse checkStatus(String ref) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<AutoCreate><Request>";
        xml += "<APIUsername>"+this.api_username+"</APIUsername>";
        xml += "<APIPassword>"+this.api_password+"</APIPassword>";
        xml += "<Method>actransactioncheckstatus</Method>";
        xml += "<PrivateTransactionReference>"+ref+"</PrivateTransactionReference>";
        xml += "</Request></AutoCreate>";
       
        this.xml_sent = xml;
       
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "text/xml");
            
            //Now generate the response.
            GateWayResponse gwResponse = new GateWayResponse();
            
            HttpRequestResponse rs = Common.doHttpRequest("POST", this.global_url, xml, headers);
            if (rs == null) {
                
                gwResponse.setHttpStatus(null);
                gwResponse.setMessage("HttpRequestResponse object is null.");
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setNetworkId("");
                gwResponse.setRequestTrace(this.global_url+""+headers.toString()+""+xml);
                
                return gwResponse;
            }
            if (rs.getStatusCode() != 200) {
                String error = rs.toString();
                Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, rs.toString(), error);
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                
                gwResponse.setMessage("Response Data: "+rs.getResponse());
                gwResponse.setStatus("ERROR");
                gwResponse.setTransactionStatus("UNDETERMINED");
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            } else {
                //Now passthe response
                this.xml_returned = rs.getResponse();
                YoPaymentsResponse yo = new YoPaymentsResponse(this.xml_returned);
                yo.parse();
                
                gwResponse.setHttpStatus(rs.getStatusCode()+"");
                gwResponse.setMessage(yo.StatusMessage);
                gwResponse.setStatus(yo.Status);
              
                String tx_status = "";
                if (yo.TransactionStatus.equals("SUCCEEDED")) {
                    tx_status = "SUCCESSFUL";
                } else if (yo.TransactionStatus.equals("FAILED")) {
                    tx_status = "FAILED";
                } else if (yo.TransactionStatus.equals("PENDING")) {
                    tx_status = "PENDING";
                } else if (yo.TransactionStatus.equals("INDETERMINATE")) {
                    tx_status = "UNDETERMINED";
                } else {
                    tx_status = "UNDETERMINED";
                }
              
                gwResponse.setTransactionStatus(tx_status);
                
                gwResponse.setNetworkId(yo.MNOTransactionReferenceId);
                gwResponse.setOurUniqueTxId(ref);
                gwResponse.setRequestTrace(rs.toString());
                return gwResponse;
            }
            
        } catch (Exception ex) {
            GateWayResponse gwResponse = new GateWayResponse();
            gwResponse.setHttpStatus(null);
            gwResponse.setMessage(ex.getMessage());
            gwResponse.setStatus("ERROR");
            gwResponse.setTransactionStatus("UNDETERMINED");
            gwResponse.setRequestTrace("ERROR");
            return gwResponse;
        }
    }

    
}
