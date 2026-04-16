/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
//import jdk.nashorn.internal.objects.Global;
import net.citotech.cito.Model.User;

//import jdk.nashorn.internal.parser.JSONParser;
//import jdk.nashorn.internal.runtime.Context;
//import jdk.nashorn.internal.runtime.JSType;
//import jdk.nashorn.internal.runtime.ScriptObject;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.UserPrivilege;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/auth")
public class AuthenticationController {
    
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    
    private HttpSession session;
    
    @PostMapping(path="/authenticate")
    @CrossOrigin(origins = "http://localhost:2019")
    public String authenticatedUser (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws NoSuchAlgorithmException {
        //Set the response header
        //response.setHeader("Access-Control-Allow-Origin", "http://localhost:2019");
       
        //Thread.sleep(6000);
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            User sessionUser;
            //sessionUser = (User) session.getAttribute("user");
            
            if (session.getAttribute("user") != null) {
                sessionUser = (User) session.getAttribute("user");
                
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "Already logged in as "+sessionUser.getName());
                return resJson.toString();
            }

            String username = requestBody.get("username");
            String password = requestBody.get("password");


            User u  = getUserByEmailAndPassword(username, password);
            
            if (u == null) {
                return GeneralException
                    .getError("103", GeneralException.ERRORS_103);
            }
            //Check if the user's account is suspended
            if (u.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }
            
            //Now set session values
            session.setAttribute("email", u.getEmail());
            session.setAttribute("phone", u.getPhone());
            session.setAttribute("user", u);
            
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "SUCCESS");
            JSONObject u_ = new JSONObject();
            u_.put("name", u.getName());
            u_.put("email", u.getEmail());
            u_.put("phone", u.getPhone());
            u_.put("status", u.getStatus());
            
            JSONArray privileges_array = new JSONArray();
            for (UserPrivilege p : u.getPrivileges()) {
                JSONObject u_p = new JSONObject();
                u_p.put("privilege", p.getPrivilege());
                u_p.put("updated_on", p.getUdpated_on());
                privileges_array.put(u_p);
            }
            u_.put("privileges", privileges_array);
            
            resJson.put("user", u_);
            
            //Send mail on login
            Thread thread = new Thread(){
                public void run(){
                    SendMail mail = new SendMail();
                    mail.sendSimpleMessage(u.getEmail(), 
                    "You have logged into Cito Account", 
                    "This is to let you know that you have logged into your account Cito Account.");
                }
            };
            thread.start();
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    @PostMapping(path="/authenticateMerchantUser")
    @CrossOrigin(origins = "http://localhost:2019")
    public String authenticateMerchantUser (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws NoSuchAlgorithmException {
        //Set the response header
        //response.setHeader("Access-Control-Allow-Origin", "http://localhost:2019");
       
        //Thread.sleep(6000);
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            MerchantUser sessionUser;
            //sessionUser = (User) session.getAttribute("user");
            
            if (session.getAttribute("merchantUser") != null) {
                sessionUser = (MerchantUser) session.getAttribute("merchantUser");
                
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "Already logged in as "+sessionUser.getName());
                return resJson.toString();
            }

            String username = requestBody.get("username");
            String password = requestBody.get("password");
            String merchant_account = requestBody.get("account_number");


            MerchantUser u  = getMerchantUserByEmailAndPassword(merchant_account, username, password);
            
            if (u == null) {
                return GeneralException
                    .getError("103", GeneralException.ERRORS_103);
            }
            
            //Check if the user's account is suspended
            if (u.getStatus().equals("SUSPENDED")) {
                return GeneralException
                    .getError("137", GeneralException.ERRORS_137);
            }
            
            //Now set session values
            session.setAttribute("email", u.getEmail());
            session.setAttribute("phone", u.getPhone());
            session.setAttribute("merchantUser", u);
           
            
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "SUCCESS");
            JSONObject u_ = new JSONObject();
            u_.put("name", u.getName());
            u_.put("email", u.getEmail());
            u_.put("phone", u.getPhone());
            u_.put("status", u.getStatus());
            u_.put("merchant_name", u.getMerchant_name());
            u_.put("account_number", u.getMerchant_number());
            u_.put("merchant_status", u.getMerchant_status());
            u_.put("merchant_type", u.getMerchant_account_type());
            
            
            JSONArray privileges_array = new JSONArray();
            for (UserPrivilege p : u.getPrivileges()) {
                JSONObject u_p = new JSONObject();
                u_p.put("privilege", p.getPrivilege());
                u_p.put("updated_on", p.getUdpated_on());
                privileges_array.put(u_p);
            }
            u_.put("privileges", privileges_array);
            
            resJson.put("user", u_);
            
            //Send mail on login
            Thread thread = new Thread(){
                public void run(){
                    SendMail mail = new SendMail();
                    mail.sendSimpleMessage(u.getEmail(), 
                    "You have logged in", 
                    "This is to let you know that you have logged into your account.");
                }
            };
            thread.start();
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    private MerchantUser getMerchantUserByEmailAndPassword(String merchant_number, 
            String email, String password) throws NoSuchAlgorithmException {
        MerchantUser u  = new MerchantUser();
        
        u.setEmail(email);
        u.setPassword(password);
        u.setMerchant_number(merchant_number);

        String sqlSelect = "SELECT m.status as merchant_status, m.name as merchant_name,"
                + " m.account_number, m.account_type, a.*, "
            + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
            + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
            + " FROM "+Common.DB_TABLE_MERCHANTS+" as m "
            + " LEFT JOIN "+Common.DB_TABLE_MERCHANT_USERS+" as a ON m.id = a.merchant_id "
            + " WHERE ";
            sqlSelect += " a.email=:email && a.password=:password "
                    + " && m.account_number=:account_number ";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", u.getEmail());
        parameters.addValue("password", Common.getSha256EncodedString(u.getPassword()));
        parameters.addValue("account_number", u.getMerchant_number());

        RowMapper rm = new RowMapper<MerchantUser>() {
            public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser user = new MerchantUser();
                user.setMerchant_account_type(rs.getString("account_type"));
                user.setMerchant_id(rs.getLong("merchant_id"));
                user.setMerchant_name(rs.getString("merchant_name"));
                user.setMerchant_status(rs.getString("merchant_status"));
                user.setMerchant_number(rs.getString("account_number"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                user.setEmail_verification_code(rs.getString("email_verification_code"));
                user.setPrivileges(getMerchantUserPrivileges(user));
                return user;
            }
        };

        //ResultSet rs; 
        List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        if (listUsers.size() < 1) {
            return null;
        }

        return listUsers.get(0);
    }
    
    private User getUserByEmailAndPassword(String email, String password) throws NoSuchAlgorithmException {
        User u  = new User();
        
        u.setEmail(email);
        u.setPassword(password);

        String sqlSelect = "SELECT *, "
            + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
            + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
            + " FROM admins WHERE ";
            sqlSelect += " email=:email && password=:password";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", u.getEmail());
        parameters.addValue("password", Common.getSha256EncodedString(u.getPassword()));

        RowMapper rm = new RowMapper<User>() {
            public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                User user = new User();
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                user.setEmail_verification_code(rs.getString("email_verification_code"));
                user.setPrivileges(getUserPrivileges(user));
                return user;
            }
        };

        //ResultSet rs; 
        List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        if (listUsers.size() < 1) {
            return null;
        }

        return listUsers.get(0);
    }
    
    /*
    * Gets a user by email address
    * @Param email: User's email address
    * @Param account_number: Merchant user's account number.
    *
    * Returns null or User object
    */
    private MerchantUser getMerchantUserByEmail(String account_number, String email) {
        MerchantUser u  = new MerchantUser();
        
        u.setEmail(email);
        u.setMerchant_number(account_number);

        String sqlSelect = "SELECT m.status as merchant_status, m.name as merchant_name,"
            + " m.account_number, m.account_type, a.*, "
            + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
            + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
            + " FROM merchants as m LEFT JOIN merchant_admins as a ON m.id = a.merchant_id "
            + " WHERE email=:email AND m.account_number=:account_number";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", u.getEmail());
        parameters.addValue("account_number", u.getMerchant_number());

        RowMapper rm = new RowMapper<MerchantUser>() {
            public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser user = new MerchantUser();
                user.setMerchant_account_type(rs.getString("account_type"));
                user.setMerchant_id(rs.getLong("merchant_id"));
                user.setMerchant_name(rs.getString("merchant_name"));
                user.setMerchant_status(rs.getString("merchant_status"));
                user.setMerchant_number(rs.getString("account_number"));
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                user.setEmail_verification_code(rs.getString("email_verification_code"));
                user.setPrivileges(getMerchantUserPrivileges(user));
                return user;
            }
        };

        //ResultSet rs; 
        List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        if (listUsers.size() < 1) {
            return null;
        }

        return listUsers.get(0);
    }
    
    
    
    /*
    * Gets a user by email address
    * @Param email: User's email address
    *
    * Returns null or User object
    */
    private User getUserByEmail(String email) {
        User u  = new User();
        
        u.setEmail(email);

        String sqlSelect = "SELECT *,  "
            + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
                + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
            + " FROM admins WHERE email=:email";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", u.getEmail());

        RowMapper rm = new RowMapper<User>() {
            public User mapRow(ResultSet rs, int rowNum) throws SQLException {
                User user = new User();
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                user.setEmail_verification_code(rs.getString("email_verification_code"));
                user.setPrivileges(getUserPrivileges(user));
                return user;
            }
        };

        //ResultSet rs; 
        List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        if (listUsers.size() < 1) {
            return null;
        }

        return listUsers.get(0);
    }
    
    
        /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/isMerchantUserLoggedIn")
    @CrossOrigin
    public String isMerchantUserLoggedIn (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        //response.setHeader("Access-Control-Allow-Origin", "*");
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            MerchantUser sessionUser;
            //sessionUser = (User) session.getAttribute("user");
            
            if (session.getAttribute("merchantUser") != null) {
                sessionUser = (MerchantUser) session.getAttribute("merchantUser");
                
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "true");
                JSONObject u_ = new JSONObject();
                u_.put("name", sessionUser.getName());
                u_.put("email", sessionUser.getEmail());
                u_.put("phone", sessionUser.getPhone());
                u_.put("status", sessionUser.getStatus());
                u_.put("merchant_name", sessionUser.getMerchant_name());
                u_.put("account_number", sessionUser.getMerchant_number());
                u_.put("merchant_status", sessionUser.getMerchant_status());
                u_.put("merchant_type", sessionUser.getMerchant_account_type());
                //
                JSONArray privileges_array = new JSONArray();
                for (UserPrivilege p : sessionUser.getPrivileges()) {
                    JSONObject u_p = new JSONObject();
                    u_p.put("privilege", p.getPrivilege());
                    u_p.put("updated_on", p.getUdpated_on());
                    privileges_array.put(u_p);
                }
                u_.put("privileges", privileges_array);
                resJson.put("user", u_);
                return resJson.toString();
            } else {
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "false");
                return resJson.toString();
            }
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/isLoggedIn")
    @CrossOrigin
    public String isLoggedIn (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        //response.setHeader("Access-Control-Allow-Origin", "*");
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            User sessionUser;
            //sessionUser = (User) session.getAttribute("user");
            
            if (session.getAttribute("user") != null) {
                sessionUser = (User) session.getAttribute("user");
                
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "true");
                JSONObject u_ = new JSONObject();
                u_.put("name", sessionUser.getName());
                u_.put("email", sessionUser.getEmail());
                u_.put("phone", sessionUser.getPhone());
                u_.put("status", sessionUser.getStatus());
                //
                JSONArray privileges_array = new JSONArray();
                for (UserPrivilege p : sessionUser.getPrivileges()) {
                    JSONObject u_p = new JSONObject();
                    u_p.put("privilege", p.getPrivilege());
                    u_p.put("updated_on", p.getUdpated_on());
                    privileges_array.put(u_p);
                }
                u_.put("privileges", privileges_array);
                resJson.put("user", u_);
                return resJson.toString();
            } else {
                JSONObject resJson = new JSONObject();
                resJson.put("code", "000");
                resJson.put("message", "false");
                return resJson.toString();
            }
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/requestMerchantUserResetPassword")
    @CrossOrigin
    public String requestMerchantUserResetPassword(@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            String userEmail = requestBody.get("email");
            String account_number = requestBody.get("merchant_number");
            
            MerchantUser u  = getMerchantUserByEmail(account_number, userEmail);
            
            if (u == null) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Now set the time when the the email verification has to be sent.
            String sql = "UPDATE "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `email_verification_code`=:email_verification_code,"
                +" `email_verification_sent_on`=now() "
                + "WHERE id = :id";
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            String verification_code = Common.randomNumericString(6);
            parameters.put("email_verification_code", verification_code);
            parameters.put("id", u.getId());
            
            long retVal = jdbcTemplate.update(sql, parameters);
            
            if (retVal > 0) {
                //Now send verification email
                Setting emailContentManage = Common.getSettings("email_tmp_pw_reset", jdbcTemplate);
                String emailContent_ = emailContentManage.getSetting_value()
                        .replace("{name}", u.getName());
                final String emailContent = emailContent_.replace("{verification_code}", verification_code);
                
                final String subject = "Password Reset Request";
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
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
 /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/requestResetPassword")
    @CrossOrigin
    public String requestResetPassword (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            String userEmail = requestBody.get("email");
            User u  = getUserByEmail(userEmail);
            
            if (u == null) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Now set the time when the the email verification has to be sent.
            String sql = "UPDATE "+Common.DB_TABLE_ADMIN+" "
                +" SET `email_verification_code`=:email_verification_code,"
                +" `email_verification_sent_on`=now() "
                + "WHERE id = :id";
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            String verification_code = Common.randomNumericString(6);
            parameters.put("email_verification_code", verification_code);
            parameters.put("id", u.getId());
            
            long retVal = jdbcTemplate.update(sql, parameters);
            
            if (retVal > 0) {
                //Now send verification email
                Setting emailContentManage = Common.getSettings("email_tmp_pw_reset", jdbcTemplate);
                String emailContent_ = emailContentManage.getSetting_value()
                        .replace("{name}", u.getName());
                final String emailContent = emailContent_.replace("{verification_code}", verification_code);
                
                final String subject = "Password Reset Request";
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
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }


/*
    * Checks if user is still logged in
    */
    @PostMapping(path="/requestResetPasswordMerchant")
    @CrossOrigin
    public String requestResetPasswordMerchant (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            String userEmail = requestBody.get("email");
            String merchantNumber =  requestBody.get("merchant_number");
            User u  = getUserByEmail(userEmail);
            
            if (u == null) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Now set the time when the the email verification has to be sent.
            String sql = "UPDATE "+Common.DB_TABLE_ADMIN+" "
                +" SET `email_verification_code`=:email_verification_code,"
                +" `email_verification_sent_on`=now() "
                + "WHERE id = :id";
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            String verification_code = Common.randomNumericString(6);
            parameters.put("email_verification_code", verification_code);
            parameters.put("id", u.getId());
            
            long retVal = jdbcTemplate.update(sql, parameters);
            
            if (retVal > 0) {
                //Now send verification email
                Setting emailContentManage = Common.getSettings("email_tmp_pw_reset", jdbcTemplate);
                String emailContent_ = emailContentManage.getSetting_value()
                        .replace("{name}", u.getName());
                final String emailContent = emailContent_.replace("{verification_code}", verification_code);
                
                final String subject = "Password Reset Request";
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
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }    
    
    
    /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/resetPassword")
    @CrossOrigin
    public String resetPassword (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            String userEmail = requestBody.get("email");
            String verificationCode = requestBody.get("verification_code");
            String newPassword = requestBody.get("new_password");
            
            User u  = getUserByEmail(userEmail);
            if (u == null) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            //Check if it didn't timeout
            if (u.getIs_verification_timedout().equals("TRUE")) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Check if the verification code matches
            if (!u.getEmail_verification_code().equals(verificationCode)) {
                return GeneralException
                    .getError("106", String.format(GeneralException.ERRORS_106, verificationCode));
            }
            
            //Now set the time when the email verification has to be sent.
            String sql = "UPDATE "+Common.DB_TABLE_ADMIN+" "
                +" SET `password`=:password "
                +" WHERE id = :id";
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            String verification_code = Common.randomNumericString(6);
            parameters.put("password", Common.getSha256EncodedString(newPassword));
            parameters.put("id", u.getId());
            
            long retVal = jdbcTemplate.update(sql, parameters);
            
            if (retVal > 0) {
                
                //Now send verification email
                Setting emailContentManage = Common
                        .getSettings("email_tmp_on_password_reset_done", 
                                jdbcTemplate);
                final String emailContent = emailContentManage.getSetting_value()
                        .replace("{name}", u.getName());
                
                final String subject = "You have Reset your Password!";
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
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_001);
            } else {
                return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    /*
    * Checks if user is still logged in
    */
    @PostMapping(path="/resetPasswordMerchant")
    @CrossOrigin
    public String resetPasswordMerchant (@RequestBody Map<String, String> requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            String userEmail = requestBody.get("email");
            String verificationCode = requestBody.get("verification_code");
            String newPassword = requestBody.get("new_password");
            String merchant_number = requestBody.get("merchant_number");
            
            MerchantUser u  = this.getMerchantUserByEmail(merchant_number, userEmail);
            if (u == null) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Check if it didn't timeout
            if (u.getIs_verification_timedout().equals("TRUE")) {
                return GeneralException
                    .getError("104", String.format(GeneralException.ERRORS_104, userEmail));
            }
            
            //Check if the verification code matches
            if (!u.getEmail_verification_code().equals(verificationCode)) {
                return GeneralException
                    .getError("106", String.format(GeneralException.ERRORS_106, verificationCode));
            }
            
            //Now set the time when the email verification has to be sent.
            String sql = "UPDATE "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `password`=:password "
                +" WHERE id = :id";
            
            Map<String, Object> parameters = new HashMap<String, Object>();
            
            parameters.put("password", Common.getSha256EncodedString(newPassword));
            parameters.put("id", u.getId());
            
            long retVal = jdbcTemplate.update(sql, parameters);
            
            if (retVal > 0) {
                
                //Now send verification email
                Setting emailContentManage = Common
                        .getSettings("email_tmp_on_password_reset_done", 
                                jdbcTemplate);
                final String emailContent = emailContentManage.getSetting_value()
                        .replace("{name}", u.getName());
                
                final String subject = "You have Reset your Password!";
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
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_001);
            } else {
                return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    public List<UserPrivilege> getUserPrivileges(User user) {
        
        String sqlSelect = "SELECT * FROM "+Common.DB_TABLE_ADMIN_PRIVILEGES+" WHERE ";
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
    
    public List<UserPrivilege> getMerchantUserPrivileges(MerchantUser user) {
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
    
    @PostMapping(path="/logout")
    public String logOut (@RequestBody String requestBody, HttpServletRequest request) {
        request.getSession().invalidate();
        
        try {
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "SUCCESS");
            return resJson.toString();
            
        } catch (JSONException ex) {
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
}
