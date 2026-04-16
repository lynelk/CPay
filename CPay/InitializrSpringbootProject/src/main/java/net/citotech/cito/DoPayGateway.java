/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.logging.Level;
import java.util.logging.Logger;

import net.citotech.cito.Model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 *
 * @author josephtabajjwa
 */
public class DoPayGateway {
    
    MTNMoMoPaymentGateway mtn_mmpgw;
    AirtelMoneyPaymentGateway airtelmm_mmpgw;
    AirtelMoneyOpenApiPaymentGateway airteloapimm_mmpgw;
    SafariComPaymentGateway safaricom_mmpgw;
    
    @Value( "${custom.gatewaystate}" )
    public static String gatewaystate;
    
    static String getGatewayIdByMsisdn(String msisdn) {
        
        if (MTNMoMoPaymentGateway.isValidMisdn(msisdn)) {
            return MTNMoMoPaymentGateway.gateway_id;
        }
        
        if (AirtelMoneyPaymentGateway.isValidMisdn(msisdn)) {
            return AirtelMoneyPaymentGateway.gateway_id;
        }
        
        if (AirtelMoneyOpenApiPaymentGateway.isValidMisdn(msisdn)) {
            return AirtelMoneyOpenApiPaymentGateway.gateway_id;
        }

        if (SafariComPaymentGateway.isValidMisdn(msisdn)) {
            return SafariComPaymentGateway.gateway_id;
        }
        
        //Check other supported gateways like Airtel
        return null;
    }
    
    static String getGatewayIdByMsisdn(String msisdn, NamedParameterJdbcTemplate jdbcTemplate) {
        
        if (MTNMoMoPaymentGateway.isValidMisdn(msisdn)) {
            return MTNMoMoPaymentGateway.gateway_id;
        }

        if (SafariComPaymentGateway.isValidMisdn(msisdn)) {
            return SafariComPaymentGateway.gateway_id;
        }
        
        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        use_open_api.trim();
        
        //Do another gateway.
        if (use_open_api.equals("yes")) {
            if (AirtelMoneyOpenApiPaymentGateway.isValidMisdn(msisdn)) {
                return AirtelMoneyOpenApiPaymentGateway.gateway_id;
            }
        } else {       
            if (AirtelMoneyPaymentGateway.isValidMisdn(msisdn)) {
                return AirtelMoneyPaymentGateway.gateway_id;
            }
        }
        
        //Check other supported gateways like Airtel
        return null;
    }
    
    
    
    static GatewayChargeDetails getGatewayChargeDetailsById(
            NamedParameterJdbcTemplate jdbcTemplate,
            String gateway_id) {
        if (MTNMoMoPaymentGateway.gateway_id.equals(gateway_id)) {
            GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
            Setting customerChargeMethod = Common.getSettings("gw_mtn_api_customer_charge_method", 
                    jdbcTemplate);
            
            Setting customerOfPayInMethod = Common.getSettings("gw_mtn_api_customer_charge_inbound_method", 
                    jdbcTemplate);
            Setting customerPayOutMethod = Common.getSettings("gw_mtn_api_customer_charge_outbound_method", 
                    jdbcTemplate);
            Setting customerInboundCharge = Common.getSettings("gw_mtn_api_customer_charge_inbound", 
                    jdbcTemplate);
            Setting customerOutboundCharge = Common.getSettings("gw_mtn_api_customer_charge_outbound", 
                    jdbcTemplate);
            
            
            gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
            gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
            gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
            gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));
            
            
            Setting costOfPayInMethod = Common.getSettings("gw_mtn_api_cost_of_inbound_payment_method", 
                    jdbcTemplate);
            Setting costOfPayOutMethod = Common.getSettings("gw_mtn_api_cost_of_outbound_payment_method", 
                    jdbcTemplate);
            Setting costOfInboundPayment = Common.getSettings("gw_mtn_api_cost_of_inbound_payment", 
                    jdbcTemplate);
            Setting costOfOutboundPayment = Common.getSettings("gw_mtn_api_cost_of_outbound_payment", 
                    jdbcTemplate);
            
            gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
            gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
            gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
            gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
            gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());
            
            
            return gwChargeDetails;
        }
        
        //Do another gateway.
       
        
        if (AirtelMoneyPaymentGateway.gateway_id.equals(gateway_id)
                || AirtelMoneyOpenApiPaymentGateway.gateway_id.equals(gateway_id)) {
            GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
            Setting customerChargeMethod = Common.getSettings("gw_airtelmoney_api_customer_charge_method", 
                    jdbcTemplate);

            Setting customerOfPayInMethod = Common.getSettings("gw_airtelmoney_api_customer_charge_inbound_method", 
                    jdbcTemplate);
            Setting customerPayOutMethod = Common.getSettings("gw_airtelmoney_api_customer_charge_outbound_method", 
                    jdbcTemplate);
            Setting customerInboundCharge = Common.getSettings("gw_airtelmoney_api_customer_charge_inbound", 
                    jdbcTemplate);
                Setting customerOutboundCharge = Common.getSettings("gw_airtelmoney_api_customer_charge_outbound", 
                    jdbcTemplate);


            gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
            gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
            gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
            gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));


            Setting costOfPayInMethod = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment_method", 
                    jdbcTemplate);
            Setting costOfPayOutMethod = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment_method", 
                    jdbcTemplate);
            Setting costOfInboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment", 
                    jdbcTemplate);
            Setting costOfOutboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment", 
                    jdbcTemplate);

            gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
            gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
            gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
            gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
            gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());


            return gwChargeDetails;
        }


        if (SafariComPaymentGateway.gateway_id.equals(gateway_id)) {
            GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
            Setting customerChargeMethod = Common.getSettings("gw_safaricom_api_customer_charge_method",
                    jdbcTemplate);

            Setting customerOfPayInMethod = Common.getSettings("gw_safaricom_api_customer_charge_inbound_method",
                    jdbcTemplate);
            Setting customerPayOutMethod = Common.getSettings("gw_safaricom_api_customer_charge_outbound_method",
                    jdbcTemplate);
            Setting customerInboundCharge = Common.getSettings("gw_safaricom_api_customer_charge_inbound",
                    jdbcTemplate);
            Setting customerOutboundCharge = Common.getSettings("gw_safaricom_api_customer_charge_outbound",
                    jdbcTemplate);


            gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
            gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
            gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
            gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));


            Setting costOfPayInMethod = Common.getSettings("gw_safaricom_api_cost_of_inbound_payment_method",
                    jdbcTemplate);
            Setting costOfPayOutMethod = Common.getSettings("gw_safaricom_api_cost_of_outbound_payment_method",
                    jdbcTemplate);
            Setting costOfInboundPayment = Common.getSettings("gw_safaricom_api_cost_of_inbound_payment",
                    jdbcTemplate);
            Setting costOfOutboundPayment = Common.getSettings("gw_safaricom_api_cost_of_outbound_payment",
                    jdbcTemplate);

            gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
            gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
            gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
            gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
            gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());


            return gwChargeDetails;
        }
        
        
        //Check other supported gateways like Airtel
        return null;
    }
    
    static Setting getMerchantSPecificSetting(String setting, 
            long merchant_id, 
            NamedParameterJdbcTemplate jdbcTemplate) {
        Setting s = Common.getMerchantSettings(setting, 
                merchant_id, 
                jdbcTemplate);
        if (s == null || s.getSetting_value().isEmpty()) {
            return null;
        } else {
            return s;
        }
    }
    
    static GatewayChargeDetails getGatewayChargeDetailsById(
            NamedParameterJdbcTemplate jdbcTemplate,
            String gateway_id,
            Long merchant_id) {
        if (MTNMoMoPaymentGateway.gateway_id.equals(gateway_id)) {
            GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
            Setting customerChargeMethod = 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_method", 
                        merchant_id, jdbcTemplate) == null ? 
                    Common.getSettings("gw_mtn_api_customer_charge_method", 
                        jdbcTemplate) 
                    : 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_method", 
                    merchant_id, jdbcTemplate);
            
            Setting customerOfPayInMethod = 
                    
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_inbound_method", 
                        merchant_id, jdbcTemplate) == null ? 
                    Common.getSettings("gw_mtn_api_customer_charge_inbound_method", 
                        jdbcTemplate) 
                    : 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_inbound_method", 
                    merchant_id, jdbcTemplate);
                    
                    
            Setting customerPayOutMethod = 
                    
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_outbound_method", 
                        merchant_id, jdbcTemplate) == null ? 
                    Common.getSettings("gw_mtn_api_customer_charge_outbound_method", 
                        jdbcTemplate) 
                    : 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_outbound_method", 
                    merchant_id, jdbcTemplate);
                    
                    
               
            Setting customerInboundCharge = 
                    
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_inbound", 
                        merchant_id, jdbcTemplate) == null ? 
                    Common.getSettings("gw_mtn_api_customer_charge_inbound", 
                        jdbcTemplate) 
                    : 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_inbound", 
                    merchant_id, jdbcTemplate);
                    
                    
            Setting customerOutboundCharge = 
                    
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_outbound", 
                        merchant_id, jdbcTemplate) == null ? 
                    Common.getSettings("gw_mtn_api_customer_charge_outbound", 
                        jdbcTemplate) 
                    : 
                    getMerchantSPecificSetting("gw_mtn_api_customer_charge_outbound", 
                    merchant_id, jdbcTemplate);
            
            
            gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
            gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
            gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
            gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));
            
            
            Setting costOfPayInMethod = Common.getSettings("gw_mtn_api_cost_of_inbound_payment_method", 
                    jdbcTemplate);
            Setting costOfPayOutMethod = Common.getSettings("gw_mtn_api_cost_of_outbound_payment_method", 
                    jdbcTemplate);
            Setting costOfInboundPayment = Common.getSettings("gw_mtn_api_cost_of_inbound_payment", 
                    jdbcTemplate);
            Setting costOfOutboundPayment = Common.getSettings("gw_mtn_api_cost_of_outbound_payment", 
                    jdbcTemplate);
            
            gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
            gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
            gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
            gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
            gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());
            
            
            return gwChargeDetails;
        }

        if (SafariComPaymentGateway.gateway_id.equals(gateway_id)) {
            GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
            Setting customerChargeMethod =
                    getMerchantSPecificSetting("gw_safaricom_api_customer_charge_method",
                            merchant_id, jdbcTemplate) == null ?
                            Common.getSettings("gw_safaricom_api_customer_charge_method",
                                    jdbcTemplate)
                            :
                            getMerchantSPecificSetting("gw_safaricom_api_customer_charge_method",
                                    merchant_id, jdbcTemplate);

            Setting customerOfPayInMethod =

                    getMerchantSPecificSetting("gw_safaricom_api_customer_charge_inbound_method",
                            merchant_id, jdbcTemplate) == null ?
                            Common.getSettings("gw_safaricom_api_customer_charge_inbound_method",
                                    jdbcTemplate)
                            :
                            getMerchantSPecificSetting("gw_safaricom_api_customer_charge_inbound_method",
                                    merchant_id, jdbcTemplate);


            Setting customerPayOutMethod =

                    getMerchantSPecificSetting("gw_safaricom_api_customer_charge_outbound_method",
                            merchant_id, jdbcTemplate) == null ?
                            Common.getSettings("gw_safaricom_api_customer_charge_outbound_method",
                                    jdbcTemplate)
                            :
                            getMerchantSPecificSetting("gw_safaricom_api_customer_charge_outbound_method",
                                    merchant_id, jdbcTemplate);



            Setting customerInboundCharge =

                    getMerchantSPecificSetting("gw_safaricom_api_customer_charge_inbound",
                            merchant_id, jdbcTemplate) == null ?
                            Common.getSettings("gw_safaricom_api_customer_charge_inbound",
                                    jdbcTemplate)
                            :
                            getMerchantSPecificSetting("gw_safaricom_api_customer_charge_inbound",
                                    merchant_id, jdbcTemplate);


            Setting customerOutboundCharge =

                    getMerchantSPecificSetting("gw_safaricom_api_customer_charge_outbound",
                            merchant_id, jdbcTemplate) == null ?
                            Common.getSettings("gw_safaricom_api_customer_charge_outbound",
                                    jdbcTemplate)
                            :
                            getMerchantSPecificSetting("gw_safaricom_api_customer_charge_outbound",
                                    merchant_id, jdbcTemplate);




            gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
            gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
            gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
            gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));


            Setting costOfPayInMethod = Common.getSettings("gw_safaricom_api_cost_of_inbound_payment_method",
                    jdbcTemplate);
            Setting costOfPayOutMethod = Common.getSettings("gw_safaricom_api_cost_of_outbound_payment_method",
                    jdbcTemplate);
            Setting costOfInboundPayment = Common.getSettings("gw_safaricom_api_cost_of_inbound_payment",
                    jdbcTemplate);
            Setting costOfOutboundPayment = Common.getSettings("gw_safaricom_api_cost_of_outbound_payment",
                    jdbcTemplate);

            gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
            gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
            gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
            gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
            gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());


            return gwChargeDetails;
        }
        
        
        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        
        //Do another gateway.

            if (AirtelMoneyOpenApiPaymentGateway.gateway_id.equals(gateway_id)) {
                GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
                Setting customerChargeMethod = 

                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_method", 
                            merchant_id, jdbcTemplate) == null ? 
                        Common.getSettings("gw_airtelmoney_api_customer_charge_method", 
                            jdbcTemplate) 
                        : 
                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_method", 
                        merchant_id, jdbcTemplate);

                Setting customerOfPayInMethod = 


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound_method", 
                            merchant_id, jdbcTemplate) == null ? 
                        Common.getSettings("gw_airtelmoney_api_customer_charge_inbound_method", 
                            jdbcTemplate) 
                        : 
                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound_method", 
                        merchant_id, jdbcTemplate);



                Setting customerPayOutMethod = 


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound_method", 
                            merchant_id, jdbcTemplate) == null ? 
                        Common.getSettings("gw_airtelmoney_api_customer_charge_outbound_method", 
                            jdbcTemplate) 
                        : 
                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound_method", 
                        merchant_id, jdbcTemplate);



                Setting customerInboundCharge = 


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound", 
                            merchant_id, jdbcTemplate) == null ? 
                        Common.getSettings("gw_airtelmoney_api_customer_charge_inbound", 
                            jdbcTemplate) 
                        : 
                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound", 
                        merchant_id, jdbcTemplate);



                    Setting customerOutboundCharge = 


                            getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound", 
                            merchant_id, jdbcTemplate) == null ? 
                        Common.getSettings("gw_airtelmoney_api_customer_charge_outbound", 
                            jdbcTemplate) 
                        : 
                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound", 
                        merchant_id, jdbcTemplate);

                gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
                gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
                gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
                gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));


                Setting costOfPayInMethod = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment_method", 
                        jdbcTemplate);
                Setting costOfPayOutMethod = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment_method", 
                        jdbcTemplate);
                Setting costOfInboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment", 
                        jdbcTemplate);
                Setting costOfOutboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment", 
                        jdbcTemplate);

                gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
                gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
                gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
                gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
                gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());


                return gwChargeDetails;
            }

            if (AirtelMoneyPaymentGateway.gateway_id.equals(gateway_id)) {
                GatewayChargeDetails gwChargeDetails = new GatewayChargeDetails();
                Setting customerChargeMethod =

                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_method",
                                merchant_id, jdbcTemplate) == null ?
                                Common.getSettings("gw_airtelmoney_api_customer_charge_method",
                                        jdbcTemplate)
                                :
                                getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_method",
                                        merchant_id, jdbcTemplate);



                Setting customerOfPayInMethod =


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound_method",
                                merchant_id, jdbcTemplate) == null ?
                                Common.getSettings("gw_airtelmoney_api_customer_charge_inbound_method",
                                        jdbcTemplate)
                                :
                                getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound_method",
                                        merchant_id, jdbcTemplate);



                Setting customerPayOutMethod =


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound_method",
                                merchant_id, jdbcTemplate) == null ?
                                Common.getSettings("gw_airtelmoney_api_customer_charge_outbound_method",
                                        jdbcTemplate)
                                :
                                getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound_method",
                                        merchant_id, jdbcTemplate);



                Setting customerInboundCharge =


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound",
                                merchant_id, jdbcTemplate) == null ?
                                Common.getSettings("gw_airtelmoney_api_customer_charge_inbound",
                                        jdbcTemplate)
                                :
                                getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_inbound",
                                        merchant_id, jdbcTemplate);



                Setting customerOutboundCharge =


                        getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound",
                                merchant_id, jdbcTemplate) == null ?
                                Common.getSettings("gw_airtelmoney_api_customer_charge_outbound",
                                        jdbcTemplate)
                                :
                                getMerchantSPecificSetting("gw_airtelmoney_api_customer_charge_outbound",
                                        merchant_id, jdbcTemplate);



                gwChargeDetails.setCustomerInboundChargeMethod(customerOfPayInMethod.getSetting_value());
                gwChargeDetails.setCustomerInboundCharge(Double.parseDouble(customerInboundCharge.getSetting_value()));
                gwChargeDetails.setCustomerOutboundChargeMethod(customerPayOutMethod.getSetting_value());
                gwChargeDetails.setCustomerOutboundCharge(Double.parseDouble(customerOutboundCharge.getSetting_value()));


                Setting costOfPayInMethod = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment_method",
                        jdbcTemplate);
                Setting costOfPayOutMethod = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment_method",
                        jdbcTemplate);
                Setting costOfInboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_inbound_payment",
                        jdbcTemplate);
                Setting costOfOutboundPayment = Common.getSettings("gw_airtelmoney_api_cost_of_outbound_payment",
                        jdbcTemplate);

                gwChargeDetails.setCostOfPayInMethod(costOfPayInMethod.getSetting_value());
                gwChargeDetails.setCostOfPayOutMethod(costOfPayOutMethod.getSetting_value());
                gwChargeDetails.setCostOfInboundPayment(Double.parseDouble(costOfInboundPayment.getSetting_value()));
                gwChargeDetails.setCostOfOutboundPayment(Double.parseDouble(costOfOutboundPayment.getSetting_value()));
                gwChargeDetails.setCustomerChargeMethod(customerChargeMethod.getSetting_value());


                return gwChargeDetails;
            }

        //Check other supported gateways like Airtel
        return null;
    }
    
    static public Double getCustomerInboundCharges(Double amount, GatewayChargeDetails chargeDetails) {
        if (chargeDetails.getCustomerInboundChargeMethod().equals("percentage")) {
            Double r = ((chargeDetails.getCustomerInboundCharge()/100) * amount);
            return r;
        } else if (chargeDetails.getCustomerInboundChargeMethod().equals("flat")) {
            return chargeDetails.getCustomerInboundCharge();
        } else {
            return 0.00;
        }
    }
    
    static public Double getCustomerOutboundCharges(Double amount, GatewayChargeDetails chargeDetails) {
        if (chargeDetails.getCustomerOutboundChargeMethod().equals("percentage")) {
            Double r = ((chargeDetails.getCustomerOutboundCharge()/100) * amount);
            return r;
        } else if (chargeDetails.getCustomerOutboundChargeMethod().equals("flat")) {
            return chargeDetails.getCustomerOutboundCharge();
        } else {
            return 0.00;
        }
    }
    
    static public Double getCostOfInboundCharges(Double amount, GatewayChargeDetails chargeDetails) {
        if (chargeDetails.getCostOfPayInMethod().equals("percentage")) {
            Double r = ((chargeDetails.getCostOfInboundPayment()/100) * amount);
            return r;
        } else if (chargeDetails.getCostOfPayInMethod().equals("flat")) {
            return chargeDetails.getCostOfInboundPayment();
        } else {
            return 0.00;
        }
    }
    
    static public Double getCostOfOutboundCharges(Double amount, GatewayChargeDetails chargeDetails) {
        if (chargeDetails.getCostOfPayOutMethod().equals("percentage")) {
            Double r = ((chargeDetails.getCostOfOutboundPayment()/100) * amount);
            return r;
        } else if (chargeDetails.getCostOfPayOutMethod().equals("flat")) {
            return chargeDetails.getCostOfOutboundPayment();
        } else {
            return 0.00;
        }
    }
    
    static public Double getCustomertOfOutboundCharges(Double amount, GatewayChargeDetails chargeDetails) {
        if (chargeDetails.getCostOfPayOutMethod().equals("percentage")) {
            Double r = ((chargeDetails.getCostOfOutboundPayment()/100) * amount);
            return r;
        } else if (chargeDetails.getCostOfPayOutMethod().equals("flat")) {
            return chargeDetails.getCostOfOutboundPayment();
        } else {
            return 0.00;
        }
    }
    
    public GateWayResponse runPayGatewayDoPayIn( 
            NamedParameterJdbcTemplate jdbcTemplate,
            String msisdn,
            Double amount, 
            String ref,
            String narrative) {
        
        //First check if this is a test.
        String state = Common.getSettings("application_settings_state", jdbcTemplate) == null ?
                "production" : 
                Common.getSettings("application_settings_state", jdbcTemplate)
                    .getSetting_value();
        if (state.toLowerCase().equals("sandbox")) {
            return sandboxRunPayGatewayDoPayIn(msisdn,
                    amount, 
                    ref,
                    narrative);
        }
        
        //Select the gateway
        if (MTNMoMoPaymentGateway.isValidMisdn(msisdn)) {
            
            Setting env = Common.getSettings("gw_mtn_api_env", jdbcTemplate);
            String global_url = "";
            String api_collections_user = "";
            String api_collections_key = "";
            String api_collections_subscription = "";

            String api_disbursements_user = "";
            String api_disbursements_key = "";
            String api_disbursements_subscription = "";
            String base_currency = "";
            
            if (env.getSetting_value().equals("mtnuganda")) {
                global_url = Common.getSettings("gw_mtn_api_url", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key", jdbcTemplate).getSetting_value();
                base_currency = Common.getSettings("gw_mtn_api_base_currency", jdbcTemplate).getSetting_value();
            } else {
                global_url = Common.getSettings("gw_mtn_api_url_sandbox", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key_sandbox", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key_sandbox", jdbcTemplate).getSetting_value();
                base_currency = Common.getSettings("gw_mtn_api_base_currency_sandbox", jdbcTemplate).getSetting_value();
            }
            mtn_mmpgw = new MTNMoMoPaymentGateway();
            mtn_mmpgw.setSegment("collection");
            mtn_mmpgw.setApiDetails(global_url, api_collections_user, 
            api_collections_key, api_collections_subscription,
            api_disbursements_user, api_disbursements_key,
            api_disbursements_subscription, env.getSetting_value(),
            base_currency);
            
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "API User Details: "+api_collections_user+" "+api_collections_key, "");
            
            GateWayResponse pResponse = mtn_mmpgw.doPayIn(amount, msisdn, ref, narrative); 
            return pResponse;
        }

        if (SafariComPaymentGateway.isValidMisdn(msisdn)) {

            Setting env = Common.getSettings("gw_safaricom_api_env", jdbcTemplate);
            String global_url = "";
            String gw_safaricom_api_shortcode = "";
            String gw_safaricom_api_password = "";
            String gw_safaricom_api_consumer_key = "";
            String gw_safaricom_api_consumer_secret = "";
            String app_setting_app_url = Common.getSettings("app_setting_app_url", jdbcTemplate).getSetting_value();


            if (env.getSetting_value().equals("sandbox")) {
                global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
                gw_safaricom_api_shortcode = Common.getSettings("gw_safaricom_api_shortcode", jdbcTemplate).getSetting_value();
                gw_safaricom_api_password = Common.getSettings("gw_safaricom_api_password", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_key = Common.getSettings("gw_safaricom_api_consumer_key", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_secret = Common.getSettings("gw_safaricom_api_consumer_secret", jdbcTemplate).getSetting_value();
            } else {
                global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
                gw_safaricom_api_shortcode = Common.getSettings("gw_safaricom_api_shortcode", jdbcTemplate).getSetting_value();
                gw_safaricom_api_password = Common.getSettings("gw_safaricom_api_password", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_key = Common.getSettings("gw_safaricom_api_consumer_key", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_secret = Common.getSettings("gw_safaricom_api_consumer_secret", jdbcTemplate).getSetting_value();
            }
            safaricom_mmpgw = new SafariComPaymentGateway();
            safaricom_mmpgw.setSegment("collection");
            safaricom_mmpgw.setApiDetails(global_url, gw_safaricom_api_consumer_key,
                    gw_safaricom_api_consumer_secret,
                    gw_safaricom_api_shortcode,
                    gw_safaricom_api_password,
                    env.getSetting_value(),
                    app_setting_app_url);

            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "API User Details: "+gw_safaricom_api_consumer_key+" "+gw_safaricom_api_consumer_secret, "");

            GateWayResponse pResponse = safaricom_mmpgw.doPayIn(amount, msisdn, ref, narrative);
            return pResponse;
        }
        
        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        
        
        //Do another gateway.
        if (use_open_api.equals("yes")) {
            if (AirtelMoneyOpenApiPaymentGateway.isValidMisdn(msisdn)) {

                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

                String api_pin = Common.getSettings("gw_airtelmoney_api_pin", jdbcTemplate)
                    .getSetting_value();

                airteloapimm_mmpgw = new AirtelMoneyOpenApiPaymentGateway();
                airteloapimm_mmpgw.setApiDetails(global_url, api_username, api_password, api_pin);

                GateWayResponse pResponse = airteloapimm_mmpgw.doPayIn(amount, msisdn, ref, narrative); 
                return pResponse;    
            }
        } else {
            if (AirtelMoneyPaymentGateway.isValidMisdn(msisdn)) {

                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();



                airtelmm_mmpgw = new AirtelMoneyPaymentGateway();
                airtelmm_mmpgw.setApiDetails(global_url, api_username, api_password);

                GateWayResponse pResponse = airtelmm_mmpgw.doPayIn(amount, msisdn, ref, narrative); 
                return pResponse;    
            }
        }
        return null;
    }
    
    public double[] runPayGatewayNetworkBalances( 
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        double[] rData = {0.0,0.0,0.0,0.0};
        
        //First check if this is a test.
        String state = Common.getSettings("application_settings_state", jdbcTemplate) == null ?
                "production" : 
                Common.getSettings("application_settings_state", jdbcTemplate)
                    .getSetting_value();
        
        if (state.toLowerCase().equals("sandbox")) {
            rData[0] = 200000.0;
            rData[1] = 3500000.0;
            rData[2] = 1000000.0;
            rData[3] = 4000000.0;
            
            return rData;
        }
        
        //Select the gateway
        //Get MTN balances
            
        Setting env = Common.getSettings("gw_mtn_api_env", jdbcTemplate);
        String global_url = "";
        String api_collections_user = "";
        String api_collections_key = "";
        String api_collections_subscription = "";

        String api_disbursements_user = "";
        String api_disbursements_key = "";
        String api_disbursements_subscription = "";
        String base_currency = "";

        if (env.getSetting_value().equals("mtnuganda")) {
            global_url = Common.getSettings("gw_mtn_api_url", jdbcTemplate).getSetting_value();
            api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id", jdbcTemplate).getSetting_value();
            api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key", jdbcTemplate).getSetting_value();
            api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key", jdbcTemplate).getSetting_value();

            api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id", jdbcTemplate).getSetting_value();
            api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key", jdbcTemplate).getSetting_value();
            api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key", jdbcTemplate).getSetting_value();
            base_currency = Common.getSettings("gw_mtn_api_base_currency", jdbcTemplate).getSetting_value();
        } else {
            global_url = Common.getSettings("gw_mtn_api_url_sandbox", jdbcTemplate).getSetting_value();
            api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id_sandbox", jdbcTemplate).getSetting_value();
            api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key_sandbox", jdbcTemplate).getSetting_value();
            api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key_sandbox", jdbcTemplate).getSetting_value();

            api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id_sandbox", jdbcTemplate).getSetting_value();
            api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key_sandbox", jdbcTemplate).getSetting_value();
            api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key_sandbox", jdbcTemplate).getSetting_value();
            base_currency = Common.getSettings("gw_mtn_api_base_currency_sandbox", jdbcTemplate).getSetting_value();
        }
        mtn_mmpgw = new MTNMoMoPaymentGateway();
        mtn_mmpgw.setSegment("collection");
        mtn_mmpgw.setApiDetails(global_url, api_collections_user, 
            api_collections_key, api_collections_subscription,
            api_disbursements_user, api_disbursements_key,
            api_disbursements_subscription, env.getSetting_value(),
            base_currency);
        rData[0] =  Common.round(mtn_mmpgw.getBalance("collection"),2);
        mtn_mmpgw.setSegment("disbursement");
        rData[1] =  Common.round(mtn_mmpgw.getBalance("disbursement"),2);
        
        //Get details for Airtel Money.
        
        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        
        //Do another gateway.
        if (use_open_api.equals("yes")) {
            
            global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();
            String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
            String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

            String disbursement_acc = Common.getSettings("gw_airtelmoney_disbursement_account", jdbcTemplate)
                        .getSetting_value();
            String collections_acc = Common.getSettings("gw_airtelmoney_collections_account", jdbcTemplate)
                        .getSetting_value();
            
            String api_pin = Common.getSettings("gw_airtelmoney_api_pin", jdbcTemplate)
                        .getSetting_value();

            airteloapimm_mmpgw = new AirtelMoneyOpenApiPaymentGateway();
            airteloapimm_mmpgw.setApiDetails(global_url, api_username, api_password, api_pin);

            rData[2] = Common.round(airteloapimm_mmpgw.getBalance(collections_acc), 2);
            rData[3] = Common.round(airteloapimm_mmpgw.getBalance(disbursement_acc),2);
        
        } else {
            global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();
            String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
            String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

            String disbursement_acc = Common.getSettings("gw_airtelmoney_disbursement_account", jdbcTemplate)
                        .getSetting_value();
            String collections_acc = Common.getSettings("gw_airtelmoney_collections_account", jdbcTemplate)
                        .getSetting_value();

            airtelmm_mmpgw = new AirtelMoneyPaymentGateway();
            airtelmm_mmpgw.setApiDetails(global_url, api_username, api_password);

            rData[2] = Common.round(airtelmm_mmpgw.getBalance(collections_acc), 2);
            rData[3] = Common.round(airtelmm_mmpgw.getBalance(disbursement_acc),2);
        }
        return rData;
    }
    
    private GateWayResponse sandboxRunPayGatewayDoPayIn(String msisdn,
            Double amount, 
            String ref,
            String narrative) {
        String tx_status = "PENDING";
        if (amount.equals(2020) || amount.equals(2020.0) || amount.equals("2020.00")) {
            tx_status = "UNDETERMINED";
        } else if (amount.equals(2021) || amount.equals(2021.0) || amount.equals("2021.00")){
            tx_status = "FAILED";
        }
        GateWayResponse gwResponse = new GateWayResponse();
        gwResponse.setHttpStatus("200");
        gwResponse.setMessage("");
        gwResponse.setStatus("OK");
        gwResponse.setTransactionStatus(tx_status);
        gwResponse.setRequestTrace("SANDBOX SIMULATION transaction to "+msisdn);
        return gwResponse;
    }
    
    
    private GateWayResponse sandboxRunPayGatewayDoPayOut(String msisdn,
            Double amount, 
            String ref,
            String narrative) {
        
        String tx_status = "PENDING";
        if (amount.equals(2020) || amount.equals(2020.0) || amount.equals("2020.00")) {
            tx_status = "UNDETERMINED";
        } else if (amount.equals(2021) || amount.equals(2021.0) || amount.equals("2021.00")){
            tx_status = "FAILED";
        }
        GateWayResponse gwResponse = new GateWayResponse();
        gwResponse.setHttpStatus("200");
        gwResponse.setMessage("");
        gwResponse.setStatus("OK");
        gwResponse.setTransactionStatus(tx_status);
        gwResponse.setRequestTrace("SANDBOX SIMULATION transaction to "+msisdn);
        return gwResponse;
    }
    
    
    private GateWayResponse sandboxrunPayGatewayDoCheckStatus(String ref) {
        
        String tx_status = "SUCCESSFUL";
        
        GateWayResponse gwResponse = new GateWayResponse();
        gwResponse.setHttpStatus("200");
        gwResponse.setMessage("");
        gwResponse.setStatus("OK");
        gwResponse.setTransactionStatus(tx_status);
        gwResponse.setNetworkId(Common.generateUuid());
        gwResponse.setRequestTrace("SANDBOX SIMULATION transaction to "+ref);
        return gwResponse;
    }
            
    
    
    public GateWayResponse runPayGatewayDoPayOut( 
            NamedParameterJdbcTemplate jdbcTemplate,
            String msisdn,
            Double amount, 
            String ref,
            String narrative) {
        
        //First check if this is a test.
        String state = Common.getSettings("application_settings_state", jdbcTemplate) == null ?
                "production" : 
                Common.getSettings("application_settings_state", jdbcTemplate)
                    .getSetting_value();
        if (state.toLowerCase().equals("sandbox")) {
            return sandboxRunPayGatewayDoPayOut(msisdn,
                    amount, 
                    ref,
                    narrative);
        }
        
        //Select the gateway
        if (MTNMoMoPaymentGateway.isValidMisdn(msisdn)) {
            Setting env = Common.getSettings("gw_mtn_api_env", jdbcTemplate);
            String global_url = "";
            String api_collections_user = "";
            String api_collections_key = "";
            String api_collections_subscription = "";

            String api_disbursements_user = "";
            String api_disbursements_key = "";
            String api_disbursements_subscription = "";
            String base_currency = "";
            if (env.getSetting_value().equals("mtnuganda")) {
                global_url = Common.getSettings("gw_mtn_api_url", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key", jdbcTemplate).getSetting_value();
                
                base_currency = Common.getSettings("gw_mtn_api_base_currency", jdbcTemplate).getSetting_value();
            } else {
                global_url = Common.getSettings("gw_mtn_api_url_sandbox", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key_sandbox", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key_sandbox", jdbcTemplate).getSetting_value();
                base_currency = Common.getSettings("gw_mtn_api_base_currency_sandbox", jdbcTemplate).getSetting_value();
            }
            mtn_mmpgw = new MTNMoMoPaymentGateway();
            mtn_mmpgw.setApiDetails(global_url, api_collections_user, 
            api_collections_key, api_collections_subscription,
            api_disbursements_user, api_disbursements_key,
            api_disbursements_subscription, env.getSetting_value(),
            base_currency);
            mtn_mmpgw.setSegment("disbursement");
            GateWayResponse pResponse = mtn_mmpgw.doPayOut(amount, msisdn, ref, narrative); 
            return pResponse;
            
        }

        //Select the gateway
        if (SafariComPaymentGateway.isValidMisdn(msisdn)) {
            Setting env = Common.getSettings("gw_mtn_api_env", jdbcTemplate);
            String global_url = "";
            String api_disbursements_user = "";
            String api_disbursements_key = "";
            String shortcode = "";
            String initiatorUsername = "";
            String initiatorPassword = "";


            global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
            api_disbursements_user = Common.getSettings("gw_safaricom_api_consumer_key_disbursement", jdbcTemplate).getSetting_value();
            api_disbursements_key = Common.getSettings("gw_safaricom_api_consumer_secret_disbursement", jdbcTemplate).getSetting_value();
            shortcode = Common.getSettings("gw_safaricom_api_shortcode_disbursement", jdbcTemplate).getSetting_value();
            initiatorUsername = Common.getSettings("gw_safaricom_api_initiator_username_disbursement", jdbcTemplate).getSetting_value();
            initiatorPassword = Common.getSettings("gw_safaricom_api_initiator_pw_disbursement", jdbcTemplate).getSetting_value();
            String app_setting_app_url = Common.getSettings("app_setting_app_url", jdbcTemplate).getSetting_value();

            safaricom_mmpgw = new SafariComPaymentGateway();
            safaricom_mmpgw.setApiDetails(global_url,
                    api_disbursements_user,
                    api_disbursements_key,
                    initiatorUsername,
                    initiatorPassword,
                    shortcode,
                    "disbursement",
                    app_setting_app_url);
            safaricom_mmpgw.setSegment("disbursement");
            GateWayResponse pResponse = safaricom_mmpgw.doPayOut(amount, msisdn, ref, narrative);
            return pResponse;

        }
        
        //Do another gateway.

        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        
        //Do another gateway.
        if (use_open_api.equals("yes")) {
            if (AirtelMoneyOpenApiPaymentGateway.isValidMisdn(msisdn)) {

                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

                String api_pin = Common.getSettings("gw_airtelmoney_api_pin", jdbcTemplate)
                        .getSetting_value();

                airteloapimm_mmpgw = new AirtelMoneyOpenApiPaymentGateway();
                airteloapimm_mmpgw.setApiDetails(global_url, api_username, api_password, api_pin);

                GateWayResponse pResponse = airteloapimm_mmpgw.doPayOut(amount, msisdn, ref, narrative); 
                return pResponse;
            }
        } else {
            if (AirtelMoneyPaymentGateway.isValidMisdn(msisdn)) {

                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();



                airtelmm_mmpgw = new AirtelMoneyPaymentGateway();
                airtelmm_mmpgw.setApiDetails(global_url, api_username, api_password);

                GateWayResponse pResponse = airtelmm_mmpgw.doPayOut(amount, msisdn, ref, narrative); 
                return pResponse;

            }
        }
        
        return null;
    }
    
    
    public GateWayResponse runPayGatewayDoCheckStatus( 
            NamedParameterJdbcTemplate jdbcTemplate,
            String gateway_id, 
            String ref,
            String tx_type) {

        String app_setting_app_url = Common.getSettings("app_setting_app_url", jdbcTemplate) == null ?
                "" : Common.getSettings("app_setting_app_url", jdbcTemplate).getSetting_value();

        //If it's a sandbox, the just simulate.
        String state = Common.getSettings("application_settings_state", jdbcTemplate) == null ?
                "production" : 
                Common.getSettings("application_settings_state", jdbcTemplate)
                    .getSetting_value();
        if (state.toLowerCase().equals("sandbox")) {
            return sandboxrunPayGatewayDoCheckStatus(ref);
        }
        
        //Select the gateway
        if (gateway_id.equals("MTNMoMoPaymentGateway")) {
            Setting env = Common.getSettings("gw_mtn_api_env", jdbcTemplate);
            String global_url = "";
            String api_collections_user = "";
            String api_collections_key = "";
            String api_collections_subscription = "";

            String api_disbursements_user = "";
            String api_disbursements_key = "";
            String api_disbursements_subscription = "";
            String base_currency = "";
            
            if (env.getSetting_value().equals("mtnuganda")) {
                global_url = Common.getSettings("gw_mtn_api_url", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key", jdbcTemplate).getSetting_value();
                base_currency = Common.getSettings("gw_mtn_api_base_currency", jdbcTemplate).getSetting_value();
            } else {
                global_url = Common.getSettings("gw_mtn_api_url_sandbox", jdbcTemplate).getSetting_value();
                api_collections_user = Common.getSettings("gw_mtn_api_collections_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_collections_key = Common.getSettings("gw_mtn_api_collections_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_collections_subscription = Common.getSettings("gw_mtn_api_collections_subscription_key_sandbox", jdbcTemplate).getSetting_value();

                api_disbursements_user = Common.getSettings("gw_mtn_api_disbursements_user_id_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_mtn_api_disbursements_user_key_sandbox", jdbcTemplate).getSetting_value();
                api_disbursements_subscription = Common.getSettings("gw_mtn_api_disbursements_subscription_key_sandbox", jdbcTemplate).getSetting_value();
                base_currency = Common.getSettings("gw_mtn_api_base_currency_sandbox", jdbcTemplate).getSetting_value();
            }
            
            mtn_mmpgw = new MTNMoMoPaymentGateway();
            mtn_mmpgw.setApiDetails(global_url, api_collections_user, 
            api_collections_key, api_collections_subscription,
            api_disbursements_user, api_disbursements_key,
            api_disbursements_subscription, env.getSetting_value(),
            base_currency);
            
            mtn_mmpgw.setSegment(tx_type);
            GateWayResponse pResponse = mtn_mmpgw.checkStatus(ref); 
            return pResponse;
        }

        if (gateway_id.equals("SafariComPaymentGateway")) {
            Setting env = Common.getSettings("gw_safaricom_api_env", jdbcTemplate);
            String global_url = "";
            String gw_safaricom_api_shortcode = "";
            String gw_safaricom_api_password = "";
            String gw_safaricom_api_consumer_key = "";
            String gw_safaricom_api_consumer_secret = "";

            if (env.getSetting_value().equals("sandbox")) {
                global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
                gw_safaricom_api_shortcode = Common.getSettings("gw_safaricom_api_shortcode", jdbcTemplate).getSetting_value();
                gw_safaricom_api_password = Common.getSettings("gw_safaricom_api_password", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_key = Common.getSettings("gw_safaricom_api_consumer_key", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_secret = Common.getSettings("gw_safaricom_api_consumer_secret", jdbcTemplate).getSetting_value();
            } else {
                global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
                gw_safaricom_api_shortcode = Common.getSettings("gw_safaricom_api_shortcode", jdbcTemplate).getSetting_value();
                gw_safaricom_api_password = Common.getSettings("gw_safaricom_api_password", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_key = Common.getSettings("gw_safaricom_api_consumer_key", jdbcTemplate).getSetting_value();
                gw_safaricom_api_consumer_secret = Common.getSettings("gw_safaricom_api_consumer_secret", jdbcTemplate).getSetting_value();
            }
            safaricom_mmpgw = new SafariComPaymentGateway();
            if (tx_type.equals("collection")) {
                safaricom_mmpgw.setSegment("collection");
                safaricom_mmpgw.setApiDetails(global_url,
                        gw_safaricom_api_consumer_key,
                        gw_safaricom_api_consumer_secret,
                        gw_safaricom_api_shortcode,
                        gw_safaricom_api_password,
                        env.getSetting_value(),
                        app_setting_app_url);
            } else {
                String api_disbursements_user = "";
                String api_disbursements_key = "";
                String shortcode = "";
                String initiatorUsername = "";
                String initiatorPassword = "";

                global_url = Common.getSettings("gw_safaricom_api_url", jdbcTemplate).getSetting_value();
                api_disbursements_user = Common.getSettings("gw_safaricom_api_consumer_key_disbursement", jdbcTemplate).getSetting_value();
                api_disbursements_key = Common.getSettings("gw_safaricom_api_consumer_secret_disbursement", jdbcTemplate).getSetting_value();
                shortcode = Common.getSettings("gw_safaricom_api_shortcode_disbursement", jdbcTemplate).getSetting_value();
                initiatorUsername = Common.getSettings("gw_safaricom_api_initiator_username_disbursement", jdbcTemplate).getSetting_value();
                initiatorPassword = Common.getSettings("gw_safaricom_api_initiator_pw_disbursement", jdbcTemplate).getSetting_value();
                safaricom_mmpgw.setApiDetails(global_url,
                        api_disbursements_user,
                        api_disbursements_key,
                        initiatorUsername,
                        initiatorPassword,
                        shortcode,
                        "disbursement",
                        app_setting_app_url);
                safaricom_mmpgw.setSegment("disbursement");
            }
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "API User Details: "+gw_safaricom_api_consumer_key+" "+gw_safaricom_api_consumer_secret, "");

            safaricom_mmpgw.setSegment(tx_type);
            GateWayResponse pResponse = safaricom_mmpgw.checkStatus(ref);
            return pResponse;
        }
       
        String use_open_api = Common.getSettings("gw_airtelmoney_use_open_api", jdbcTemplate)
                    .getSetting_value();
        
        //Do another gateway.
        if (use_open_api.equals("yes")) {
            if (gateway_id.equals("AirtelMoneyOpenApiPaymentGateway")) {

                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

                String api_pin = Common.getSettings("gw_airtelmoney_api_pin", jdbcTemplate)
                        .getSetting_value();

                airteloapimm_mmpgw = new AirtelMoneyOpenApiPaymentGateway();
                airteloapimm_mmpgw.setApiDetails(global_url, api_username, api_password, api_pin);
                airteloapimm_mmpgw.setSegment(tx_type);

                GateWayResponse pResponse = airteloapimm_mmpgw.checkStatus(ref); 
                return pResponse;
            }
        } else {
            if (gateway_id.equals("AirtelMoneyPaymentGateway")) {
            
                String global_url = Common.getSettings("gw_airtelmoney_api_url", jdbcTemplate)
                        .getSetting_value();

                String api_username = Common.getSettings("gw_airtelmoney_api_username", jdbcTemplate)
                        .getSetting_value();
                String api_password = Common.getSettings("gw_airtelmoney_api_password", jdbcTemplate)
                        .getSetting_value();

                airtelmm_mmpgw = new AirtelMoneyPaymentGateway();
                airtelmm_mmpgw.setApiDetails(global_url, api_username, api_password);
                airtelmm_mmpgw.setSegment(tx_type);

                GateWayResponse pResponse = airtelmm_mmpgw.checkStatus(ref); 
                return pResponse;
            }
        }
        //Another gateway
        return null;
    }
    
}
