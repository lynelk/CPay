package net.citotech.cito;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.citotech.cito.Model.KeyPairStrings;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.Setting;
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
import org.springframework.transaction.support.TransactionTemplate;
import net.citotech.cito.security.ColumnAllowlist;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/merchants")
public class MerchantsController {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    
    private HttpSession session;
    
    @PostMapping(path="/getMerchants")

    public String getMerchants (@RequestBody String requestBody, 
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
            
            String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" ";
            
            //HANDLE SEARCH PARAMETERS
            if (!searchValue.isNull("category") && !searchValue.isNull("value") ) {
                
                String category = searchValue.getString("category");
                String value = searchValue.getString("value");
                if (!value.toLowerCase().equals("all") && !category.isEmpty() && !value.isEmpty()) {
                    sqlSelect += " WHERE "+category+" LIKE :"+category+" ";
                    parameters.addValue(category, "%"+value+"%");
                }
            }
            
            if (pageSize != null && !pageSize.isEmpty()) {
                int _limit = Math.max(1, Math.min(Integer.parseInt(pageSize.trim()), 1000));
                sqlSelect += " LIMIT " + _limit;
            }
            
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
                    m.setUsers(getMerchantUsers(m));
                    m.setPrivate_key(rs.getString("private_key"));
                    m.setPublic_key(rs.getString("public_key"));
                    m.setShort_name(rs.getString("short_name"));
                    String allowed_apis_string = rs.getString("allowed_apis")!= null ? 
                        rs.getString("allowed_apis"): "";
                
                    String[] allowed_apis;
                    if (allowed_apis_string.isEmpty()) {
                        allowed_apis = new String[0];
                    } else {
                        allowed_apis = allowed_apis_string.split(",");
                    }
                    m.setAllowed_apis(allowed_apis);
                    return m;
                }
            };
            
            //ResultSet rs; 
            List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray admins_array = new JSONArray();
            for (Merchant us : listUsers) {
                JSONObject u_p_ = new JSONObject();
                u_p_.put("id", us.getId());
                u_p_.put("name", us.getName());
                u_p_.put("short_name", us.getShort_name());
                u_p_.put("account_number", us.getAccount_number());
                u_p_.put("status", us.getStatus());
                u_p_.put("account_type", us.getAccount_type());
                u_p_.put("created_by", us.getCreated_by());
                u_p_.put("created_on", us.getCreated_on());
                u_p_.put("updaed_on", us.getUpdated_on());
                u_p_.put("shoert_name", us.getShort_name());
                u_p_.put("delete", false);
                u_p_.put("generate_pw", false);
                u_p_.put("private_key", us.getPrivate_key());
                u_p_.put("public_key", us.getPublic_key());
                
                //Get Merchant apis
                JSONArray apsArray = new JSONArray();
                for (String api : us.getAllowed_apis()) {
                    apsArray.put(api);
                }
                u_p_.put("allowed_apis", apsArray);
                
                //Add accounts
                List<MerchantUser> mus = getMerchantUsers(us);
                JSONArray m_us_array = new JSONArray();
                for (MerchantUser mu : mus) {
                    JSONObject muObject = new JSONObject();
                    muObject.put("id", mu.getId());
                    muObject.put("name", mu.getName());
                    muObject.put("email", mu.getEmail());
                    muObject.put("phone", mu.getPhone());
                    muObject.put("created_on", mu.getUpdated_on());
                    muObject.put("status", mu.getStatus());
                    muObject.put("delete", false);
                    muObject.put("generate_pw", false);
                    m_us_array.put(muObject);
                    
                    
                    //Get privileges
                    JSONArray admins_privileges_array = new JSONArray();
                    for (UserPrivilege up : mu.getPrivileges()) {
                        admins_privileges_array.put(up.getPrivilege());
                    }
                    muObject.put("privileges", admins_privileges_array);
                }
                u_p_.put("admins", m_us_array);
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
    
    
    /*
    * Queries the database to get the user 
    * by their email address.
    * 
    * Returns User object or null.
    */
    public Merchant getMerchantByAccountNumber(String account) {
        
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
                m.setShort_name(rs.getString("short_name"));
                m.setUsers(getMerchantUsers(m));
                String allowed_apis_string = rs.getString("allowed_apis")!= null ? 
                        rs.getString("allowed_apis"): "";
                
                String[] allowed_apis;
                if (allowed_apis_string.isEmpty()) {
                    allowed_apis = new String[0];
                } else {
                    allowed_apis = allowed_apis_string.split(",");
                }
                m.setAllowed_apis(allowed_apis);
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
    
    
    public List<UserPrivilege> getUserPrivileges(User user) {
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
    * @Param id: This is the ID of the user.
    * Returns User object or null.
    */
    public Merchant getMerchantByAccountNumber(String account, String id) {
        
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_number", account);
        parameters.addValue("id", id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" "
                + " WHERE account_number=:account_number AND id <> :id";
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
                m.setShort_name(rs.getString("short_name"));
                m.setUsers(getMerchantUsers(m));
                String allowed_apis_string = rs.getString("allowed_apis")!= null ? 
                        rs.getString("allowed_apis"): "";
                
                String[] allowed_apis;
                if (allowed_apis_string.isEmpty()) {
                    allowed_apis = new String[0];
                } else {
                    allowed_apis = allowed_apis_string.split(",");
                }
                m.setAllowed_apis(allowed_apis);
                //user.setPrivileges(getUserPrivileges(user));Cla
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
    
    
    public List<MerchantUser> getMerchantUsers(Merchant merchant) {
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
                    user.setPrivileges(getUserPrivileges(user));
                    return user;
                }
            };
            
            List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
            
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
    
    /*
    * API to add new merchant by Authorized app.
    */
    @PostMapping(path="/internalAppAddMerchant")

    public String internalAppAddMerchant(@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        
        Setting internalAppAccessAuths = Common.getSettings("internal_app_access_auths", jdbcTemplate);
        if (internalAppAccessAuths == null || internalAppAccessAuths.getSetting_value().isEmpty()) {
           return GeneralException
            .getError("140", GeneralException.ERRORS_140);
        }
        
        Setting internalAppAccessIps = Common.getSettings("internal_app_access_ips", jdbcTemplate);
        if (internalAppAccessIps == null || internalAppAccessIps.getSetting_value().isEmpty()) {
           return GeneralException
            .getError("141", GeneralException.ERRORS_141);
        }
        
        //Get User's Remote IP address
        String client_ip = request.getRemoteAddr();
        
        try {
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String account_number = generateMerchantNumber();
            String name = sObject.getString("name");
            String status = sObject.getString("status");
            String account_type = sObject.getString("account_type");
            String short_name = sObject.getString("short_name");
            Boolean generate_new_keys = sObject.getBoolean("generate_new_keys");
            JSONArray allowed_apis_array = sObject.getJSONArray("allowed_apis");
            String authorizationKey = sObject.getString("authorizationKey");
            
            String[] allowed_auths = internalAppAccessAuths.getSetting_value().split(",");
            boolean found_auth = false;
            boolean found_client_ip = false;
            for (String a_s : allowed_auths) {
                if (a_s.trim().equals(authorizationKey)) {
                    found_auth = true;
                }
            }
            
            if (!found_auth) {
                return GeneralException.getError("138", GeneralException.ERRORS_138).replaceAll("%s", authorizationKey);
            }
            
            String[] allowed_ips = internalAppAccessIps.getSetting_value().split(",");
            for (String a_ip : allowed_ips) {
                if (a_ip.trim().equals(client_ip)) {
                    found_client_ip = true;
                }
            }
            if (!found_client_ip) {
                return GeneralException.getError("139", GeneralException.ERRORS_139).replaceAll("%s", client_ip);
            }
            
            String allowed_apis = Common.imploadStringJsonArray(allowed_apis_array);
            
            Merchant newMerchant = new Merchant();
            newMerchant.setCreated_by("SYSTEM:-"+authorizationKey);
            newMerchant.setName(name);
            newMerchant.setAccount_type(account_type);
            newMerchant.setStatus(status);
            newMerchant.setShort_name(short_name);
            
            JSONArray users = sObject.getJSONArray("admins");
            Merchant u  = getMerchantByAccountNumber(account_number);
            if (u != null) {
                return GeneralException
                    .getError("108", String.format(GeneralException.ERRORS_108, "Merchant ", name));
            }
            
            //Now add the user to database
            String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANTS+" "
                +" SET `name`=:name,"
                +" `account_number`=:account_number, "
                +" `created_by`=:created_by,"
                +" `status`=:status,"
                +" `short_name`=:short_name,"
                +" `allowed_apis`=:allowed_apis,"
                +" `account_type`=:account_type";
            
            sql += ", `public_key`=:public_key, "
                +"`private_key`=:private_key ";
            
            
            String sqlAdmins = "INSERT INTO "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `merchant_id`=:merchant_id,"
                +" `name`=:name, "
                +" `email`=:email, "
                +" `phone`=:phone, "
                +" `password`=:password, "
                +" `status`=:status ";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("account_number", account_number);
            parameters.addValue("created_by", "SYSTEM:-"+authorizationKey);
            parameters.addValue("status", status);
            parameters.addValue("account_type", account_type);
            parameters.addValue("name", name);
            parameters.addValue("short_name", short_name);
            parameters.addValue("allowed_apis", allowed_apis);
            KeyPairStrings keys = null;
            
            keys = Common.generateKeyPair();
            parameters.addValue("private_key", keys.getPrivate_key());
            parameters.addValue("public_key", keys.getPublic_key());
            
            final String sql_ = sql;
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        //long userId;
                        jdbcTemplate.update(sql_, parameters, keyHolder);
                        //Now insert privileges
                        BigInteger merchantId = (BigInteger)keyHolder.getKey();
                        
                        KeyHolder keyHolderUser = new GeneratedKeyHolder();
                        MapSqlParameterSource privParams;
                        for (int i=0; i < users.length(); i++) {
                            keyHolderUser = new GeneratedKeyHolder();
                            JSONObject usersObject = users.getJSONObject(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("merchant_id", merchantId);
                            privParams.addValue("name", usersObject.getString("name"));
                            privParams.addValue("phone", usersObject.getString("phone"));
                            String email = usersObject.getString("email");
                            privParams.addValue("email", email);
                            privParams.addValue("status", usersObject.getString("status"));
                            
                            String password = Common.randomAlphaNumericString(10);
                            privParams.addValue("password", Common.getSha256EncodedString(password));
                            //privParams.addValue("name", privilege.getString("name"));
                            long privId = jdbcTemplate.update(sqlAdmins, privParams, keyHolderUser);
                            
                            BigInteger userId = (BigInteger)keyHolderUser.getKey();
                            
                            //Now add merchant users. First delete 
                            JSONArray uPrivileges = usersObject.getJSONArray("privileges");
                            for (int p=0; p < uPrivileges.length(); p++) {
                                String privilege = uPrivileges.getString(p);
                                privParams = new MapSqlParameterSource();
                                privParams.addValue("admin_id", userId);
                                privParams.addValue("privilege", privilege);
                                jdbcTemplate.update(sqlPrivileges, privParams);
                            }
                            
                            //Send an email with login details
                            MerchantUser mU = getMerchantUserByEmail(userId+"", email);
                            if (mU != null) {
                                //Send an email with user's credentials
                                sendEmailOnUpdatingMerchantUserPassword(mU, password);
                            }
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
                JSONObject accountInfo = new JSONObject();
                accountInfo.put("account_number", account_number);
                if (keys != null) {
                    accountInfo.put("private_key", keys.getPrivate_key());
                }
                return GeneralSuccessResponse
                    .getMessageOnInternalAppRes("000", GeneralSuccessResponse.SUCCESS_000, accountInfo);
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
    * API to add a new merchant to the database
    */
    @PostMapping(path="/addMerchant")

    public String addMerchant (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            User sessionUser = (User) session.getAttribute("user");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("CREATE_MERCHANT", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String created_by = sessionUser.getName();
            String account_number = generateMerchantNumber();
            String name = sObject.getString("name");
            String status = sObject.getString("status");
            String account_type = sObject.getString("account_type");
            String short_name = sObject.getString("short_name");
            Boolean generate_new_keys = sObject.getBoolean("generate_new_keys");
            JSONArray allowed_apis_array = sObject.getJSONArray("allowed_apis");
            //String[] allowed_apis = new String[allowed_apis_array.length()];
            /*for (int i=0; i < allowed_apis_array.length(); i++) {
                allowed_apis[i] = allowed_apis_array.getString(i);
            }*/
            String allowed_apis = Common.imploadStringJsonArray(allowed_apis_array);
            
            Merchant newMerchant = new Merchant();
            newMerchant.setCreated_by(created_by);
            newMerchant.setName(name);
            newMerchant.setAccount_type(account_type);
            newMerchant.setStatus(status);
            newMerchant.setShort_name(short_name);
            
            JSONArray users = sObject.getJSONArray("admins");
            Merchant u  = getMerchantByAccountNumber(account_number);
            if (u != null) {
                return GeneralException
                    .getError("108", String.format(GeneralException.ERRORS_108, "Merchant ", name));
            }
            
            //Now add the user to database
            String sql = "INSERT INTO "+Common.DB_TABLE_MERCHANTS+" "
                +" SET `name`=:name,"
                +" `account_number`=:account_number, "
                +" `created_by`=:created_by,"
                +" `status`=:status,"
                +" `short_name`=:short_name,"
                +" `allowed_apis`=:allowed_apis,"
                +" `account_type`=:account_type";
            if (generate_new_keys) {
                sql += ", `public_key`=:public_key, "
                    +"`private_key`=:private_key ";
            }
            
            String sqlAdmins = "INSERT INTO "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `merchant_id`=:merchant_id,"
                +" `name`=:name, "
                +" `email`=:email, "
                +" `phone`=:phone, "
                +" `password`=:password, "
                +" `status`=:status ";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
            
            //Map<String, Object> parameters = new HashMap<String, Object>();
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("account_number", account_number);
            parameters.addValue("created_by", created_by);
            parameters.addValue("status", status);
            parameters.addValue("account_type", account_type);
            parameters.addValue("name", name);
            parameters.addValue("short_name", short_name);
            parameters.addValue("allowed_apis", allowed_apis);
            if (generate_new_keys) {
                KeyPairStrings keys = Common.generateKeyPair();
                parameters.addValue("private_key", keys.getPrivate_key());
                parameters.addValue("public_key", keys.getPublic_key());
            }
            final String sql_ = sql;
            
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        KeyHolder keyHolder = new GeneratedKeyHolder();
                        //long userId;
                        jdbcTemplate.update(sql_, parameters, keyHolder);
                        //Now insert privileges
                        BigInteger merchantId = (BigInteger)keyHolder.getKey();
                        
                        KeyHolder keyHolderUser = new GeneratedKeyHolder();
                        MapSqlParameterSource privParams;
                        for (int i=0; i < users.length(); i++) {
                            keyHolderUser = new GeneratedKeyHolder();
                            JSONObject usersObject = users.getJSONObject(i);
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("merchant_id", merchantId);
                            privParams.addValue("name", usersObject.getString("name"));
                            privParams.addValue("phone", usersObject.getString("phone"));
                            String email = usersObject.getString("name");
                            privParams.addValue("email", email);
                            privParams.addValue("status", usersObject.getString("status"));
                            
                            String password = Common.randomAlphaNumericString(10);
                            privParams.addValue("password", Common.getSha256EncodedString(password));
                            //privParams.addValue("name", privilege.getString("name"));
                            long privId = jdbcTemplate.update(sqlAdmins, privParams, keyHolderUser);
                            
                            BigInteger userId = (BigInteger)keyHolderUser.getKey();
                            
                            //Now add merchant users. First delete 
                            JSONArray uPrivileges = usersObject.getJSONArray("privileges");
                            for (int p=0; p < uPrivileges.length(); p++) {
                                String privilege = uPrivileges.getString(p);
                                privParams = new MapSqlParameterSource();
                                privParams.addValue("admin_id", userId);
                                privParams.addValue("privilege", privilege);
                                jdbcTemplate.update(sqlPrivileges, privParams);
                            }
                            
                            //Send an email with login details
                            MerchantUser mU = getMerchantUserByEmail(userId+"", email);
                            if (mU != null) {
                                //Send an email with user's credentials
                                sendEmailOnUpdatingMerchantUserPassword(mU, password);
                            }
                               
                        }
                        
                        //Now insert auditTrail
                        String actionInsert = Common.recordAction(sessionUser, 
                                "Added new merchant "+newMerchant.toString(), jdbcTemplate);
                        
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
    
    private String generateMerchantNumber() {
        
        String sqlSelect = "SELECT COUNT(*) AS count_num  FROM "+Common.DB_TABLE_MERCHANTS+" ";
        RowMapper rm = new RowMapper<Integer>() {
        public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
                
                return rs.getInt("count_num");
            }
        };
        
        List<Integer> accs = jdbcTemplate.query(sqlSelect, new MapSqlParameterSource(), rm);
        int acc = (1000000 + accs.get(0));
        String sqlSelect_  =  sqlSelect+" WHERE account_number = '"+acc+"' ";
        
        rm = new RowMapper<Integer>() {
        public Integer mapRow(ResultSet rs, int rowNum) throws SQLException {
                return rs.getInt("count_num");
            }
        };
        
        List<Integer> accs_2 = jdbcTemplate.query(sqlSelect_, new MapSqlParameterSource(), rm);
        
        if (accs_2.get(0) > 0) {
            return generateMerchantNumber();
        } else {
            return acc+"";
        }
    }
    
    
    private MerchantUser getMerchantUserByEmail(String merchant_id, String email) {
        
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_USERS+" ";
        sqlSelect += " WHERE merchant_id='"+merchant_id+"' AND email = '"+email+"' ";
        RowMapper rm = new RowMapper<MerchantUser>() {
        public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser u = new MerchantUser();
                u.setId(rs.getLong("id"));
                u.setName(rs.getString("name"));
                u.setEmail(rs.getString("email"));
                u.setPhone(rs.getString("phone"));
                u.setStatus(rs.getString("status"));
                u.setPassword(rs.getString("password"));
                u.setCreated_on(rs.getString("created_on"));
                u.setUpdated_on(rs.getString("updated_on"));
                
                return u;
            }
        };
        
        List<MerchantUser> uList = jdbcTemplate.query(sqlSelect, new MapSqlParameterSource(), rm);
        
        if (uList.size() > 0) {
            return uList.get(0);
        } else {
            return null;
        }
    }
    
    private MerchantUser getMerchantUserByEmail(String merchant_id, String email, String id) {
        
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANT_USERS+" ";
        sqlSelect += " WHERE merchant_id='"+merchant_id+"' AND email = '"+email+"' "
                + " AND id <> '"+id+"'";
        RowMapper rm = new RowMapper<MerchantUser>() {
        public MerchantUser mapRow(ResultSet rs, int rowNum) throws SQLException {
                MerchantUser u = new MerchantUser();
                u.setId(rs.getLong("id"));
                u.setName(rs.getString("name"));
                u.setEmail(rs.getString("email"));
                u.setPhone(rs.getString("phone"));
                u.setStatus(rs.getString("status"));
                u.setPassword(rs.getString("password"));
                u.setCreated_on(rs.getString("created_on"));
                u.setUpdated_on(rs.getString("updated_on"));
                
                return u;
            }
        };
        
        List<MerchantUser> uList = jdbcTemplate.query(sqlSelect, new MapSqlParameterSource(), rm);
        
        if (uList.size() > 0) {
            return uList.get(0);
        } else {
            return null;
        }
    }
    
    /*
    * API to add a new admin to the database
    */
    @PostMapping(path="/editMerchant")

    public String editMerchant (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) {
        //Set the response header
        
        try {
            
            if (!isLoggedIn (request )) {
                return GeneralException
                    .getError("107", GeneralException.ERRORS_107);
            }
            
            User sessionUser = (User) session.getAttribute("user");
            
            //Check permissions
            if (!Common.isUserAllowedAccessToThis("UPDATE_MERCHANT", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String created_by = sessionUser.getName();
            String account_number = sObject.getString("account_number");
            String name = sObject.getString("name");
            String status = sObject.getString("status");
            String account_type = sObject.getString("account_type");
            String short_name = sObject.getString("short_name");
            String id = sObject.getString("id");
            Boolean generate_new_keys = sObject.getBoolean("generate_new_keys");
            JSONArray allowed_apis_array = sObject.getJSONArray("allowed_apis");
            String allowed_apis = Common.imploadStringJsonArray(allowed_apis_array);
            
            Merchant newMerchant = new Merchant();
            newMerchant.setCreated_by(created_by);
            newMerchant.setName(name);
            newMerchant.setAccount_type(account_type);
            newMerchant.setStatus(status);
            newMerchant.setAccount_number(account_number);
            newMerchant.setShort_name(short_name);
            
            
            JSONArray users = sObject.getJSONArray("admins");
            Merchant u  = getMerchantByAccountNumber(account_number, id);
            if (u != null) {
                return GeneralException
                    .getError("108", String.format(GeneralException.ERRORS_108, "Merchant ", name));
            }
            
            //Now add the user to database
            String sql = "UPDATE "+Common.DB_TABLE_MERCHANTS+" "
                +" SET `name`=:name,"
                +" `account_number`=:account_number, "
                +" `created_by`=:created_by,"
                +" `status`=:status,"
                +" `short_name`=:short_name,"
                +" `allowed_apis`=:allowed_apis,"
                +" `account_type`=:account_type";
            
            if (generate_new_keys) {
                sql += ", `public_key`=:public_key, "
                    +" `private_key`=:private_key ";
            }
            
            sql += " WHERE `id` = :id ";
            
            String sqlDropMerchantUser = "DELETE FROM "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" WHERE `id`=:id ";
            
            String sqlInsertMerchantUser = "INSERT INTO "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `merchant_id`=:merchant_id,"
                +" `name`=:name, "
                +" `phone`=:phone, "
                +" `email`=:email, "
                +" `status`=:status, "
                +" `password`=:password "
                +" ";
            
            String sqlUpdateMerchantUser = "UPDATE "+Common.DB_TABLE_MERCHANT_USERS+" "
                +" SET `merchant_id`=:merchant_id,"
                +" `name`=:name, "
                +" `phone`=:phone, "
                +" `email`=:email, "
                +" `status`=:status ";
            
            String sqlPrivilegesDropExistings = "DELETE FROM "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" WHERE `admin_id`=:admin_id ";
            
            String sqlPrivileges = "INSERT INTO "+Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES+" "
                +" SET `admin_id`=:admin_id,"
                +" `privilege`=:privilege ";
                
            
            MapSqlParameterSource parameterDropPrivileges = new MapSqlParameterSource();
            parameterDropPrivileges.addValue("admin_id", sObject.getString("id"));
            
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            
            parameters.addValue("account_number", account_number);
            parameters.addValue("created_by", created_by);
            parameters.addValue("status", status);
            parameters.addValue("account_type", account_type);
            parameters.addValue("name", name);
            parameters.addValue("short_name", short_name);
            parameters.addValue("id", id);
            parameters.addValue("allowed_apis", allowed_apis);
            if (generate_new_keys) {
                KeyPairStrings keys = Common.generateKeyPair();
                parameters.addValue("private_key", keys.getPrivate_key());
                parameters.addValue("public_key", keys.getPublic_key());
            }
            
            final String sql_ = sql;
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            String result = template.execute(new TransactionCallback<String>() {
                @Override
                public String doInTransaction(TransactionStatus status) {
                    try {

                        //Update Merchants Table
                        jdbcTemplate.update(sql_, parameters);
                        
                        //Update each Merchant user
                        MapSqlParameterSource privParams;
                        for (int i=0; i < users.length(); i++) {
                            //First check if merchant user exists
                            JSONObject userObject = users.getJSONObject(i);
                            String name = userObject.getString("name");
                            String email = userObject.getString("email");
                            String row_id = userObject.getString("id");
                            String phone =  userObject.getString("phone");
                            String status_ =  userObject.getString("status");
                            
                            MerchantUser mu = new MerchantUser();
                            mu.setName(name);
                            mu.setEmail(email);
                            mu.setPhone(phone);
                            mu.setStatus(status_);
                            mu.setMerchant_account_type(newMerchant.getAccount_type());
                            mu.setMerchant_name(newMerchant.getName());
                            mu.setMerchant_number(newMerchant.getAccount_number());
                            mu.setMerchant_status(newMerchant.getStatus());
                            
                            Boolean generate_pw =  userObject.getBoolean("generate_pw");
                            Boolean delete = !userObject.isNull("delete") ? 
                                    userObject.getBoolean("delete") : false;
                            
                            //Check if we are to delete
                            if (delete) {
                                privParams = new MapSqlParameterSource();
                                privParams.addValue("id", row_id);
                                long privId = jdbcTemplate.update(sqlDropMerchantUser, privParams);
                                continue;
                            }
                            
                            privParams = new MapSqlParameterSource();
                            privParams.addValue("merchant_id", id);
                            privParams.addValue("name", name);
                            privParams.addValue("email", email);
                            privParams.addValue("phone", phone);
                            privParams.addValue("status", status_);
                            
                            if (row_id.isEmpty()) {
                                //This is a new MerchantUser
                                String password = Common.randomAlphaNumericString(10);
                                privParams.addValue("password", Common.getSha256EncodedString(password));
                                
                                //Check if this user exists
                                MerchantUser mU = getMerchantUserByEmail(id, email);
                                if (mU != null) {
                                    return GeneralException
                                        .getError("108", 
                                                String.format(GeneralException.ERRORS_108, 
                                                "Merchant User ", name));
                                }
                                
                                //Send an email with user's credentials
                                sendEmailOnUpdatingMerchantUserPassword(mu, password);
                                
                                GeneratedKeyHolder keyHolderUser = new GeneratedKeyHolder();
                                long privId = jdbcTemplate.update(sqlInsertMerchantUser, privParams, keyHolderUser);
                                BigInteger userId = (BigInteger)keyHolderUser.getKey();
                                row_id = userId+"";
                            } else {
                                //Now update
                                String sqlUpdateMerchantUser_ = sqlUpdateMerchantUser;
                                String password = "";
                                if (generate_pw) {
                                    password = Common.randomAlphaNumericString(10);
                                    sqlUpdateMerchantUser_ += ", password = '"+Common.getSha256EncodedString(password)+"'";
                                }
                                sqlUpdateMerchantUser_ += " WHERE id='"+row_id+"' ";
                                MerchantUser mU = getMerchantUserByEmail(id, email, row_id);
                               
                                if (mU != null) {
                                    return GeneralException
                                        .getError("108", 
                                                String.format(GeneralException.ERRORS_108, 
                                                "Merchant User ", name));
                                }
                                if (generate_pw) {
                                    sendEmailOnUpdatingMerchantUserPassword(mu, password);
                                }
                                long privId = jdbcTemplate.update(sqlUpdateMerchantUser_, privParams);
                                
                            }
                            

                            //Now update user privileges
                            //Now drop privileges
                            MapSqlParameterSource parameterDropPrivileges = new MapSqlParameterSource();
                            parameterDropPrivileges.addValue("admin_id", row_id);
                            jdbcTemplate.update(sqlPrivilegesDropExistings, parameterDropPrivileges);

                            JSONArray uPrivileges = userObject.getJSONArray("privileges");
                            for (int p=0; p < uPrivileges.length(); p++) {
                                String privilege = uPrivileges.getString(p);
                                privParams = new MapSqlParameterSource();
                                privParams.addValue("admin_id", row_id);
                                privParams.addValue("privilege", privilege);
                                jdbcTemplate.update(sqlPrivileges, privParams);
                            }
                        }
                        //Now insert audit Trail
                        String actionInsert = Common.recordAction(sessionUser, 
                                "Updated merchant "+newMerchant.toString(), jdbcTemplate);
                        
                        //If it failed to execute the statement to record this action
                        if (!actionInsert.equals("success")) {
                            status.setRollbackOnly();
                            return actionInsert;
                        }
                        
                        //transactionManager.commit(status);
                        
                        return "success";
                    } catch (Exception e) {
                        
                        e.printStackTrace();
                        //transactionManager.rollback(status);
                        status.setRollbackOnly();
                        return GeneralException
                            .getError("102", GeneralException.ERRORS_102+"***: "+e.getStackTrace());
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
                    .log(Level.SEVERE, ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+": "+ex.getMessage());
        }
    }
    
    private void sendEmailOnUpdatingMerchantUserPassword(MerchantUser u, String password){
        //Now send verification email
        Setting emailContentManage = Common.getSettings("email_tmp_on_creating_merchant_user", jdbcTemplate);
        Setting app_setting_app_url = Common.getSettings("app_setting_app_url", jdbcTemplate);
        String emailContent_ = emailContentManage.getSetting_value()
                .replace("{name}", u.getName());
        emailContent_ = emailContent_.replace("{url}", app_setting_app_url.getSetting_value());
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
    * API to deleteing an admin from the database
    */
    @PostMapping(path="/deleteMerchant")

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
            if (!Common.isUserAllowedAccessToThis("DELETE_MERCHANT", sessionUser)) {
                return GeneralException
                    .getError("110", GeneralException.ERRORS_110);
            }
            
            
            JSONObject sObject = new JSONObject(requestBody);
            
            String account_type = sObject.getString("account_type");
            String account_number = sObject.getString("account_number");
            String name = sObject.getString("name");
            String short_name = sObject.getString("short_name");
            String status = sObject.getString("status");
            String id = sObject.getString("id");
            Merchant newUser = new Merchant();
            newUser.setAccount_number(account_number);
            newUser.setName(name);
            newUser.setShort_name(short_name);
            newUser.setAccount_type(account_type);
            newUser.setStatus(status);
            
            
            //Now add the user to database
            String sql = "DELETE FROM "+Common.DB_TABLE_MERCHANTS+" "
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
                                "DELETED merchant "+newUser.toString(), jdbcTemplate);
                        
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
