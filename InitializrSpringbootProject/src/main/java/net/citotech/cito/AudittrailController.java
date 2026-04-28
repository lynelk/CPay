/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.citotech.cito.Model.AuditTrail;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import net.citotech.cito.security.ColumnAllowlist;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/audittrail")
public class AudittrailController {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    
    private HttpSession session;
    
    @PostMapping(path="/getAudittrails")

    public String getAudittrails (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            User sessionUser;
            
            if (session.getAttribute("user") == null) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            sessionUser = (User) session.getAttribute("user");
            //Get the first details
            
            //Check permissions
            if (!isUserAllowedAccessToThis("ACCESS_AUDITTRAIL", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            //Obtain search fields
            JSONObject sObject = new JSONObject(requestBody);
            String pageSize = sObject.getString("pageSize");
            String currentPage = sObject.isNull("currentPage") ? "" : sObject.getString("currentPage");
            JSONObject searchValue = sObject.getJSONObject("searchingValue");
            
            String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_AUDIT_TRAIL+" ";
            
            //HANDLE SEARCH PARAMETERS
            if (!searchValue.isNull("category") && !searchValue.isNull("value") ) {
                
                String category = searchValue.getString("category");
                String value = searchValue.getString("value");
                if (!value.equals("all") && !category.isEmpty() && !value.isEmpty()) {
                    try {
                        String safeCategory = ColumnAllowlist.validate(category);
                        sqlSelect += " WHERE " + safeCategory + " LIKE :" + safeCategory + " ";
                        parameters.addValue(safeCategory, "%" + value + "%");
                    } catch (IllegalArgumentException e) {
                        return GeneralException.getError("101", "Invalid search field.");
                    }
                }
            }
            
            sqlSelect +=" ORDER BY id DESC ";
            
            if (pageSize != null && !pageSize.isEmpty()) {
                int _limit = Math.max(1, Math.min(Integer.parseInt(pageSize.trim()), 1000));
                sqlSelect += " LIMIT " + _limit;
            }
            
            RowMapper rm = new RowMapper<AuditTrail>() {
            public AuditTrail mapRow(ResultSet rs, int rowNum) throws SQLException {
                    AuditTrail auditrail = new AuditTrail();
                    auditrail.setId(rs.getLong("id"));
                    auditrail.setUser_name(rs.getString("user_name"));
                    auditrail.setAction(rs.getString("action"));
                    auditrail.setCreated_on(rs.getString("created_on"));
                    auditrail.setUser_id(rs.getString("user_id"));
                    
                    return auditrail;
                }
            };
            
            //ResultSet rs; 
            List<AuditTrail> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray d_array = new JSONArray();
            for (AuditTrail d : listUsers) {
                JSONObject u_p_ = new JSONObject();
                u_p_.put("id", d.getId());
                u_p_.put("user_id", d.getUser_id());
                u_p_.put("user_name", d.getUser_name());
                u_p_.put("created_on", d.getCreated_on());
                u_p_.put("action", d.getAction());
                
                d_array.put(u_p_);
            }
            resJson.put("data", d_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    Boolean isUserAllowedAccessToThis(String permission, User user) {
        List<UserPrivilege> uPermissions = user.getPrivileges();
        for (UserPrivilege p : uPermissions) {
            String privilege = p.getPrivilege();
            if (privilege != null && privilege.equals(permission)) {
                return true;
            }
        }
        return false;
    }
    
    
    @PostMapping(path="/getMerchantAudittrails")

    public String getMerchantAudittrails (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        //First set session variable
        session = request.getSession();
        try {
            //Check if still logged in
            MerchantUser sessionUser;
            
            if (session.getAttribute("merchantUser") == null) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            sessionUser = (MerchantUser) session.getAttribute("merchantUser");
            //Get the first details
            
            //Check permissions
            if (!isUserAllowedAccessToThis("ACCESS_AUDITTRAIL", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            //Obtain search fields
            JSONObject sObject = new JSONObject(requestBody);
            String pageSize = sObject.getString("pageSize");
            String currentPage = sObject.isNull("currentPage") ? "" : sObject.getString("currentPage");
            JSONObject searchValue = sObject.getJSONObject("searchingValue");
            
            String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_AUDIT_TRAIL_MERCHANT+" "
                    + " WHERE merchant_id='"+sessionUser.getMerchant_id()+"'";
            
            //HANDLE SEARCH PARAMETERS
            if (!searchValue.isNull("category") && !searchValue.isNull("value") ) {
                
                String category = searchValue.getString("category");
                String value = searchValue.getString("value");
                if (!value.equals("all") && !category.isEmpty() && !value.isEmpty()) {
                    sqlSelect += " AND "+category+" LIKE :"+category+" ";
                    parameters.addValue(category, "%"+value+"%");
                }
            }
            
            sqlSelect +=" ORDER BY id DESC ";
            
            if (pageSize != null && !pageSize.isEmpty()) {
                int _limit = Math.max(1, Math.min(Integer.parseInt(pageSize.trim()), 1000));
                sqlSelect += " LIMIT " + _limit;
            }
            
            RowMapper rm = new RowMapper<AuditTrail>() {
            public AuditTrail mapRow(ResultSet rs, int rowNum) throws SQLException {
                    AuditTrail auditrail = new AuditTrail();
                    auditrail.setId(rs.getLong("id"));
                    auditrail.setUser_name(rs.getString("user_name"));
                    auditrail.setAction(rs.getString("action"));
                    auditrail.setCreated_on(rs.getString("created_on"));
                    auditrail.setUser_id(rs.getString("user_id"));
                    
                    return auditrail;
                }
            };
            
            //ResultSet rs; 
            List<AuditTrail> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray d_array = new JSONArray();
            for (AuditTrail d : listUsers) {
                JSONObject u_p_ = new JSONObject();
                u_p_.put("id", d.getId());
                u_p_.put("user_id", d.getUser_id());
                u_p_.put("user_name", d.getUser_name());
                u_p_.put("created_on", d.getCreated_on());
                u_p_.put("action", d.getAction());
                
                d_array.put(u_p_);
            }
            resJson.put("data", d_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
   
}
