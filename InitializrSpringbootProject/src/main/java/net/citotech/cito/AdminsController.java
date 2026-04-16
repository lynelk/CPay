/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/admins")
public class AdminsController {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    
    private HttpSession session;
    
    @PostMapping(path="/getAdmins")
    @CrossOrigin
    public String getAdmins (@RequestBody String requestBody, 
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
            if (!Common.isUserAllowedAccessToThis("ACCESS_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            //Obtain search fields
            JSONObject sObject = new JSONObject(requestBody);
            String pageSize = sObject.getString("pageSize");
            String currentPage = sObject.isNull("currentPage") ? "" : sObject.getString("currentPage");
            JSONObject searchValue = sObject.getJSONObject("searchingValue");
            
            String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_ADMIN+" ";
            
            //HANDLE SEARCH PARAMETERS
            if (!searchValue.isNull("category") && !searchValue.isNull("value") ) {
                
                String category = searchValue.getString("category");
                String value = searchValue.getString("value");
                if (!value.equals("all") && !category.isEmpty() && !value.isEmpty()) {
                    sqlSelect += " WHERE "+category+" LIKE :"+category+" ";
                    parameters.addValue(category, "%"+value+"%");
                }
            }
            
            if (pageSize != null && pageSize.isEmpty()) {
                sqlSelect += " LIMIT "+pageSize+" ";
            }
            
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
                    user.setPrivileges(getUserPrivileges(user));
                    return user;
                }
            };
            
            //ResultSet rs; 
            List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray admins_array = new JSONArray();
            for (User us : listUsers) {
                JSONObject u_p_ = new JSONObject();
                u_p_.put("id", us.getId());
                u_p_.put("name", us.getName());
                u_p_.put("email", us.getEmail());
                u_p_.put("phone", us.getPhone());
                u_p_.put("status", us.getStatus());
                u_p_.put("created_on", us.getCreated_on());
                u_p_.put("updaed_on", us.getUpdated_on());
                
                //Get privileges
                JSONArray admins_privileges_array = new JSONArray();
                for (UserPrivilege up : us.getPrivileges()) {
                    admins_privileges_array.put(up.getPrivilege());
                }
                u_p_.put("privileges", admins_privileges_array);
                admins_array.put(u_p_);
            }
            resJson.put("data", admins_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    @PostMapping(path="/getAdminsMerchant")
    @CrossOrigin
    public String getAdminsMerchant (@RequestBody String requestBody, 
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
            if (!Common.isUserAllowedAccessToThis("ACCESS_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            //Obtain search fields
            JSONObject sObject = new JSONObject(requestBody);
            String pageSize = sObject.getString("pageSize");
            String currentPage = sObject.isNull("currentPage") ? "" : sObject.getString("currentPage");
            JSONObject searchValue = sObject.getJSONObject("searchingValue");
            
            String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                    + " WHERE merchant_id='"+sessionUser.getMerchant_id()+"' ";
            
            //HANDLE SEARCH PARAMETERS
            if (!searchValue.isNull("category") && !searchValue.isNull("value") ) {
                
                String category = searchValue.getString("category");
                String value = searchValue.getString("value");
                if (!value.equals("all") && !category.isEmpty() && !value.isEmpty()) {
                    sqlSelect += " AND "+category+" LIKE :"+category+" ";
                    parameters.addValue(category, "%"+value+"%");
                }
            }
            
            if (pageSize != null && pageSize.isEmpty()) {
                sqlSelect += " LIMIT "+pageSize+" ";
            }
            
            RowMapper rm = new RowMapper<MerchantUser>() {
            public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                    MerchantUser user = new MerchantUser();
                    user.setName(rs.getString("name"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setId(rs.getLong("id"));
                    user.setStatus(rs.getString("status"));
                    user.setCreated_on(rs.getString("created_on"));
                    user.setUpdated_on(rs.getString("updated_on"));
                    user.setPrivileges(getMerchantUserPrivileges(user));
                    return user;
                }
            };
            
            //ResultSet rs; 
            List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray admins_array = new JSONArray();
            for (User us : listUsers) {
                JSONObject u_p_ = new JSONObject();
                u_p_.put("id", us.getId());
                u_p_.put("name", us.getName());
                u_p_.put("email", us.getEmail());
                u_p_.put("phone", us.getPhone());
                u_p_.put("status", us.getStatus());
                u_p_.put("created_on", us.getCreated_on());
                u_p_.put("updaed_on", us.getUpdated_on());
                
                //Get privileges
                JSONArray admins_privileges_array = new JSONArray();
                for (UserPrivilege up : us.getPrivileges()) {
                    admins_privileges_array.put(up.getPrivilege());
                }
                u_p_.put("privileges", admins_privileges_array);
                admins_array.put(u_p_);
            }
            resJson.put("data", admins_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    public List<UserPrivilege> getMerchantUserPrivileges(User user) {
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
    
    
    /*
    * Queries the database to get the user 
    * by their email address.
    * 
    * Returns User object or null.
    */
    public User getUserByEmail(String email) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", email);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_ADMIN+" "
                + " WHERE email=:email";
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
                user.setPrivileges(getUserPrivileges(user));
                return user;
            }
        };
        List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
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
    */
    public MerchantUser getMerchantUserByEmail(String email, long merchant_id) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", email);
        parameters.addValue("merchant_id", merchant_id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                + " WHERE "
                + " merchant_id =:merchant_id "
                + " AND email=:email ";
        RowMapper rm = new RowMapper<MerchantUser>() {
        public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser user = new MerchantUser();
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setPrivileges(getMerchantUserPrivileges(user));
                return user;
            }
        };
        List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }
    
    
    /*
    * Queries the database to get the user 
    * by their email address.
    * @Param id: This is the ID of the user.
    * Returns User object or null.
    */
    public User getUserByEmail(String email, String id) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", email);
        parameters.addValue("id", id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_ADMIN+" "
                + " WHERE email=:email AND id <> :id";
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
                user.setPrivileges(getUserPrivileges(user));
                return user;
            }
        };
        List<User> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }
    
    /*
    * Queries the database to get the user 
    * by their email address.
    * @Param id: This is the ID of the user.
    * Returns User object or null.
    */
    public MerchantUser getMerchantUserByEmail(String email, String id, Long merchant_id) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("email", email);
        parameters.addValue("id", id);
        parameters.addValue("merchant_id", merchant_id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                + " WHERE "
                + " merchant_id=:merchant_id AND "
                + " email=:email AND id <> :id";
        RowMapper rm = new RowMapper<MerchantUser>() {
        public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser user = new MerchantUser();
                user.setName(rs.getString("name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setId(rs.getLong("id"));
                user.setStatus(rs.getString("status"));
                user.setCreated_on(rs.getString("created_on"));
                user.setUpdated_on(rs.getString("updated_on"));
                user.setPrivileges(getMerchantUserPrivileges(user));
                return user;
            }
        };
        List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
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
    
    
    /*
    * Checks if user is still logged in
    *
    * @Param request This is the serverlet request.
    * 
    * Returns true if still logged in or false otherwise.
    */
    
    public Boolean isLoggedIn (HttpServletRequest request ) {
       
        //First set session variable
        session = request.getSession();
       
        //Check if still logged in
        User sessionUser;
        //sessionUser = (User) session.getAttribute("user");

        if (session.getAttribute("user") == null) {
            return false;
        } else {
            return true;
        }
            
    }
    
    
    public Boolean isMerchantUserLoggedIn (HttpServletRequest request ) {
       
        //First set session variable
        session = request.getSession();
       
        //Check if still logged in
        MerchantUser sessionUser;
        //sessionUser = (User) session.getAttribute("user");

        if (session.getAttribute("merchantUser") == null) {
            return false;
        } else {
            return true;
        }   
    }
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/addAdmin")
    @CrossOrigin
    public String addAdmin (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            User sessionUser = (User) session.getAttribute("user");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("CREATE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String password = sObject.getString("password");
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            
            JSONArray privileges = sObject.getJSONArray("privileges");
            User u  = getUserByEmail(email);
            if (u != null) {
                return GeneralException
                    .getError("108", String.format(GeneralException.ERRORS_108, "User ", email));
            }
            
            //Now add the user to database
            String sql = "INSERT INTO "+Common.DB_TABLE_ADMIN+" "
                +" SET `email`=:email,"
                +" `phone`=:phone, "
                +" `password`=:password,"
                +" `status`=:status,"
                +" `name`=:name";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("email", email);
            parameters.addValue("phone", phone);
            parameters.addValue("status", status);
            parameters.addValue("password", Common.getSha256EncodedString(password));
            parameters.addValue("name", name);
            
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        //long userId;
                        jdbcTemplate.update(sql, parameters, keyHolder);
                        //Now insert privileges
                        BigInteger userId = (BigInteger)keyHolder.getKey();
                        
                        MapSqlParameterSource privParams;
                        for (int i=0; i < privileges.length(); i++) {
                            String privilege = privileges.getString(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("admin_id", userId);
                            privParams.addValue("privilege", privilege);
                            long privId = jdbcTemplate.update(sqlPrivileges, privParams);
                        }
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordAction(sessionUser, 
                                "Added new admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        //transactionManager.commit(status);
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/addAdminMerchant")
    @CrossOrigin
    public String addAdminMerchant (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isMerchantUserLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            MerchantUser sessionUser = (MerchantUser) session.getAttribute("merchantUser");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("CREATE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String password = sObject.getString("password");
            MerchantUser newUser = new MerchantUser();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            newUser.setMerchant_id(sessionUser.getMerchant_id());
            newUser.setMerchant_name(sessionUser.getMerchant_name());
            newUser.setMerchant_number(sessionUser.getMerchant_number());
            newUser.setMerchant_status(sessionUser.getMerchant_status());
            newUser.setMerchant_account_type(sessionUser.getMerchant_account_type());
            
            JSONArray privileges = sObject.getJSONArray("privileges");
            MerchantUser u  = getMerchantUserByEmail(email, sessionUser.getMerchant_id());
            if (u != null) {
                return GeneralException
                    .getError("108", String.format(GeneralException.ERRORS_108, "User ", email));
            }
            
            //Now add the user to database
            String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `email`=:email,"
                +" `merchant_id`=:merchant_id, "
                +" `phone`=:phone, "
                +" `password`=:password,"
                +" `status`=:status,"
                +" `name`=:name";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("email", email);
            parameters.addValue("phone", phone);
            parameters.addValue("status", status);
            parameters.addValue("password", Common.getSha256EncodedString(password));
            parameters.addValue("name", name);
            parameters.addValue("merchant_id", sessionUser.getMerchant_id());
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        //long userId;
                        jdbcTemplate.update(sql, parameters, keyHolder);
                        //Now insert privileges
                        BigInteger userId = (BigInteger)keyHolder.getKey();
                        
                        MapSqlParameterSource privParams;
                        for (int i=0; i < privileges.length(); i++) {
                            String privilege = privileges.getString(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("admin_id", userId);
                            privParams.addValue("privilege", privilege);
                            long privId = jdbcTemplate.update(sqlPrivileges, privParams);
                        }
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordMerchantAction(sessionUser, 
                                "Added new admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        //Send and email.
                       
                        //Send an email with user's credentials
                        Common.sendEmailOnUpdatingMerchantUserPassword(newUser, 
                                password,
                                jdbcTemplate);
                        
                        //transactionManager.commit(status);
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        String eMessage = e.getMessage();
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": Here... "+eMessage);
                    }
                }
            });
            
            if (result.equals("success")) {
                
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/editAdmin")
    @CrossOrigin
    public String editAdmin (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            User sessionUser = (User) session.getAttribute("user");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("UPDATE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String password = sObject.getString("password");
            String id = sObject.getString("id");
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            
            JSONArray privileges = sObject.getJSONArray("privileges");
            //Check if this user already exists.
            User u  = getUserByEmail(email, id);
            if (u != null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "User ", email));
            }
            
            //Now add the user to database
            String sql = "UPDATE "+Common.DB_TABLE_ADMIN+" "
                +" SET `email`=:email,"
                +" `phone`=:phone, "
                + (!password.isEmpty() ? " `password`=:password," : "")
                +" `status`=:status,"
                +" `name`=:name "
                + " WHERE `id`=:id ";
            
            String sqlPrivilegesDropExistings = "DELETE FROM "+Common.DB_TABLE_ADMIN_PRIVILEGES+" "
                +" WHERE `admin_id`=:admin_id ";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            MapSqlParameterSource parameterDropPrivileges = new MapSqlParameterSource();
            parameterDropPrivileges.addValue("admin_id", sObject.getString("id"));
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("email", email);
            parameters.addValue("phone", phone);
            parameters.addValue("status", status);
            if (!password.isEmpty()) {
                parameters.addValue("password", Common.getSha256EncodedString(password));
            }
            parameters.addValue("name", name);
            parameters.addValue("id", sObject.getString("id"));
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        
                        jdbcTemplate.update(sql, parameters);
                        
                        //Now drop privileges
                        jdbcTemplate.update(sqlPrivilegesDropExistings, parameterDropPrivileges);
                        
                        
                        MapSqlParameterSource privParams;
                        for (int i=0; i < privileges.length(); i++) {
                            String privilege = privileges.getString(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("admin_id", sObject.getString("id"));
                            privParams.addValue("privilege", privilege);
                            long privId = jdbcTemplate.update(sqlPrivileges, privParams);
                        }
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordAction(sessionUser, 
                                "Updated admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        //transactionManager.commit(status);
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/editAdminMerchant")
    @CrossOrigin
    public String editAdminMerchant (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isMerchantUserLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            MerchantUser sessionUser = (MerchantUser) session.getAttribute("merchantUser");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("UPDATE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String password = sObject.getString("password");
            long id =  /*sessionUser.getMerchant_id();*/sObject.getLong("id");
            MerchantUser newUser = new MerchantUser();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            
            JSONArray privileges = sObject.getJSONArray("privileges");
            //Check if this user already exists.
            MerchantUser u  = getMerchantUserByEmail(email, id+"", sessionUser.getMerchant_id());
            if (u != null) {
                return GeneralException
                    .getError("109", String.format(GeneralException.ERRORS_109, "User ", email));
            }
            
            //Now add the user to database
            String sql = "UPDATE "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `email`=:email,"
                +" `phone`=:phone, "
                + (!password.isEmpty() ? " `password`=:password," : "")
                +" `status`=:status,"
                +" `name`=:name "
                + " WHERE `id`=:id ";
            
            String sqlPrivilegesDropExistings = "DELETE FROM "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" WHERE `admin_id`=:admin_id ";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            MapSqlParameterSource parameterDropPrivileges = new MapSqlParameterSource();
            parameterDropPrivileges.addValue("admin_id", sObject.getString("id"));
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("email", email);
            parameters.addValue("phone", phone);
            parameters.addValue("status", status);
            if (!password.isEmpty()) {
                parameters.addValue("password", Common.getSha256EncodedString(password));
            }
            parameters.addValue("name", name);
            parameters.addValue("id", sObject.getString("id"));
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        
                        jdbcTemplate.update(sql, parameters);
                        
                        //Now drop privileges
                        jdbcTemplate.update(sqlPrivilegesDropExistings, parameterDropPrivileges);
                        
                        
                        MapSqlParameterSource privParams;
                        for (int i=0; i < privileges.length(); i++) {
                            String privilege = privileges.getString(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("admin_id", sObject.getString("id"));
                            privParams.addValue("privilege", privilege);
                            long privId = jdbcTemplate.update(sqlPrivileges, privParams);
                        }
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordMerchantAction(sessionUser, 
                                "Updated admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        //transactionManager.commit(status);
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
    /*
    * API to deleteing an admin from the database
    */
    @PostMapping(path="/deleteAdmin")
    @CrossOrigin
    public String deleteAdmin (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            User sessionUser = (User) session.getAttribute("user");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("DELETE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("ACCESS_AUDITTRAIL", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String id = sObject.getString("id");
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            
            
            //Now add the user to database
            String sql = "DELETE FROM "+Common.DB_TABLE_ADMIN+" "
                + " WHERE `id`=:id ";
            
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", id);
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        jdbcTemplate.update(sql, parameters);
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordAction(sessionUser, 
                                "DELETED admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    
    /*
    * API to deleteing an admin from the database
    */
    @PostMapping(path="/deleteAdminMerchant")
    @CrossOrigin
    public String deleteAdminMerchant (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isMerchantUserLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            MerchantUser sessionUser = (MerchantUser) session.getAttribute("merchantUser");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("DELETE_ADMIN", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("ACCESS_AUDITTRAIL", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String email = sObject.getString("email");
            String name = sObject.getString("name");
            String phone = sObject.getString("phone");
            String status = sObject.getString("status");
            String id = sObject.getString("id");
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(name);
            newUser.setPhone(phone);
            newUser.setStatus(status);
            
            
            //Now add the user to database
            String sql = "DELETE FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                + " WHERE `id`=:id ";
            
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("id", id);
            
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        jdbcTemplate.update(sql, parameters);
                        
                        //Now insert audit Trail
                        String actionInsert = Common.recordMerchantAction(sessionUser, 
                                "DELETED admin "+newUser.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        return "success";
                    } catch (Exception e) {
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                    }
                }
            });
            
            if (result.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return result;
            }
        }  catch (Exception ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
}
