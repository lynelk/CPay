/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.citotech.cito.Model.*;
import net.citotech.cito.Model.HttpRequestResponse.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.RequestBody;

/**
 *
 * @author josephtabajjwa
 */
public class Common {
    public static String DB_TABLE_ADMIN = "admins";
    public static String DB_TABLE_ADMIN_PRIVILEGES = "admin_privileges";
    public static String DB_TABLE_AUDIT_TRAIL = "audit_trail";
    public static String DB_TABLE_AUDIT_TRAIL_MERCHANT = "merchants_audit_trail";
    public static String DB_TABLE_SETTINGS = "settings";
    public static String DB_TABLE_MERCHANTS = "merchants";
    public static String DB_TABLE_MERCHANT_USERS = "merchant_admins";
    public static String DB_TABLE_MERCHANT_TRANSACTION_LOG = "merchant_transactions_log";
    public static String DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG = "merchant_batch_transactions_log";
    public static String DB_TABLE_MERCHANT_BATCH_TRANSACTION_BENEFICIARIES = "beneficiaries";
    public static String DB_TABLE_MERCHANT_STATEMENT = "merchant_statement";
    public static String DB_TABLE_CHARGING_DETAILS = "charging_details";
    public static String DB_TABLE_MERCHANT_ADMIN_PRIVILEGES = "merchant_admin_privileges";
    public static String DB_TABLE_DB_CHANGES = "db_changes";
    public static String DB_TABLE_MERCHANT_SMS = "merchant_sms";
    public static String DB_MERCHANTS_SETTINGS = "merchant_settings";
    
    
    //Settings
    public static String CLASS_PATH_DEFAULT_SETTINGS = "settings/default_settings.json";
    public static String CLASS_PATH_DEFAULT_MERCHANT_SETTINGS = "settings/default_merchant_settings.json";
    public static String CLASS_PATH_GENERAL_SETTINGS = "settings/general_settings.json";
    public static String CLASS_PATH_GENERAL_DBCHANGES_DIR = "dbchanges";
    public static String CLASS_PATH_MTN_TOKEN_FILE = "default_mtn_token.json";
    public static String CLASS_PATH_SAFARICOM_TOKEN_FILE = "default_safaricom_token.json";
    public static String CLASS_PATH_AIRTELOAPI_TOKEN_FILE = "default_airteloapi_token.json";
    public static String CLASS_PATH_CHECK_TX_LOCK = "check_tx.lock";
    public static String CLASS_PATH_SEND_SMS_SERVICE_TX_LOCK = "send_sms_service_tx.lock";
    public static String CLASS_PATH_UPLOAD_DIRECTORY = "uploadDir";
    public static String CLASS_PATH_PAYMENTS_CRON_TX_LOCK = "payments_cron_tx.lock";
    
    /** Set to {@code true} only in local development (via {@code custom.ssl.skip-verify=true}). */
    private static volatile boolean skipSslVerify = false;

    /** Application base URL used in outbound email links (e.g. password-reset). */
    private static volatile String appBaseUrl = "";

    /**
     * Called at startup by {@link net.citotech.cito.config.SslConfig} to set
     * the application base URL used in outbound email links.
     */
    public static void setAppBaseUrl(String url) {
        appBaseUrl = (url != null) ? url : "";
    }


    public static void setSslSkipVerify(boolean skip) {
        skipSslVerify = skip;
        if (skip) {
            Logger.getLogger(Common.class.getName()).log(Level.WARNING,
                "SECURITY WARNING: SSL certificate verification is DISABLED "
                + "(custom.ssl.skip-verify=true). Do NOT use in production.");
        }
    }


    private static final String NUMERIC_STRING = "0123456789";
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    
    public static int HTTP_REQUEST_TIMEOUT_MILLISECONDS = 30000;
    public static int HTTP_REQUEST_READTIMEOUT_MILLISECONDS = 60000;
    
    public static String API_MOBILE_MONEY_PAYIN = "MOBILE_MONEY_PAYIN";
    public static String API_MOBILE_MONEY_PAYOUT = "MOBILE_MONEY_PAYOUT";
    public static String API_MULTIPLE_PAYOUT = "MULTIPLE_PAYOUT";
    public static String API_MULTIPLE_CHECKSTATUS = "MULTIPLE_CHECKSTATUS";
    public static String API_TRANSACTION_CHECKSTATUS = "TRANSACTION_CHECKSTATUS";
    public static String API_BALANCE_CHECK = "BALANCE_CHECK";
    public static String API_ACCOUNT_VALIDATION = "ACCOUNT_VALIDATION";
    public static String API_SEND_SMS = "API_SEND_SMS";
    
    /*
    * Returns random numeric string
    * @Parma count: is the length you would like 
    */
    public static String randomNumericString(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*NUMERIC_STRING.length());
            builder.append(NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
    
    
    public static String recordAction(User user, String action, NamedParameterJdbcTemplate jdbcTemplate) {
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_AUDIT_TRAIL+" "
        +" SET `user_name`=:user_name,"
        +" `user_id`=:user_id, "
        +" `action`=:action";

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("user_name", user.getName());
        parameters.put("user_id", user.getEmail());
        parameters.put("action", action);

        try {
            long userId = jdbcTemplate.update(sql, parameters);
            //Now insert privileges
            return "success";
        } catch (Exception e) {
            return GeneralException
                .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
        }
    }
    
    
    public static String recordMerchantAction(MerchantUser user, String action, NamedParameterJdbcTemplate jdbcTemplate) {
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_AUDIT_TRAIL_MERCHANT+" "
        +" SET `user_name`=:user_name,"
        +" `user_id`=:user_id, "
        +" `merchant_id`=:merchant_id, "
        +" `action`=:action";

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("user_name", user.getName());
        parameters.put("user_id", user.getEmail());
        parameters.put("merchant_id", user.getMerchant_id());
        parameters.put("action", action);

        try {
            long userId = jdbcTemplate.update(sql, parameters);
            //Now insert privileges
            return "success";
        } catch (Exception e) {
            return GeneralException
                .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
        }
    }
    
    /*
    * Returns random alpha numeric string.
    * @Parma count: is the length you would like 
    */
    public static String randomAlphaNumericString(int count) {
        StringBuilder builder = new StringBuilder();
        while (count-- != 0) {
            int character = (int)(Math.random()*ALPHA_NUMERIC_STRING.length());
            builder.append(ALPHA_NUMERIC_STRING.charAt(character));
        }
        return builder.toString();
    }
    
    /*
    * Checks to see if the user is allowed to access or perform an account 
    * 
    */
    static Boolean isUserAllowedAccessToThis(String permission, User user) {
        List<UserPrivilege> uPermissions = user.getPrivileges();
        for (UserPrivilege p : uPermissions) {
            String privilege = p.getPrivilege();
            if (privilege != null && privilege.equals(permission)) {
                return true;
            }
        }
        return false;
    }
    
    /*
    * Retrieves settings page
    * 
    * Returns Settings Object or null.
    */
    static public Setting getSettings(String settings_name, 
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", settings_name);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_SETTINGS+" "
                + " WHERE name=:name";
        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setName(rs.getString("name"));
                setting.setLabel(rs.getString("label"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setId(rs.getLong("id"));
                setting.setGroup(rs.getString("setting_group"));
                setting.setDescription(rs.getString("description"));
                return setting;
            }
        };
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }
    
    /*
    * Retrieves settings page
    * 
    * Returns Settings Object or null.
    */
    static public Setting getMerchantSettings(String settings_name, 
            Long merchant_id,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", settings_name);
        parameters.addValue("merchant_id", merchant_id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_MERCHANTS_SETTINGS+" "
                + " WHERE name=:name AND merchant_id=:merchant_id ";
        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setName(rs.getString("name"));
                setting.setLabel(rs.getString("label"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setId(rs.getLong("id"));
                setting.setGroup(rs.getString("setting_group"));
                setting.setDescription(rs.getString("description"));
                setting.setMerchant_id(rs.getLong("merchant_id"));
                return setting;
            }
        };
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }
    
    public static DateTimeFormatter getDateTimeFormater() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return formatter;
    }
    
    
    public static String getCurrentDate() {
        LocalDateTime dt = LocalDateTime.now();
        return dt.format(Common.getDateTimeFormater());
    }
    
    /*
    * 
    * Helper method to make http requests.
    * 
    *
    * @Param method: This may be set to GET, POST, PUT, DELETE.
    * @Param url: This is the url to call.
    * @Param data: This is the data to be sent.
    * @Param headers: a hashmap of headers.
    * 
    * Returns HttpRequestREsponse class
    */
    
    public static HttpRequestResponse doHttpRequest(String method, String url, 
            String data, Map<String, String> headers) {
        HttpRequestResponse r = new HttpRequestResponse();
            r.setUrl(url);
            r.setRequestData(data);
            r.setRequestHeaders(headers);
        try {
            URL rquestUrl = new URL(url);
            HttpURLConnection con;

            if ("https".equalsIgnoreCase(rquestUrl.getProtocol())) {
                HttpsURLConnection httpsConn = (HttpsURLConnection) rquestUrl.openConnection();
                if (skipSslVerify) {
                    // Development-only bypass – do NOT enable in production.
                    SSLContext sc = SSLContext.getInstance("SSL");
                    sc.init(null, getTrustmanager(), new java.security.SecureRandom());
                    httpsConn.setSSLSocketFactory(sc.getSocketFactory());
                    httpsConn.setHostnameVerifier((hostname, session) -> true);
                }
                con = httpsConn;
            } else {
                con = (HttpURLConnection) rquestUrl.openConnection();
            }
            
            con.setRequestMethod(method);
            
            for (Map.Entry<String, String> h : headers.entrySet()) {
                con.setRequestProperty(h.getKey(), h.getValue());
            }
            
            con.setConnectTimeout(HTTP_REQUEST_TIMEOUT_MILLISECONDS);
            con.setReadTimeout(HTTP_REQUEST_READTIMEOUT_MILLISECONDS);
            con.setDoOutput(true);
            
            
            
            //methods without the body.
            List<String> methods = new ArrayList();
            methods.add("DELETE");
            methods.add("PUT");
            methods.add("POST");
            
            if (methods.contains(method)) {
                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                out.writeBytes(data);
                out.flush();
                out.close();
            }
            
            //Now read the content of the response
            int status = con.getResponseCode();
            
            Reader streamReader = null;
            if (status > 299) {
                if (con.getErrorStream() == null) {
                    streamReader = null;
                } else {
                    streamReader = new InputStreamReader(con.getErrorStream());
                }
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }
            
            //streamReader = new InputStreamReader(con.getInputStream());
            StringBuffer content = new StringBuffer();
            if (streamReader != null) {
                BufferedReader in = new BufferedReader(streamReader);
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                r.setStatusCode(status);
                r.setResponse(content.toString());
                r.setRequestHeaders(headers);
            } else {
                content.append("");
                r.setStatusCode(status);
                r.setResponse(content.toString());
                r.setRequestHeaders(headers);
            }
            Map<String, String> rHeaders = new HashMap<>();
           
            con.getHeaderFields().entrySet().stream()
                .filter(entry -> entry.getKey() != null)
                .forEach(entry -> {
                
                    List headerValues = entry.getValue();
                    String sHeaderValue = "";
                    Iterator it = headerValues.iterator();
                    if (it.hasNext()) {
                        sHeaderValue += it.next();
                        while (it.hasNext()) {
                            sHeaderValue += it.next();
                        }
                    }

                    rHeaders.put(entry.getKey(), sHeaderValue);   
            });
            r.setResponseHeaders(rHeaders);
            r.setErrorMessage("");
            con.disconnect(); 
            
            return r;
        } catch (MalformedURLException ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        } catch (IOException ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        } catch (KeyManagementException ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        } catch (Exception ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        }
    }
    
    public static TrustManager[] getTrustmanager() {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                // Not implemented
            }
        } };
        return trustAllCerts;
    }
    
    public static String base64Encode(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes());
    }
    
    public static String base64Decode(String content) {
        byte[] decodedBytes = Base64.getDecoder().decode(content);
        return new String(decodedBytes);
    }
    
    public static PublicKey getPublicKeyFromBase64String(String base64String) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpecX509 = new X509EncodedKeySpec(Base64.getMimeDecoder().decode(base64String));
            RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);
            return pubKey;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }
    
    public static PrivateKey getPrivateKeyFromBase64String(String base64String) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpecPKCS8 = new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64String));
            PrivateKey privKey = kf.generatePrivate(keySpecPKCS8);
            return privKey;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }

    public static String generateSha256String(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(data.getBytes("utf8"));
            String sha256 = String.format("%040x", new BigInteger(1, digest.digest()));
            return sha256;
        } catch (Exception e){
            e.printStackTrace();
            return "";
        }
    }
    
    public static String generateUuid() {
        UUID uuid = UUID.randomUUID();
        String r = uuid.toString();
        return r;//r.substring(0, 25);
    }
    
    
    /*
    * Queries the database to get the Merchant 
    * by their id.
    * 
    * Returns Merchant object or null.
    */
    static public Merchant getMerchantById(String id,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_id", id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" "
                + " WHERE id=:account_id";
        RowMapper rm = new RowMapper<Merchant>() {
        public Merchant mapRow(ResultSet rs, int rowNum) throws SQLException {
                Merchant m = new Merchant();
                m.setName(rs.getString("name"));
                m.setShort_name(rs.getString("short_name"));
                m.setAccount_number(rs.getString("account_number"));
                m.setStatus(rs.getString("status"));
                m.setId(rs.getLong("id"));
                m.setCreated_on(rs.getString("created_on"));
                m.setCreated_by(rs.getString("created_by"));
                m.setAccount_type(rs.getString("account_type"));
                m.setPublic_key(rs.getString("public_key"));
                m.setPrivate_key(rs.getString("private_key"));
                String allowed_apis_string = rs.getString("allowed_apis")!= null ? 
                        rs.getString("allowed_apis"): "";
                
                String[] allowed_apis;
                if (allowed_apis_string.isEmpty()) {
                    allowed_apis = new String[0];
                } else {
                    allowed_apis = allowed_apis_string.split(",");
                }
                m.setAllowed_apis(allowed_apis);
                
                m.setUsers(getMerchantUsers(m, jdbcTemplate));
                return m;
            }
        };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }
    
    
    /*
    * Queries the database to get the Merchant 
    * by their id.
    * @Param reference: The customer's reference as submitted in the API request.
    * @Param merchant_id: This is the customer's long id
    * Returns Merchant object or null.
    */
    static public Transaction getMerchantTxByTheirRef(String reference, String merchant_id,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchant_id);
        parameters.addValue("tx_merchant_ref", reference);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " WHERE merchant_id=:merchant_id AND tx_merchant_ref=:tx_merchant_ref";
        RowMapper rm = new RowMapper<Transaction>() {
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                 Transaction t = new Transaction();
                    t.setId(rs.getLong("id"));
                    t.setCharging_method(rs.getString("charging_method"));
                    t.setCharges(rs.getDouble("charges"));
                    t.setOriginal_amount(rs.getDouble("original_amount"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setStatus(rs.getString("status"));
                    t.setMerchant_id(rs.getString("merchant_id"));
                    t.setTx_description(rs.getString("tx_request_trace"));
                    t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                    t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                    t.setTx_request_trace(rs.getString("tx_request_trace"));
                    t.setTx_unique_id(rs.getString("tx_unique_id"));
                    t.setTx_update_trace(rs.getString("tx_update_trace"));
                    t.setPayer_number(rs.getString("payer_number"));
                    t.setTx_type(rs.getString("tx_type"));
                return t;
            }
        };
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }




    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    static public Transaction getTxByRef(String reference,
                                                      NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tx_unique_id", reference);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " WHERE tx_unique_id=:tx_unique_id";
        RowMapper rm = new RowMapper<Transaction>() {
            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                Transaction t = new Transaction();
                t.setId(rs.getLong("id"));
                t.setCharging_method(rs.getString("charging_method"));
                t.setCharges(rs.getDouble("charges"));
                t.setOriginal_amount(rs.getDouble("original_amount"));
                t.setCreated_on(rs.getString("created_on"));
                t.setUpdated_on(rs.getString("updated_on"));
                t.setGateway_id(rs.getString("gateway_id"));
                t.setStatus(rs.getString("status"));
                t.setMerchant_id(rs.getString("merchant_id"));
                t.setTx_description(rs.getString("tx_request_trace"));
                t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                t.setTx_request_trace(rs.getString("tx_request_trace"));
                t.setTx_unique_id(rs.getString("tx_unique_id"));
                t.setTx_update_trace(rs.getString("tx_update_trace"));
                t.setPayer_number(rs.getString("payer_number"));
                t.setTx_type(rs.getString("tx_type"));
                return t;
            }
        };
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    static public Transaction getTxByNetworkRef(String networkRef,
                                         NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tx_gateway_ref", networkRef);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " WHERE tx_gateway_ref=:tx_gateway_ref FOR UPDATE";
        RowMapper rm = new RowMapper<Transaction>() {
            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                Transaction t = new Transaction();
                t.setId(rs.getLong("id"));
                t.setCharging_method(rs.getString("charging_method"));
                t.setCharges(rs.getDouble("charges"));
                t.setOriginal_amount(rs.getDouble("original_amount"));
                t.setCreated_on(rs.getString("created_on"));
                t.setUpdated_on(rs.getString("updated_on"));
                t.setGateway_id(rs.getString("gateway_id"));
                t.setStatus(rs.getString("status"));
                t.setMerchant_id(rs.getString("merchant_id"));
                t.setTx_description(rs.getString("tx_request_trace"));
                t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                t.setTx_request_trace(rs.getString("tx_request_trace"));
                t.setTx_unique_id(rs.getString("tx_unique_id"));
                t.setTx_update_trace(rs.getString("tx_update_trace"));
                t.setPayer_number(rs.getString("payer_number"));
                t.setTx_type(rs.getString("tx_type"));
                return t;
            }
        };
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    static public Transaction getTxBySafaricomRef(String networkRef,
                                                NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("safaricom_request_reference", networkRef);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " WHERE safaricom_request_reference=:safaricom_request_reference FOR UPDATE";
        RowMapper rm = new RowMapper<Transaction>() {
            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                Transaction t = new Transaction();
                t.setId(rs.getLong("id"));
                t.setCharging_method(rs.getString("charging_method"));
                t.setCharges(rs.getDouble("charges"));
                t.setOriginal_amount(rs.getDouble("original_amount"));
                t.setCreated_on(rs.getString("created_on"));
                t.setUpdated_on(rs.getString("updated_on"));
                t.setGateway_id(rs.getString("gateway_id"));
                t.setStatus(rs.getString("status"));
                t.setMerchant_id(rs.getString("merchant_id"));
                t.setTx_description(rs.getString("tx_request_trace"));
                t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                t.setTx_request_trace(rs.getString("tx_request_trace"));
                t.setTx_unique_id(rs.getString("tx_unique_id"));
                t.setTx_update_trace(rs.getString("tx_update_trace"));
                t.setPayer_number(rs.getString("payer_number"));
                t.setTx_type(rs.getString("tx_type"));
                t.setSafaricomRequestReference(rs.getString("safaricom_request_reference"));
                return t;
            }
        };
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }
    
    
    /*
    * Queries the database to get the Merchant 
    * by their id.
    * @Param reference: The customer's reference as submitted in the API request.
    * @Param merchant_id: This is the customer's long id
    * Returns Merchant object or null.
    */
    static public Transaction getTxByBatchIdBeneficiaryId(long batch_id, long beneficiaryId,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("beneficiary_id", beneficiaryId);
        parameters.addValue("batch_id", batch_id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " WHERE beneficiary_id=:beneficiary_id "
                + " AND merchant_batch_transactions_log_id=:batch_id ";
        
        RowMapper rm = new RowMapper<Transaction>() {
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                 Transaction t = new Transaction();
                    t.setId(rs.getLong("id"));
                    t.setCharging_method(rs.getString("charging_method"));
                    t.setCharges(rs.getDouble("charges"));
                    t.setOriginal_amount(rs.getDouble("original_amount"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setStatus(rs.getString("status"));
                    t.setMerchant_id(rs.getString("merchant_id"));
                    t.setTx_description(rs.getString("tx_request_trace"));
                    t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                    t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                    t.setTx_request_trace(rs.getString("tx_request_trace"));
                    t.setTx_unique_id(rs.getString("tx_unique_id"));
                    t.setTx_update_trace(rs.getString("tx_update_trace"));
                    t.setPayer_number(rs.getString("payer_number"));
                    t.setTx_type(rs.getString("tx_type"));
                return t;
            }
        };
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }
    
    
    /*
    * Queries the database to get the Merchant 
    * by their account_number.
    * 
    * Returns Merchant object or null.
    */
    static public Merchant getMerchantByAccountNumber(String acc_number,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_number", acc_number);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" "
                + " WHERE account_number=:account_number";
        
        RowMapper rm = new RowMapper<Merchant>() {
        public Merchant mapRow(ResultSet rs, int rowNum) throws SQLException {
                Merchant m = new Merchant();
                m.setName(rs.getString("name"));
                m.setAccount_number(rs.getString("account_number"));
                m.setStatus(rs.getString("status"));
                m.setId(rs.getLong("id"));
                m.setCreated_on(rs.getString("created_on"));
                m.setCreated_by(rs.getString("created_by"));
                m.setAccount_type(rs.getString("account_type"));
                m.setPublic_key(rs.getString("public_key"));
                m.setPrivate_key(rs.getString("private_key"));
                m.setShort_name(rs.getString("short_name"));
                //Get allowed APIs
                String allowed_apis_string = rs.getString("allowed_apis")!= null ? 
                        rs.getString("allowed_apis"): "";
                
                String[] allowed_apis;
                if (allowed_apis_string.isEmpty()) {
                    allowed_apis = new String[0];
                } else {
                    allowed_apis = allowed_apis_string.split(",");
                }
                m.setAllowed_apis(allowed_apis);
                
                m.setUsers(getMerchantUsers(m, jdbcTemplate));
                return m;
            }
        };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }
    
    /*
    * Queries the database to get the user 
    * by their email address.
    * 
    * Returns User object or null.
    *
    static public Merchant getMerchantByAccountNumber(String account,
            NamedParameterJdbcTemplate jdbcTemplate) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_number", account);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" "
                + " WHERE account_number=:account_number";
        RowMapper rm = new RowMapper<Merchant>() {
        public Merchant mapRow(ResultSet rs, int rowNum) throws SQLException {
                Merchant m = new Merchant();
                m.setName(rs.getString("name"));
                m.setAccount_number(rs.getString("account_number"));
                m.setStatus(rs.getString("status"));
                m.setId(rs.getLong("id"));
                m.setCreated_on(rs.getString("created_on"));
                m.setCreated_by(rs.getString("created_by"));
                m.setAccount_type(rs.getString("account_type"));
                m.setUsers(getMerchantUsers(m, jdbcTemplate));
                return m;
            }
        };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }*/
    
    public static List<MerchantUser> getMerchantUsers(Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate ) {
        String sqlSelect = "SELECT *,"
                + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
                + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
                + " FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                + " WHERE ";
                sqlSelect += " merchant_id=:merchant_id";
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("merchant_id", merchant.getId());
                
            RowMapper rm = new RowMapper<MerchantUser>() {
                public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                    MerchantUser user = new MerchantUser();
                    user.setName(rs.getString("name"));
                    user.setId(rs.getLong("id"));
                    user.setCreated_on(rs.getString("created_on"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setStatus(rs.getString("status"));
                    user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                    user.setEmail_verification_code(rs.getString("email_verification_code"));
                    
                    user.setPrivileges(getUserPrivileges(user, jdbcTemplate));
                    return user;
                }
            };
            
            List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            
            return listUsers;
    }
    
    public static List<UserPrivilege> getUserPrivileges(User user, 
            NamedParameterJdbcTemplate jdbcTemplate) {
        String sqlSelect = "SELECT * FROM "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" WHERE ";
                sqlSelect += " admin_id=:admin_id";
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("admin_id", user.getId());
                
            RowMapper rm = new RowMapper<UserPrivilege>() {
                public UserPrivilege mapRow(ResultSet rs, int rowNum) throws SQLException {
                    UserPrivilege user = new UserPrivilege();
                    user.setPrivilege(rs.getString("privilege"));
                    user.setId(rs.getLong("id"));
                    user.setCreated_on(rs.getString("created_on"));
                    user.setUdpated_on(rs.getString("updated_on"));
                    return user;
                }
            };
            
            List<UserPrivilege> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            
            return listUsers;
    }
    
    
    static public ArrayList<Balance> getMerchantBalances (String merchant_id, 
            NamedParameterJdbcTemplate jdbcTemplate) {
        //Set the response header
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant_id);

        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_STATEMENT+" "
                + "WHERE merchant_id = :merchant_id"
                + " ORDER BY id DESC LIMIT 1";

        RowMapper rm = new RowMapper<Statement>() {
        public Statement mapRow(ResultSet rs, int rowNum) throws SQLException {
                Statement t = new Statement();
                t.setId(rs.getLong("id"));
                t.setAmount(rs.getDouble("amount"));
                t.setTransactions_log_id(rs.getLong("transactions_log_id"));
                t.setGateway_id(rs.getString("gateway_id"));
                t.setCreated_on(rs.getString("created_on"));
                t.setUpdated_on(rs.getString("updated_on"));
                t.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
                t.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
                t.setDescription(rs.getString("description"));
                t.setTx_type(rs.getString("tx_type"));
                t.setSms_balance(rs.getDouble("sms_balance"));
                t.setSafaricom_balance(rs.getDouble(SafariComPaymentGateway.BALANCE_TYPE));
                return t;
            }
        };

        //ResultSet rs; 
        List<Statement> listS = jdbcTemplate.query(sqlSelect, parameters, rm);
        ArrayList<Balance> balances = new ArrayList<>(); 
        for (Statement us : listS) {
            String code = MTNMoMoPaymentGateway.getGatewayCurrencyCode();
            Double amount = us.getMtnmm_balance();
            String gateway_id = MTNMoMoPaymentGateway.getGatewayId();
            Balance mtn_mm = new Balance(code, amount, gateway_id);
            mtn_mm.setBalance_type(Balance.BALANCE_TYPE_MTNMM_BALANCE);
            
            //Airtel
            String airtelmm_code = AirtelMoneyPaymentGateway.getGatewayCurrencyCode();
            Double airtelmm_amount = us.getAirtelmm_balance();
            String airtelmm_gateway_id = AirtelMoneyPaymentGateway.getGatewayId();
            Balance airtelmm_mtn_mm = new Balance(airtelmm_code, airtelmm_amount, airtelmm_gateway_id);
            airtelmm_mtn_mm.setBalance_type(Balance.BALANCE_TYPE_AIRTELMM_BALANCE);


            //Safaricom
            String safaricom_balance_code = SafariComPaymentGateway.getGatewayCurrencyCode();
            Double safaricom_balance_amount = us.getSafaricom_balance();
            String safaricom_balance_gateway_id = SafariComPaymentGateway.getGatewayId();
            Balance safaricom_balance_mm = new Balance(safaricom_balance_code, safaricom_balance_amount, safaricom_balance_gateway_id);
            safaricom_balance_mm.setBalance_type(Balance.BALANCE_TYPE_SAFARICOMMM_BALANCE);
            
            //Other balances
            String sms_code = SmsGateway.getGatewayCurrencyCode();
            Double sms_amount = us.getSms_balance();
            String sms_gateway_id = SmsGateway.getGatewayId();
            Balance sms_balance = new Balance(sms_code, sms_amount, sms_gateway_id);
            sms_balance.setBalance_type(Balance.BALANCE_TYPE_SMS_BALANCE);

            balances.add(mtn_mm);
            balances.add(airtelmm_mtn_mm);
            balances.add(safaricom_balance_mm);
            balances.add(sms_balance);
        }
        
        return balances;    
    }
    
    static String numberFormat(Double n) {
        String formattedNumber = String.format("%,.2f", n);
        return formattedNumber;
    }
    
    static KeyPairStrings generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();
            
            Base64.Encoder encoder = Base64.getMimeEncoder();
            
            Key pub = kp.getPublic();
            Key pvt = kp.getPrivate();
            
            String private_k = "-----BEGIN PRIVATE KEY-----\n";
            private_k += encoder.encodeToString(pvt.getEncoded());
            private_k += "\n-----END PRIVATE KEY-----\n";
            
            String public_k = "-----BEGIN PUBLIC KEY-----\n";
            public_k += encoder.encodeToString(pub.getEncoded());
            public_k += "\n-----END PUBLIC KEY-----\n";
            
            //Logger.getLogger(Common.class.getName()).log(Level.SEVERE, private_k, "");
            
            KeyPairStrings r = new KeyPairStrings(public_k, private_k);
            return r;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
    
    
    static public String imploadStringArray(String[] strings) {
        String r = "";
        for (String s : strings) {
            String s_ = s.trim();
            if (s_.isEmpty()) {
                continue;
            }
            r += s.trim()+",";
        }
        r = r.substring(0, (r.length()-1));
        return r;
    }
    
    static public String imploadStringJsonArray(JSONArray strings) throws JSONException {
        String r = "";
        for (int i=0; i < strings.length(); i++) {
            String s = strings.getString(i).trim();
            if (s.isEmpty()) {
                continue;
            }
            r += s+",";
        }
        if (r.length() > 0) {
            r = r.substring(0, (r.length()-1));
        }
        return r;
    }
    
    
    /*
    * @Param String balance_type: This is the balance type, check Common.
    * @Param Statement tx : This is the statement transaction.
    * Returns success | JSON String with errors.
    */
    
    static public String recordStatementTx(Statement tx, 
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        
        //Balance query
        String balanceSql = "SELECT * FROM "+Common.DB_TABLE_MERCHANT_STATEMENT
                +" WHERE merchant_id = :merchant_id "
                +" ORDER BY id DESC LIMIT 1 "
                +" FOR UPDATE";

         MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
         parametersBalanceSql.addValue("merchant_id", tx.getMerchant_id());

        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_STATEMENT+" "
            +" SET `merchant_id`=:merchant_id,"
            +" `gateway_id`=:gateway_id, "
            +" `description`=:description,"
            +" `recorded_by`=:recorded_by,"
            +" `amount`=:amount,"
            +" `tx_type`=:tx_type,"
            +" `narrative`=:narrative,"
            +" `airtelmm_balance`=:airtelmm_balance,"
                +" `safaricom_balance`=:safaricom_balance,"
            +" `sms_balance`=:sms_balance,"
            +" `mtnmm_balance`=:mtnmm_balance";


        MapSqlParameterSource parameters = new MapSqlParameterSource();
        
        if (tx.getTransactions_log_id() > 0) {
            sql += ", transactions_log_id=:transactions_log_id ";
            parameters.addValue("transactions_log_id", tx.getTransactions_log_id());
        }
        parameters.addValue("merchant_id", tx.getMerchant_id());
        parameters.addValue("gateway_id", tx.getGateway_id());
        parameters.addValue("description", tx.getDescription());
        parameters.addValue("amount", tx.getAmount());
        parameters.addValue("tx_type", tx.getTx_type());
        parameters.addValue("narrative", tx.getNarritive());
        parameters.addValue("recorded_by", tx.getRecorded_by());

        final String sql_final = sql;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    RowMapper rm_b = new RowMapper<Statement>() {
                    public Statement mapRow(ResultSet rs, int rowNum) throws SQLException {
                            Statement t = new Statement();
                            t.setId(rs.getLong("id"));
                            t.setAmount(rs.getDouble("amount"));
                            t.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
                            t.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
                            t.setSafaricom_balance(rs.getDouble("safaricom_balance"));
                            t.setCreated_on(rs.getString("created_on"));
                            t.setUpdated_on(rs.getString("updated_on"));
                            t.setGateway_id(rs.getString("gateway_id"));
                            t.setDescription(rs.getString("description"));
                            t.setMerchant_id(rs.getLong("merchant_id"));
                            t.setNarritive(rs.getString("narrative"));
                            t.setTransactions_log_id(rs.getLong("transactions_log_id"));
                            t.setTx_type(rs.getString("tx_type"));
                            t.setSms_balance(rs.getDouble("sms_balance"));
                            return t;
                        }
                    };

                    List<Statement> balanceList = jdbcTemplate.query(balanceSql, parametersBalanceSql, rm_b);
                    Balance mtn_balance;
                    Balance airtel_balance;
                    Balance sms_balance;
                    Balance safaricom_balance;

                    if (balanceList.size() > 0) {
                        Statement s = balanceList.get(0);
                        mtn_balance = new Balance("UGX MTN MM", 
                                s.getMtnmm_balance(), 
                                "MTNMoMoPaymentGateway"   );

                        airtel_balance = new Balance("UGX AIRTEL MM", 
                                s.getAirtelmm_balance(), 
                                "AirtelMoneyPaymentGateway"   );
                        airtel_balance.setBaseCurrency("UGX");

                        safaricom_balance = new Balance("KES MPESA",
                                s.getSafaricom_balance(),
                                "SafariComPaymentGateway"   );
                        safaricom_balance.setBaseCurrency("KES");

                        sms_balance = new Balance("UGX SMS", 
                                s.getSms_balance(), 
                                "SmsGateway"   );
                        sms_balance.setBaseCurrency("UGX");
                    } else {
                        mtn_balance = new Balance("UGX MTN MM", 
                                0.00, 
                                "MTNMoMoPaymentGateway");

                        airtel_balance = new Balance("UGX AIRTEL MM", 
                                0.00, 
                                "AirtelMoneyPaymentGateway");
                        airtel_balance.setBaseCurrency("UGX");

                        safaricom_balance = new Balance("KES MPESA",
                                0.00,
                                "SafariComPaymentGateway"   );
                        safaricom_balance.setBaseCurrency("KES");

                        sms_balance = new Balance("UGX SMS", 
                                0.00, 
                                "SmsGateway"   );
                        sms_balance.setBaseCurrency("UGX");
                    }

                    //New balance
                    if (tx.getTx_type().contains("CR")) {
                        if (balance_type.equals("mtnmm_balance")) {
                            Double nBalance = tx.getAmount() + mtn_balance.getAmount();
                            parameters.addValue("mtnmm_balance", nBalance);
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }
                        if (balance_type.equals("airtelmm_balance")) {
                            Double nBalance = tx.getAmount() + airtel_balance.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", nBalance);
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }
                        if (balance_type.equals("safaricom_balance")) {
                            Double nBalance = tx.getAmount() + safaricom_balance.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", nBalance);
                        }
                        if (balance_type.equals("sms_balance")) {
                            Double nBalance = tx.getAmount() + sms_balance.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", nBalance);
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }
                    } else {
                        if (balance_type.equals("mtnmm_balance")) {
                            //Check if there is enough balance for this transaction
                            if (tx.getAmount() > mtn_balance.getAmount()) {
                                status.setRollbackOnly();
                                return GeneralException
                                        .getError("111", 
                                                String.format(GeneralException.ERRORS_111, 
                                                        mtn_balance.getAmount(), 
                                                        mtn_balance.getCode()));
                            }
                            Double nBalance =  mtn_balance.getAmount() - tx.getAmount();
                            parameters.addValue("mtnmm_balance", nBalance);
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }

                        if (balance_type.equals("airtelmm_balance")) {
                            if (tx.getAmount() > airtel_balance.getAmount()) {
                                status.setRollbackOnly();
                                return GeneralException
                                        .getError("111", 
                                                String.format(GeneralException.ERRORS_111, 
                                                        airtel_balance.getAmount(), 
                                                        airtel_balance.getCode()));
                            }
                            Double nBalance = airtel_balance.getAmount() - tx.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", nBalance);
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }

                        if (balance_type.equals("sms_balance")) {
                            if (tx.getAmount() > sms_balance.getAmount()) {
                                status.setRollbackOnly();
                                return GeneralException
                                        .getError("111", 
                                                String.format(GeneralException.ERRORS_111, 
                                                        sms_balance.getAmount(), 
                                                        sms_balance.getCode()));
                            }
                            Double nBalance = sms_balance.getAmount() - tx.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", nBalance);
                            parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                        }
                        if (balance_type.equals("safaricom_balance")) {
                            if (tx.getAmount() > safaricom_balance.getAmount()) {
                                status.setRollbackOnly();
                                return GeneralException
                                        .getError("111",
                                                String.format(GeneralException.ERRORS_111,
                                                        safaricom_balance.getAmount(),
                                                        safaricom_balance.getCode()));
                            }
                            Double nBalance = safaricom_balance.getAmount() - tx.getAmount();
                            parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                            parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                            parameters.addValue("sms_balance", sms_balance.getAmount());
                            parameters.addValue("safaricom_balance", nBalance);
                        }
                        //More balances
                    }

                    if (parameters.getParameterNames().length <= 0) {
                        return GeneralException
                                .getError("102", GeneralException.ERRORS_102
                                        +" "+balance_type);
                    }

                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    //long userId;
                    jdbcTemplate.update(sql_final, parameters, keyHolder);
                    //Now insert privileges
                    BigInteger statementId = (BigInteger)keyHolder.getKey();


                    return "success";
                } catch (Exception e) {
                    e.printStackTrace();
                    status.setRollbackOnly();
                    return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
                }
            }
        });
        return result;
    }
    
    
    static public String recordStatementTxWithoutTransaciton(Statement tx, 
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TransactionStatus status) {
        
        //Balance query
        String balanceSql = "SELECT * FROM "+Common.DB_TABLE_MERCHANT_STATEMENT
                +" WHERE merchant_id = :merchant_id "
                +" ORDER BY id DESC LIMIT 1 "
                +" FOR UPDATE";

         MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
         parametersBalanceSql.addValue("merchant_id", tx.getMerchant_id());

        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_STATEMENT+" "
            +" SET `merchant_id`=:merchant_id,"
            +" `gateway_id`=:gateway_id, "
            +" `description`=:description,"
            +" `recorded_by`=:recorded_by,"
            +" `amount`=:amount,"
            +" `tx_type`=:tx_type,"
            +" `narrative`=:narrative,"
            +" `airtelmm_balance`=:airtelmm_balance,"
                +" `safaricom_balance`=:safaricom_balance,"
            +" `sms_balance`=:sms_balance,"
            +" `mtnmm_balance`=:mtnmm_balance";


        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (tx.getTransactions_log_id() > 0) {
            sql += ", transactions_log_id=:transactions_log_id ";
            parameters.addValue("transactions_log_id", tx.getTransactions_log_id());
        }
        parameters.addValue("merchant_id", tx.getMerchant_id());
        parameters.addValue("gateway_id", tx.getGateway_id());
        parameters.addValue("description", tx.getDescription());
        parameters.addValue("amount", tx.getAmount());
        parameters.addValue("tx_type", tx.getTx_type());
        parameters.addValue("narrative", tx.getNarritive());
        parameters.addValue("recorded_by", tx.getRecorded_by());
        

        final String sql_final = sql;
        //TransactionTemplate template = new TransactionTemplate(transactionManager);
        
        try {

            RowMapper rm_b = new RowMapper<Statement>() {
            public Statement mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Statement t = new Statement();
                    t.setId(rs.getLong("id"));
                    t.setAmount(rs.getDouble("amount"));
                    t.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
                    t.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setDescription(rs.getString("description"));
                    t.setMerchant_id(rs.getLong("merchant_id"));
                    t.setNarritive(rs.getString("narrative"));
                    t.setTransactions_log_id(rs.getLong("transactions_log_id"));
                    t.setTx_type(rs.getString("tx_type"));
                    t.setSms_balance(rs.getDouble("sms_balance"));
                    t.setSafaricom_balance(rs.getDouble("safaricom_balance"));
                    //safaricom_balance

                    return t;
                }
            };

            List<Statement> balanceList = jdbcTemplate.query(balanceSql, parametersBalanceSql, rm_b);
            Balance mtn_balance;
            Balance airtel_balance;
            Balance sms_balance;
            Balance safaricom_balance;

            Logger.getLogger(Common.class.getName()).log(Level.INFO,
                    "Working on SMS Balance List: "+balanceList.size());

            if (balanceList.size() > 0) {
                Statement s = balanceList.get(0);
                mtn_balance = new Balance("UGX MTN MM", 
                        s.getMtnmm_balance(), 
                        "MTNMoMoPaymentGateway"   );

                airtel_balance = new Balance("UGX AIRTEL MM", 
                        s.getAirtelmm_balance(), 
                        "AirtelMoneyPaymentGateway"   );
                airtel_balance.setBaseCurrency("UGX");

                safaricom_balance = new Balance("KES MPESA",
                        s.getSafaricom_balance(),
                        "SafariComPaymentGateway"   );
                safaricom_balance.setBaseCurrency("KES");
                
                sms_balance = new Balance("UGX SMS", 
                        s.getSms_balance(), 
                        "SmsGateway"   );
                sms_balance.setBaseCurrency("UGX");

            } else {
                mtn_balance = new Balance("UGX MTN MM", 
                        0.00, 
                        "MTNMoMoPaymentGateway");

                airtel_balance = new Balance("UGX AIRTEL MM", 
                        0.00, 
                        "AirtelMoneyPaymentGateway");
                airtel_balance.setBaseCurrency("UGX");
                
                sms_balance = new Balance("UGX SMS", 
                        0.00, 
                        "SmsGateway"   );
                sms_balance.setBaseCurrency("UGX");

                safaricom_balance = new Balance("KES MPESA",
                        0.00,
                        "SafariComPaymentGateway"   );
                safaricom_balance.setBaseCurrency("KES");
            }

            //New balance
            if (tx.getTx_type().contains("CR")) {
                if (balance_type.equals("mtnmm_balance")) {
                    Double nBalance = tx.getAmount() + mtn_balance.getAmount();
                    parameters.addValue("mtnmm_balance", nBalance);
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                if (balance_type.equals("airtelmm_balance")) {
                    Double nBalance = tx.getAmount() + airtel_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", nBalance);
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                if (balance_type.equals("sms_balance")) {
                    Double nBalance = tx.getAmount() + sms_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", nBalance);
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }

                if (balance_type.equals("safaricom_balance")) {
                    Double nBalance = tx.getAmount() + safaricom_balance.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", nBalance);
                }
            } else {
                if (balance_type.equals("mtnmm_balance")) {
                    //Check if there is enough balance for this transaction
                    if (tx.getAmount() > mtn_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException
                                .getError("111", 
                                        String.format(GeneralException.ERRORS_111, 
                                                mtn_balance.getAmount(), 
                                                mtn_balance.getCode()));
                    }
                    Double nBalance =  mtn_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", nBalance);
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }

                if (balance_type.equals("airtelmm_balance")) {
                    if (tx.getAmount() > airtel_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException
                                .getError("111", 
                                        String.format(GeneralException.ERRORS_111, 
                                                airtel_balance.getAmount(), 
                                                airtel_balance.getCode()));
                    }
                    Double nBalance = airtel_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", nBalance);
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }

                if (balance_type.equals("safaricom_balance")) {
                    if (tx.getAmount() > safaricom_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException
                                .getError("111",
                                        String.format(GeneralException.ERRORS_111,
                                                safaricom_balance.getAmount(),
                                                safaricom_balance.getCode()));
                    }
                    Double nBalance = safaricom_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", sms_balance.getAmount());
                    parameters.addValue("safaricom_balance", nBalance);
                }
                
                if (balance_type.equals("sms_balance")) {
                    if (tx.getAmount() > sms_balance.getAmount()) {
                        status.setRollbackOnly();
                        return GeneralException
                                .getError("111", 
                                        String.format(GeneralException.ERRORS_111, 
                                                sms_balance.getAmount(), 
                                                sms_balance.getCode()));
                    }
                    Double nBalance = sms_balance.getAmount() - tx.getAmount();
                    parameters.addValue("mtnmm_balance", mtn_balance.getAmount());
                    parameters.addValue("airtelmm_balance", airtel_balance.getAmount());
                    parameters.addValue("sms_balance", nBalance);
                    parameters.addValue("safaricom_balance", safaricom_balance.getAmount());
                }
                //More balances
            }

            KeyHolder keyHolder = new GeneratedKeyHolder();
            //long userId;
            jdbcTemplate.update(sql_final, parameters, keyHolder);
            //Now insert privileges
            BigInteger statementId = (BigInteger)keyHolder.getKey();

            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            status.setRollbackOnly();
            return GeneralException
                .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    public static void sendEmailOnUpdatingMerchantUserPassword(MerchantUser u, 
            String password, 
            NamedParameterJdbcTemplate jdbcTemplate){
        //Now send verification email
        Setting emailContentManage = Common.getSettings("email_tmp_on_creating_merchant_user", jdbcTemplate);
        String emailContent_ = emailContentManage.getSetting_value()
                .replace("{name}", u.getName());
        emailContent_ = emailContent_.replace("{url}", appBaseUrl);
        emailContent_ = emailContent_.replace("{merchant_number}", u.getMerchant_number());
        emailContent_ = emailContent_.replace("{username}", u.getEmail());
        final String emailContent = emailContent_.replace("{password}", password);

        final String subject = "Merchant User Credentials";
        final String to = u.getEmail();

        Thread thread = new Thread(){
            public void run(){
                SendMail mail = new SendMail();
                mail.sendSimpleMessage(to, 
                subject, 
                emailContent);
            }
        };
        thread.start();
    }
    
    /*
    * DoPayIn makes a payin transaction.
    */
    public static String doPayIn(Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        
        //First check if there is tx with this reference on merchant.
        Transaction tx = Common.getMerchantTxByTheirRef(newTx.getTx_merchant_ref(), merchant.getId()+"",
        jdbcTemplate);
        if (tx != null) {
            return GeneralException
                .getError("121", String.format(GeneralException.ERRORS_121, 
                        newTx.getTx_merchant_ref()));
        }
        
        Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
        Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
        Setting getSuspenseAccount = Common.getSettings("suspense_account", jdbcTemplate);
        
        String stock_account_number = getStockAccount.getSetting_value().trim();
        String suspense_account_number = getSuspenseAccount.getSetting_value().trim();
        String revenue_account_number = getRevenueAccount.getSetting_value().trim();
        
        Merchant float_stock_account = Common.getMerchantByAccountNumber(
                    stock_account_number,
                    jdbcTemplate);
        
        Merchant suspense_stock_account = Common.getMerchantByAccountNumber(
                    suspense_account_number,
                    jdbcTemplate);
        
        Merchant revenue_stock_account = Common.getMerchantByAccountNumber(
                    revenue_account_number,
                    jdbcTemplate);
        
        String[] bType = Balance.getBalanceTypeByGatewayId(newTx.getGateway_id());
            String balance_type = bType[0];


        MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
        parametersBalanceSql.addValue("merchant_id", merchant.getId());

        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" ";
        String sql_set = " SET `merchant_id`=:merchant_id,"
            +" `gateway_id`=:gateway_id, "
            +" `original_amount`=:original_amount,"
            +" `tx_type`=:tx_type,"
            +" `charges`=:charges,"
            +" `tx_description`=:tx_description,"
            +" `tx_merchant_description`=:tx_merchant_description,"
            +" `tx_unique_id`=:tx_unique_id,"
            +" `tx_gateway_ref`=:tx_gateway_ref,"
            +" `tx_merchant_ref`=:tx_merchant_ref,"
            +" `payer_number`=:payer_number,"
            +" `tx_request_trace`=:tx_request_trace,"
            +" `tx_update_trace`=:tx_update_trace,"
            +" `charging_method`=:charging_method,"
            +" `status`=:status,"
            +" `callback_url`=:callback_url,"
            +" `originate_ip`=:originate_ip,"
                +"`safaricom_request_reference`=:safaricom_request_reference,"
            +" `tx_cost`=:tx_cost";

        final String sql_insert = sql+sql_set;

        String sql_update = " UPDATE "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" ";


        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant.getId());
        parameters.addValue("gateway_id", newTx.getGateway_id());
        parameters.addValue("tx_description", newTx.getTx_merchant_description());
        parameters.addValue("tx_merchant_description", newTx.getTx_merchant_description());
        parameters.addValue("original_amount", newTx.getOriginal_amount());
        parameters.addValue("tx_type", newTx.getTx_type());
        parameters.addValue("tx_cost", newTx.getTx_cost());

        parameters.addValue("tx_cost", newTx.getTx_cost());
        parameters.addValue("status", newTx.getStatus());
        parameters.addValue("charging_method", newTx.getCharging_method());
        parameters.addValue("payer_number", newTx.getPayer_number());
        parameters.addValue("tx_merchant_ref", newTx.getTx_merchant_ref());
        parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
        parameters.addValue("tx_unique_id", newTx.getTx_unique_id());
        parameters.addValue("charges", newTx.getCharges());

        parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
        parameters.addValue("tx_update_trace", newTx.getTx_update_trace());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("originate_ip", newTx.getOriginate_ip());
        parameters.addValue("safaricom_request_reference", "");

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    //long userId;
                    jdbcTemplate.update(sql_insert, parameters, keyHolder);
                    //Now insert privileges
                    BigInteger txId = (BigInteger)keyHolder.getKey();
                    newTx.setId(txId.longValue());

                    return "success";
                } catch (Exception e) {
                    //transactionManager.rollback(status);
                    status.setRollbackOnly();
                    Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: "+e.getMessage(), "");
                    return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
                }
            }
        });

        if (result.equals("success")) {
            //Now make the actual transaction
            DoPayGateway gw = new DoPayGateway();

            final GateWayResponse pResponse = gw.runPayGatewayDoPayIn(jdbcTemplate,
                    newTx.getPayer_number(),
                    newTx.getOriginal_amount(), 
                    newTx.getTx_unique_id(), 
                    newTx.getTx_description());

            if (pResponse != null ) {
                String trace = pResponse.getRequestTrace();
                //Now update this transaction in DB
                newTx.setTx_request_trace(trace);
                newTx.setStatus(pResponse.getTransactionStatus());
                newTx.setTx_gateway_ref(pResponse.getNetworkId());
                if (!pResponse.getSafaricomRequestReference().isEmpty()) {
                    newTx.setSafaricomRequestReference(pResponse.getSafaricomRequestReference());
                }

                String sql_update_final =  sql_update+sql_set+" WHERE id='"+newTx.getId()+"'";

                //Update parameters
                parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
                parameters.addValue("status", newTx.getStatus());
                parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
                if (!newTx.getSafaricomRequestReference().isEmpty()) {
                    parameters.addValue("safaricom_request_reference", newTx.getSafaricomRequestReference());
                }

                result = template.execute(new TransactionCallback<String>() {
                    @Override
                    public String doInTransaction(TransactionStatus status) {
                        try {

                            jdbcTemplate.update(sql_update_final, parameters);
                            String res_string = "";
                            
                            if (pResponse.getTransactionStatus().equals("SUCCESSFUL")) {
                                //Credit this customer's account.
                                Statement newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());
                                newTxS.setNarritive(newTx.getTx_type());
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(Long.parseLong(newTx.getMerchant_id()));
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");

                                res_string = Common.recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {
                                    return res_string;
                                }

                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYIN_CHARGE);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(merchant.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("DR");

                                res_string = Common.recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {
                                    return res_string;
                                }

                                //Now record this revenue account.
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYIN_REVENUE);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(revenue_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");

                                res_string = Common.recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {
                                    return res_string;
                                } 

                                //Now increase stock account.
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYIN);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(float_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");

                                res_string = Common.recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {
                                    return res_string;
                                }
                            }
                            
                            return "success";
                        } catch (Exception e) {
                            //transactionManager.rollback(status);
                            status.setRollbackOnly();
                            Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: "+e.getMessage(), "");
                            return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                        }
                    }
                });
                if (result.equals("success")) {
                    pResponse.setOurUniqueTxId(newTx.getTx_unique_id());
                    return GeneralSuccessResponse
                        .getApiTxMessage("000", 
                                GeneralSuccessResponse.SUCCESS_000, 
                                pResponse);
                } else {
                    return result;
                }
            } else {
                GateWayResponse pResponse_ = new GateWayResponse();
                pResponse_.setHttpStatus("");
                pResponse_.setStatus("ERROR");
                pResponse_.setRequestTrace("");
                pResponse_.setTransactionStatus("");
                pResponse_.setNetworkId("");
                pResponse_.setMessage(GeneralException.ERRORS_102);
                return GeneralException
                    .getApiTxMessage("102", GeneralException.ERRORS_102, 
                            pResponse_);
            }
        } else {
            return result;
        }
    }
    
    /*
    * DoPayOut makes a payout transaction.
    */
    public static String doPayOut(Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        
        Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
        Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
        Setting getSuspenseAccount = Common.getSettings("suspense_account", jdbcTemplate);
        
        String stock_account_number = getStockAccount.getSetting_value().trim();
        String suspense_account_number = getSuspenseAccount.getSetting_value().trim();
        String revenue_account_number = getRevenueAccount.getSetting_value().trim();
        
        Merchant float_stock_account = Common.getMerchantByAccountNumber(
                    stock_account_number,
                    jdbcTemplate);
        
        Merchant suspense_stock_account = Common.getMerchantByAccountNumber(
                    suspense_account_number,
                    jdbcTemplate);
        
        Merchant revenue_stock_account = Common.getMerchantByAccountNumber(
                    revenue_account_number,
                    jdbcTemplate);
        
        //First check if there is tx with this reference on merchant.
        Transaction tx = Common.getMerchantTxByTheirRef(newTx.getTx_merchant_ref(), merchant.getId()+"",
        jdbcTemplate);
        if (tx != null) {
            return GeneralException
                .getError("121", String.format(GeneralException.ERRORS_121, 
                        newTx.getTx_merchant_ref()));
        }

        MapSqlParameterSource parametersBalanceSql = new MapSqlParameterSource();
        parametersBalanceSql.addValue("merchant_id", merchant.getId());

        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" ";
        String sql_set = " SET `merchant_id`=:merchant_id,"
            +" `gateway_id`=:gateway_id, "
            +" `original_amount`=:original_amount,"
            +" `tx_type`=:tx_type,"
            +" `charges`=:charges,"
            +" `tx_description`=:tx_description,"
            +" `tx_merchant_description`=:tx_merchant_description,"
            +" `tx_unique_id`=:tx_unique_id,"
            +" `tx_gateway_ref`=:tx_gateway_ref,"
            +" `tx_merchant_ref`=:tx_merchant_ref,"
            +" `payer_number`=:payer_number,"
            +" `tx_request_trace`=:tx_request_trace,"
            +" `tx_update_trace`=:tx_update_trace,"
            +" `charging_method`=:charging_method,"
            +" `status`=:status,"
            +" `callback_url`=:callback_url,"
            +" `originate_ip`=:originate_ip,"
                +" `safaricom_request_reference`=:safaricom_request_reference,"
            +" `tx_cost`=:tx_cost";
        
        if (newTx.getMerchant_batch_transactions_log_id() != null 
                && newTx.getMerchant_batch_transactions_log_id() > 0) {
            sql_set += ", merchant_batch_transactions_log_id=:batch_id ";
        }
        
        if (newTx.getBeneficiary_id() != null 
                && newTx.getBeneficiary_id() > 0) {
            sql_set += ", beneficiary_id=:beneficiary_id ";
        }

        final String sql_insert = sql+sql_set;

        String sql_update = " UPDATE "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" ";


        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant.getId());
        parameters.addValue("gateway_id", newTx.getGateway_id());
        parameters.addValue("tx_description", newTx.getTx_merchant_description());
        parameters.addValue("tx_merchant_description", newTx.getTx_merchant_description());
        parameters.addValue("original_amount", newTx.getOriginal_amount());
        parameters.addValue("tx_type", newTx.getTx_type());
        parameters.addValue("tx_cost", newTx.getTx_cost());

        parameters.addValue("tx_cost", newTx.getTx_cost());
        parameters.addValue("status", newTx.getStatus());
        parameters.addValue("charging_method", newTx.getCharging_method());
        parameters.addValue("payer_number", newTx.getPayer_number());
        parameters.addValue("tx_merchant_ref", newTx.getTx_merchant_ref());
        parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());
        parameters.addValue("tx_unique_id", newTx.getTx_unique_id());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("safaricom_request_reference", newTx.getSafaricomRequestReference());

        parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
        parameters.addValue("tx_update_trace", newTx.getTx_update_trace());
        parameters.addValue("charges", newTx.getCharges());
        parameters.addValue("callback_url", newTx.getCallback_url());
        parameters.addValue("originate_ip", newTx.getOriginate_ip());
        
        if (newTx.getMerchant_batch_transactions_log_id() != null 
                && newTx.getMerchant_batch_transactions_log_id() > 0) {
            parameters.addValue("batch_id", newTx.getMerchant_batch_transactions_log_id());
        }
        
        if (newTx.getBeneficiary_id() != null 
                && newTx.getBeneficiary_id() > 0) {
            parameters.addValue("beneficiary_id", newTx.getBeneficiary_id());
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {
                    String result = "";
                    KeyHolder keyHolder = new GeneratedKeyHolder();
                    //long userId;
                    jdbcTemplate.update(sql_insert, parameters, keyHolder);
                    //Now insert privileges
                    BigInteger txId = (BigInteger)keyHolder.getKey();
                    newTx.setId(txId.longValue());

                    //Remove the charges and put them to suspense account

                    return "success";
                } catch (Exception e) {
                    //transactionManager.rollback(status);
                    status.setRollbackOnly();
                    Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR - SAVING TX: "+e.getMessage(), "");
                    return GeneralException
                        .getError("102", GeneralException.ERRORS_102);
                }
            }
        });

        if (result.equals("success")) {

            //Transfer the amount to suspense account
            String[] bType = Balance.getBalanceTypeByGatewayId(newTx.getGateway_id());
            String balance_type = bType[0];

            Statement newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getOriginal_amount());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(Long.parseLong(newTx.getMerchant_id()));
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("DR");

            result = Common.recordStatementTx(newTxStatement, 
                    balance_type,
                    jdbcTemplate,
                    transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            //Reduce the float stock account
            newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getOriginal_amount());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(float_stock_account.getId());
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("DR");

            result = Common.recordStatementTx(newTxStatement, 
                    balance_type,
                    jdbcTemplate,
                    transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            //Credit the suspense account.
            newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getOriginal_amount());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(suspense_stock_account.getId());
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("CR");

            result = Common.recordStatementTx(newTxStatement, 
                    balance_type,
                    jdbcTemplate,
                    transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            //Dr account for this transaction's charge
            newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getCharges());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(Long.parseLong(newTx.getMerchant_id()));
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("DR");

            result = Common.recordStatementTx(newTxStatement, 
                    balance_type,
                    jdbcTemplate,
                    transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            //Transfer charges to suspense account
            newTxStatement = new Statement();
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setAmount(newTx.getCharges());
            newTxStatement.setGateway_id(newTx.getGateway_id());
            newTxStatement.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE);
            newTxStatement.setTransactions_log_id(newTx.getId());
            newTxStatement.setMerchant_id(suspense_stock_account.getId());
            newTxStatement.setDescription(newTx.getTx_description());
            newTxStatement.setRecorded_by("SYSTEM");
            newTxStatement.setTx_type("CR");

            result = Common.recordStatementTx(newTxStatement, 
                    balance_type,
                    jdbcTemplate,
                    transactionManager);
            if (!result.equals("success")) {
                return result;
            }

            //Now make the actual transaction
            DoPayGateway gw = new DoPayGateway();
            final GateWayResponse pResponse = gw.runPayGatewayDoPayOut(jdbcTemplate,
                    newTx.getPayer_number(),
                    newTx.getOriginal_amount(), 
                    newTx.getTx_unique_id(), 
                    newTx.getTx_description());

            if (pResponse != null ) {
                String trace = pResponse.getRequestTrace();
                //Now update this transaction in DB
                newTx.setTx_request_trace(trace);
                newTx.setStatus(pResponse.getTransactionStatus());
                newTx.setTx_gateway_ref(pResponse.getNetworkId());
                newTx.setSafaricomRequestReference(pResponse.getSafaricomRequestReference());

                String sql_update_final =  sql_update+sql_set+" WHERE id='"+newTx.getId()+"'";

                //Update parameters
                parameters.addValue("safaricom_request_reference", newTx.getSafaricomRequestReference());
                parameters.addValue("tx_request_trace", newTx.getTx_request_trace());
                parameters.addValue("status", newTx.getStatus());
                parameters.addValue("tx_gateway_ref", newTx.getTx_gateway_ref());

                result = template.execute(new TransactionCallback<String>() {
                    @Override
                    public String doInTransaction(TransactionStatus status) {
                        try {

                            jdbcTemplate.update(sql_update_final, parameters);
                            
                            String res_string = "";
                            //If the transaction failed, the reverse the funds
                            if (pResponse.getTransactionStatus().equals("FAILED")) {



                                //Dr the amount on suspense account
                                Statement newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(suspense_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("DR");

                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //DR the charge reversal
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(suspense_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("DR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //CR the amount back to customer's account
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(merchant.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //CR the charge back on customer's account
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(merchant.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //Restore the float account
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(float_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR - TX STATUS UPDATE: "+pResponse.toString(), "");
                            } else if (pResponse.getTransactionStatus().equals("SUCCESSFUL")) {
                                //Record a settlement transaction for Payout
                                
                                Statement newTxS = new Statement();
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getOriginal_amount());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_SETTLEMENT);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(suspense_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("DR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //Record a settlement transaction for Payout charge
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_SETTLEMENT);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(suspense_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("DR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }

                                //Record Revenue to revenue account
                                newTxS = new Statement();
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setAmount(newTx.getCharges());
                                newTxS.setGateway_id(newTx.getGateway_id());

                                newTxS.setNarritive(Transaction.TX_TYPE_PAYOUT_REVENUE);
                                newTxS.setTransactions_log_id(newTx.getId());
                                newTxS.setMerchant_id(revenue_stock_account.getId());
                                newTxS.setDescription(newTx.getTx_description());
                                newTxS.setRecorded_by("SYSTEM");
                                newTxS.setTx_type("CR");
                                res_string = recordStatementTx(newTxS, 
                                        balance_type,
                                        jdbcTemplate,
                                        transactionManager);
                                if (!res_string.equals("success")) {

                                    return res_string;
                                }
                            }
                            
                            return "success";
                        } catch (Exception e) {
                            e.printStackTrace();
                            Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR - SAVING TX UPDATE: "+e.getStackTrace(), "");
                            
                            //transactionManager.rollback(status);
                            status.setRollbackOnly();
                            return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                        }
                    }
                });

                if (result.equals("success")) {
                    
                    
                    pResponse.setOurUniqueTxId(newTx.getTx_unique_id());
                    pResponse.setSafaricomRequestReference(newTx.getSafaricomRequestReference());
                    return GeneralSuccessResponse
                        .getApiTxMessage("000", 
                                GeneralSuccessResponse.SUCCESS_000, 
                                pResponse);
                } else {
                    return result;
                }
                
            } else {
                GateWayResponse pResponse_ = new GateWayResponse();
                pResponse_.setHttpStatus("0");
                pResponse_.setStatus("ERROR");
                pResponse_.setRequestTrace("");
                pResponse_.setTransactionStatus("UNDETERMINED");
                pResponse_.setNetworkId("");
                pResponse_.setMessage(GeneralException.ERRORS_102);
                return GeneralException
                    .getApiTxMessage("102", GeneralException.ERRORS_102, 
                            pResponse_);
            }

        } else {
            return result;
        }
    }
    
    /*Returns a hash string*/
    public static String getSha256EncodedString(String originalString) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(originalString.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encodedhash);
    }
    
    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
        String hex = Integer.toHexString(0xff & hash[i]);
        if(hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
    
    
    public static RowMapper getTransactionRowMapper() {
        RowMapper rm = new RowMapper<Transaction>() {
            public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Transaction t = new Transaction();
                    t.setId(rs.getLong("id"));
                    t.setCharging_method(rs.getString("charging_method"));
                    t.setCharges(rs.getDouble("charges"));
                    t.setOriginal_amount(rs.getDouble("original_amount"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setStatus(rs.getString("status"));
                    t.setMerchant_id(rs.getString("merchant_id"));
                    t.setTx_description(rs.getString("tx_description"));
                    t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                    t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                    t.setTx_request_trace(rs.getString("tx_request_trace"));
                    t.setTx_unique_id(rs.getString("tx_unique_id"));
                    t.setTx_update_trace(rs.getString("tx_update_trace"));
                    t.setPayer_number(rs.getString("payer_number"));
                    t.setTx_type(rs.getString("tx_type"));
                    t.setCallback_trace(rs.getString("callback_trace"));
                    t.setTx_merchant_ref(rs.getString("tx_merchant_ref"));
                    return t;
                }
            };
        return rm;
    }
    
    public static String getExtensionByStringHandling(String filename) {
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i+1);
        } else {
            return "";
        }
    }
    
    public static String getIpAddress(HttpServletRequest request) {
        String remoteAdr = "";
        String remoteProxyClient = "";
        String http_x_forwarded_for = "";
        String http_client_ip_addr = "";
        String wl_proxy_client_ip_addr = "";
        if(request != null){
            remoteAdr = request.getHeader("X-FORWADED-FOR");
            if (remoteAdr == null || "".equals(remoteAdr)) {
                remoteAdr = request.getRemoteAddr();
            }
            remoteProxyClient = request.getHeader("Proxy-Client-IP");
            if (remoteProxyClient == null) {
                remoteAdr += "<==>"+remoteProxyClient;
            }
            
            http_x_forwarded_for = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (http_x_forwarded_for == null) {
                remoteAdr += "<==>"+http_x_forwarded_for;
            }
            
            http_client_ip_addr = request.getHeader("HTTP_CLIENT_IP");
            if (http_client_ip_addr == null) {
                remoteAdr += "<==>"+http_client_ip_addr;
            }
            
            wl_proxy_client_ip_addr = request.getHeader("WL-Proxy-Client-IP");
            if (wl_proxy_client_ip_addr == null) {
                remoteAdr += "<==>"+wl_proxy_client_ip_addr;
            }
        }
        return remoteAdr;
    }
    
    
    public static String urlEncodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException(ex.getCause());
        }
    }
    
    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    //
    public static String updateTx(Transaction tx,
                                NamedParameterJdbcTemplate jdbcTemplate,
                                PlatformTransactionManager transactionManager) {

        //First check if stock|revenew|suspense accounts were configured transaction
        Setting getStockAccount = Common.getSettings("float_stock_account", jdbcTemplate);
        if (getStockAccount == null || getStockAccount.getSetting_value().isEmpty()) {
            // release lock
            return GeneralException
                    .getError("112", GeneralException.ERRORS_112);
        }

        Setting getRevenueAccount = Common.getSettings("revenue_account", jdbcTemplate);
        if (getRevenueAccount == null || getStockAccount.getSetting_value().isEmpty()) {
            // release lock
            return GeneralException
                    .getError("117", GeneralException.ERRORS_117);
        }

        Setting getSuspenseAccount = Common.getSettings("suspense_account", jdbcTemplate);
        if (getSuspenseAccount == null || getStockAccount.getSetting_value().isEmpty()) {
            // release lock
            return GeneralException
                    .getError("127", GeneralException.ERRORS_127);
        }

        //Now get Stock account
        String stock_account_number = getStockAccount.getSetting_value().trim();
        Merchant float_stock_account = Common.getMerchantByAccountNumber(
                stock_account_number,
                jdbcTemplate);

        //Now get Revenue account
        String revenue_account_number = getRevenueAccount.getSetting_value().trim();
        Merchant revenue_stock_account = Common.getMerchantByAccountNumber(
                revenue_account_number,
                jdbcTemplate);

        //suspense_account
        String suspense_account_number = getSuspenseAccount.getSetting_value().trim();
        Merchant suspense_stock_account = Common.getMerchantByAccountNumber(
                suspense_account_number,
                jdbcTemplate);


        DoPayGateway gwChargingDetails = new DoPayGateway();

        String tx_type = "";
        if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYIN)) {
            tx_type = "collection";
        } else {
            tx_type = "disbursement";
        }

        GateWayResponse txUpdatedDetails;
        if (!tx.isFinalStatusSet()) {
            txUpdatedDetails = gwChargingDetails.runPayGatewayDoCheckStatus(
                    jdbcTemplate,
                    tx.getGateway_id(),
                    tx.getTx_unique_id(),
                    tx_type
            );
        } else {
            txUpdatedDetails = new GateWayResponse();
            txUpdatedDetails.setTransactionStatus(tx.getStatus());
            txUpdatedDetails.setMessage("Updated from callback");
            txUpdatedDetails.setHttpStatus("200");
            txUpdatedDetails.setMessage("Updated from callback");
            txUpdatedDetails.setStatus("OK");
            txUpdatedDetails.setNetworkId(tx.getTx_gateway_ref());
            txUpdatedDetails.setRequestTrace(tx.getTx_update_trace());
        }

        String sql_update = " UPDATE "+Common.DB_TABLE_MERCHANT_TRANSACTION_LOG+" "
                + " SET status=:status, tx_update_trace=:tx_update_trace, "
                + " tx_gateway_ref=:tx_gateway_ref ";

        if (txUpdatedDetails != null ) {

            if (txUpdatedDetails.getTransactionStatus().isEmpty()) {
                Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE,
                        "Empty Tx Status: "+txUpdatedDetails.getRequestTrace(), "");
                return "success";
            }

            MapSqlParameterSource parameters_ = new MapSqlParameterSource();
            tx.setTx_update_trace(txUpdatedDetails.getRequestTrace());
            tx.setStatus(txUpdatedDetails.getTransactionStatus());
            tx.setTx_gateway_ref(txUpdatedDetails.getNetworkId());

            final String sql_update_final =  sql_update+" WHERE id='"+tx.getId()+"'";
            parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
            parameters_.addValue("status", tx.getStatus());
            parameters_.addValue("tx_gateway_ref", tx.getTx_gateway_ref());

            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {
                        jdbcTemplate.update(sql_update_final, parameters_);
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        Logger.getLogger(AuthenticationController.class.getName())
                                .log(Level.SEVERE, "INTERNAL ERROR: "+e.getMessage(), "");
                        return GeneralException
                                .getError("102", GeneralException.ERRORS_102);
                    }
                }
            });



            if (result.equals("success")) {
                Merchant merchant = Common.getMerchantById(tx.getMerchant_id(), jdbcTemplate);

                //If the transaction SUCCEEDED, then CREDIT THE CUSTOMER'S ACCOUNT
                if (txUpdatedDetails.getTransactionStatus().equals("SUCCESSFUL")) {

                    //Send callback request on another thread
                    if (tx.getCallback_url() != null && !tx.getCallback_url().isEmpty()) {
                        Thread thread = new Thread(){
                            public void run(){
                                String amountToSign = tx.getOriginal_amount()+"";
                                String signedData = tx.getPayer_number()+amountToSign
                                        +tx.getCreated_on()+tx.getTx_merchant_ref()+tx.getStatus()
                                        +tx.getTx_merchant_description()+tx.getTx_gateway_ref();

                                //String signedData_ = Common.generateSha256String(signedData);

                                if (merchant.getPrivate_key()== null || merchant.getPrivate_key().isEmpty()) {
                                    return;
                                }
                                try {
                                    //Now verify signature.
                                    Signature sign = Signature.getInstance("SHA256withRSA");
                                    String base64_private_key = merchant.getPrivate_key();
                                    base64_private_key = base64_private_key.replace("-----BEGIN PRIVATE KEY-----\n", "");
                                    String base64_cleaned = base64_private_key.replace("\n-----END PRIVATE KEY-----\n", "");

                                    PrivateKey privateKey = Common.getPrivateKeyFromBase64String(base64_cleaned);
                                    sign.initSign(privateKey);
                                    sign.update(signedData.getBytes());
                                    byte[] digitalSignature = sign.sign();
                                    JSONObject jObject = new JSONObject();
                                    jObject.put("amount", amountToSign);
                                    jObject.put("payer_number", tx.getPayer_number());
                                    jObject.put("reference", tx.getTx_merchant_ref());
                                    jObject.put("network_ref", tx.getTx_gateway_ref());
                                    jObject.put("status", tx.getStatus());
                                    jObject.put("description", tx.getTx_merchant_description());
                                    jObject.put("completed_on", tx.getUpdated_on());
                                    jObject.put("created_on", tx.getCreated_on());
                                    jObject.put("SignedData", signedData);
                                    jObject.put("signature", Base64.getEncoder().encodeToString(digitalSignature));


                                    String requestData = jObject.toString();
                                    String url = tx.getCallback_url();
                                    //Now make the callback request.
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("Content-Type", "application/json");

                                    HttpRequestResponse rs = Common.doHttpRequest("POST", url, requestData, headers);
                                    if (rs != null) {
                                        String sql_update_final =  sql_update+", callback_trace=:callback_trace "
                                                + " WHERE id='"+tx.getId()+"'";
                                        MapSqlParameterSource parameters_ = new MapSqlParameterSource();
                                        parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
                                        parameters_.addValue("status", tx.getStatus());
                                        parameters_.addValue("tx_gateway_ref", tx.getTx_gateway_ref());
                                        parameters_.addValue("callback_trace", rs.toString());

                                        //Now update the trace of this transaction.
                                        String result = template.execute(new TransactionCallback<String>() {
                                            @Override
                                            public String doInTransaction(TransactionStatus status) {
                                                try {
                                                    jdbcTemplate.update(sql_update_final, parameters_);
                                                    return "success";
                                                } catch (Exception e) {
                                                    //transactionManager.rollback(status);
                                                    status.setRollbackOnly();
                                                    Logger.getLogger(AuthenticationController.class.getName())
                                                            .log(Level.SEVERE, "INTERNAL ERROR: "+e.getMessage(), "");
                                                    return GeneralException
                                                            .getError("102", GeneralException.ERRORS_102);
                                                }
                                            }
                                        });
                                        Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, "Callback Results: "+result, "");
                                    }

                                } catch (NoSuchAlgorithmException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (InvalidKeyException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (SignatureException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (JSONException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                //System.out.println("Thread Running");
                            }
                        };
                        thread.start();
                    }

                    //Record this transaction
                    String[] bType = Balance.getBalanceTypeByGatewayId(tx.getGateway_id());
                    String balance_type = bType[0];

                    Statement newTx = new Statement();

                    //Record the charge and update stock and revenue account
                    if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYIN)) {
                        //Credit this customer's account.
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());
                        newTx.setNarritive(tx.getTx_type());
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(Long.parseLong(tx.getMerchant_id()));
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");

                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);
                        if (!result.equals("success")) {
                            // release lock
                            return result;
                        }

                        newTx = new Statement();
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYIN_CHARGE);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(merchant.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("DR");

                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);
                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //Now record this revenue account.
                        newTx = new Statement();
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYIN_REVENUE);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(revenue_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //Now increase stock account.
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYIN);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(float_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }
                    } else if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYOUT)) {
                        //Record a settlement transaction for Payout
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_SETTLEMENT);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(suspense_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("DR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //Record a settlement transaction for Payout charge
                        newTx = new Statement();
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_SETTLEMENT);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(suspense_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("DR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //Record Revenue to revenue account
                        newTx = new Statement();
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVENUE);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(revenue_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                    }
                } else if (txUpdatedDetails.getTransactionStatus().equals("FAILED")) {

                    //Send callback request on another thread
                    if (tx.getCallback_url() != null && !tx.getCallback_url().isEmpty()) {
                        Thread thread = new Thread() {
                            public void run() {
                                String signedData = tx.getPayer_number() + tx.getOriginal_amount()
                                        + tx.getCreated_on() + tx.getTx_merchant_ref() + tx.getStatus()
                                        + tx.getTx_merchant_description() + tx.getTx_gateway_ref();

                                if (merchant.getPublic_key() == null || merchant.getPublic_key().isEmpty()) {
                                    return;
                                }
                                try {
                                    //Now verify signature.
                                    Signature sign = Signature.getInstance("SHA256withRSA");
                                    String base64_private_key = merchant.getPrivate_key();
                                    base64_private_key = base64_private_key.replace("-----BEGIN PRIVATE KEY-----\n", "");
                                    String base64_cleaned = base64_private_key.replace("\n-----END PRIVATE KEY-----\n", "");

                                    PrivateKey privateKey = Common.getPrivateKeyFromBase64String(base64_cleaned);
                                    sign.initSign(privateKey);
                                    sign.update(signedData.getBytes());
                                    byte[] digitalSignature = sign.sign();
                                    JSONObject jObject = new JSONObject();
                                    jObject.put("amount", tx.getOriginal_amount());
                                    jObject.put("payer_number", tx.getPayer_number());
                                    jObject.put("reference", tx.getTx_merchant_ref());
                                    jObject.put("network_ref", tx.getTx_gateway_ref());
                                    jObject.put("status", tx.getStatus());
                                    jObject.put("description", tx.getTx_merchant_description());
                                    jObject.put("completed_on", tx.getUpdated_on());
                                    jObject.put("created_on", tx.getCreated_on());
                                    jObject.put("signature", Base64.getEncoder().encodeToString(digitalSignature));
                                    String requestData = jObject.toString();
                                    String url = tx.getCallback_url();
                                    //Now make the callback request.
                                    Map<String, String> headers = new HashMap<>();
                                    headers.put("Content-Type", "application/json");

                                    HttpRequestResponse rs = Common.doHttpRequest("POST", url, requestData, headers);
                                    if (rs != null) {
                                        String sql_update_final = sql_update + ", callback_trace=:callback_trace "
                                                + " WHERE id='" + tx.getId() + "'";
                                        MapSqlParameterSource parameters_ = new MapSqlParameterSource();
                                        parameters_.addValue("tx_update_trace", tx.getTx_update_trace());
                                        parameters_.addValue("status", tx.getStatus());
                                        parameters_.addValue("tx_gateway_ref", tx.getTx_gateway_ref());
                                        parameters_.addValue("callback_trace", rs.toString());

                                        //Now update the trace of this transaction.
                                        String result = template.execute(new TransactionCallback<String>() {
                                            @Override
                                            public String doInTransaction(TransactionStatus status) {
                                                try {
                                                    jdbcTemplate.update(sql_update_final, parameters_);
                                                    return "success";
                                                } catch (Exception e) {
                                                    //transactionManager.rollback(status);
                                                    status.setRollbackOnly();
                                                    Logger.getLogger(AuthenticationController.class.getName())
                                                            .log(Level.SEVERE, "INTERNAL ERROR: " + e.getMessage(), "");
                                                    return GeneralException
                                                            .getError("102", GeneralException.ERRORS_102);
                                                }
                                            }
                                        });
                                        Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, "Callback Results: " + result, "");
                                    }

                                } catch (NoSuchAlgorithmException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (InvalidKeyException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (SignatureException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                } catch (JSONException ex) {
                                    Logger.getLogger(TransactionsLogController.class.getName()).log(Level.SEVERE, null, ex);
                                }
                                //System.out.println("Thread Running");
                            }
                        };
                        thread.start();
                    }

                    //If it's a payout, reverse the money.
                    Statement newTx = new Statement();
                    String[] bType = Balance.getBalanceTypeByGatewayId(tx.getGateway_id());
                    String balance_type = bType[0];
                    if (tx.getTx_type().equals(Transaction.TX_TYPE_PAYOUT)) {
                        //Dr the amount
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(suspense_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("DR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //DR the charge reversal
                        newTx = new Statement();
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(suspense_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("DR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //CR the amount back to customer's account
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(merchant.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //CR the charge back on customer's account
                        newTx = new Statement();
                        newTx.setAmount(tx.getCharges());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_CHARGE_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(merchant.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }

                        //Restore the float account
                        newTx = new Statement();
                        newTx.setAmount(tx.getOriginal_amount());
                        newTx.setGateway_id(tx.getGateway_id());

                        newTx.setNarritive(Transaction.TX_TYPE_PAYOUT_REVERSAL);
                        newTx.setTransactions_log_id(tx.getId());
                        newTx.setMerchant_id(float_stock_account.getId());
                        newTx.setDescription(tx.getTx_description());
                        newTx.setRecorded_by("SYSTEM");
                        newTx.setTx_type("CR");
                        result = recordStatementTx(newTx, balance_type, jdbcTemplate, transactionManager);

                        if (!result.equals("success")) {
                            // release lock
                            //lock.release();
                            //writer.close();
                            return result;
                        }
                    }
                }
                return result;
            } else {
                // release lock
                //lock.release();
                //close the file
                //writer.close();
                return result;
            }
        }
        return "error";
    }
}
