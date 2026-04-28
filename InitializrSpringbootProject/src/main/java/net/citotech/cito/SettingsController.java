/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author josephtabajjwa
 */
@RestController 
@RequestMapping(path="/settings")
public class SettingsController {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    @Autowired
    TransactionTemplate transactionTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    private HttpSession session;
    
    @PostMapping(path="/getSettings")

    public String getSettings (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            
            
            //First check if there settings
            JSONArray default_settings = getDefaultSettings();
            
            String sSettings = updateCurrentSettings(default_settings);
            if (!sSettings.equals("success")) {
                return sSettings;
            }
            
            //ResultSet rs; 
            List<Setting> listSettings = getCurrentSettings();
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray admins_array = new JSONArray();
            for (Setting us : listSettings) {
                JSONObject u_p_ = new JSONObject();
                
                JSONObject jObject = isInCurrentSettings(us.getName(), default_settings);
                if ( jObject != null) {
                    u_p_.put("id", us.getId());
                    u_p_.put("name", us.getName());
                    u_p_.put("setting_value", us.getSetting_value());
                    u_p_.put("label", jObject.getString("label"));
                    u_p_.put("description", jObject.getString("description"));
                    u_p_.put("setting_group", jObject.getString("setting_group"));
                    admins_array.put(u_p_);
                }
            }
            resJson.put("data", admins_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    
    @PostMapping(path="/getMerchantSettings")

    public String getMerchantSettings (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            
            JSONObject sObject = new JSONObject(requestBody);
            
            Long merchant_id = sObject.getLong("merchant_id");
            
            //First check if there settings
            JSONArray default_settings = getDefaultMerchantSettings();

            
            String sSettings = updateCurrentMerchantSettings(default_settings, merchant_id);
            if (!sSettings.equals("success")) {
                return sSettings;
            }
            
            //ResultSet rs; 
            List<Setting> listSettings = getCurrentMerchantSettings(merchant_id);
            JSONObject resJson = new JSONObject();
            resJson.put("code", "000");
            resJson.put("message", "true");
            JSONArray admins_array = new JSONArray();
            for (Setting us : listSettings) {
                JSONObject u_p_ = new JSONObject();
                
                JSONObject jObject = isInCurrentMerchantSettings(us.getName(), default_settings);
                if ( jObject != null) {
                    u_p_.put("id", us.getId());
                    u_p_.put("name", us.getName());
                    u_p_.put("setting_value", us.getSetting_value());
                    u_p_.put("label", jObject.getString("label"));
                    u_p_.put("description", jObject.getString("description"));
                    u_p_.put("setting_group", jObject.getString("setting_group"));
                    u_p_.put("merchant_id", merchant_id);
                    admins_array.put(u_p_);
                } 
            }
            resJson.put("data", admins_array);
            
            return resJson.toString();
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName())
                    .log(Level.SEVERE, ex.getMessage(), ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102+" Failed to get JSON");
        }
    }
    
    private JSONObject isInCurrentSettings(String setting, JSONArray settingsArray) {
        try {
            for (int i=0; i < settingsArray.length(); i++) {
                JSONObject jObject = settingsArray.getJSONObject(i);

                if (jObject.getString("name").equals(setting)) {
                    return jObject;
                }
            }
        } catch (JSONException ex) {
            return null;
        }
        return null;
    }
    
    private JSONObject isInCurrentMerchantSettings(String setting, JSONArray settingsArray) {
        try {
            for (int i=0; i < settingsArray.length(); i++) {
                JSONObject jObject = settingsArray.getJSONObject(i);

                if (jObject.getString("name").equals(setting)) {
                    return jObject;
                }
            }
        } catch (JSONException ex) {
            return null;
        }
        return null;
    }
    
    @PostMapping(path="/updateSettings")

    public String updateSettings (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            
            
            JSONArray _settings = new JSONArray(requestBody);
            String sSettings = updateCurrentSettings(_settings, sessionUser);
            
            if (sSettings.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return sSettings;
            }
            
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    @PostMapping(path="/updateMerchantSettings")

    public String updateMerchantSettings (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws IOException {
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
            
            
            JSONArray _settings = new JSONArray(requestBody);
            String sSettings = updateCurrentMerchantSettings(_settings, sessionUser);
            
            if (sSettings.equals("success")) {
                 
                return GeneralSuccessResponse
                    .getMessage("000", GeneralSuccessResponse.SUCCESS_000);
            } else {
                return sSettings;
            }
            
            
        } catch (JSONException ex) {
            
            Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, null, ex);
            return GeneralException
                    .getError("102", GeneralException.ERRORS_102);
        }
    }
    
    private JSONArray getDefaultSettings() throws IOException {
        InputStream resource = new ClassPathResource(
            Common.CLASS_PATH_DEFAULT_SETTINGS).getInputStream();//.getFile();
        String settings = StreamUtils.copyToString(resource, Charset.defaultCharset());
        /*new String(
            Files.readAllBytes(resource.toPath())
        );*/
        JSONArray r = null;
        try {
            r = new JSONArray(settings);
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return r;
    }
    
    private JSONArray getDefaultMerchantSettings() throws IOException {
        InputStream resource = new ClassPathResource(
            Common.CLASS_PATH_DEFAULT_MERCHANT_SETTINGS).getInputStream();//.getFile();
        String settings = StreamUtils.copyToString(resource, Charset.defaultCharset());
        /*new String(
            Files.readAllBytes(resource.toPath())
        );*/
        JSONArray r = null;
        try {
            r = new JSONArray(settings);
            /*
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "REACHED "+r.toString(), "Parse JSON");
                    */
        } catch (JSONException ex) {
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "JSONException", ex);
        } catch (Exception e) {
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "Exception", e);
        }
        return r;
    }
    
    
    private List<Setting> getCurrentSettings() {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_SETTINGS;   
            
        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setLabel(rs.getString("label"));
                setting.setId(rs.getLong("id"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setDescription(rs.getString("description"));
                setting.setName(rs.getString("name"));
                setting.setGroup(rs.getString("setting_group"));
                return setting;
            }
        };
            
        //ResultSet rs; 
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        return listSettings;
    }
    
    private List<Setting> getCurrentMerchantSettings(Long merchant_id) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sqlSelect = "SELECT *  FROM "+Common.DB_MERCHANTS_SETTINGS+" "
                + " WHERE merchant_id='"+merchant_id+"'";   

        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setLabel(rs.getString("label"));
                setting.setId(rs.getLong("id"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setDescription(rs.getString("description"));
                setting.setName(rs.getString("name"));
                setting.setGroup(rs.getString("setting_group"));
                setting.setMerchant_id(rs.getLong("merchant_id"));
                return setting;
            }
        };

        //ResultSet rs; 
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        return listSettings;
    }
    
    private Setting getCurrentMerchantSettingsByName(String name, long merchant_id) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", name);
        parameters.addValue("merchant_id", merchant_id);
        String sqlSelect = "SELECT *  FROM "+Common.DB_MERCHANTS_SETTINGS; 
        sqlSelect += " WHERE merchant_id=:merchant_id AND name=:name ";
            
        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setLabel(rs.getString("label"));
                setting.setId(rs.getLong("id"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setDescription(rs.getString("description"));
                setting.setName(rs.getString("name"));
                setting.setGroup(rs.getString("setting_group"));
                setting.setMerchant_id(rs.getLong("merchant_id"));
                return setting;
            }
        };
            
        //ResultSet rs; 
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }
    
    private Setting getCurrentSettingsByName(String name) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", name);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_SETTINGS; 
        sqlSelect += " WHERE name=:name ";
            
        RowMapper rm = new RowMapper<Setting>() {
        public Setting mapRow(ResultSet rs, int rowNum) throws SQLException {
                Setting setting = new Setting();
                setting.setLabel(rs.getString("label"));
                setting.setId(rs.getLong("id"));
                setting.setSetting_value(rs.getString("setting_value"));
                setting.setDescription(rs.getString("description"));
                setting.setName(rs.getString("name"));
                setting.setGroup(rs.getString("setting_group"));
                return setting;
            }
        };
            
        //ResultSet rs; 
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }
    
    
    private String updateCurrentSettings(JSONArray new_settings) {
        
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description";
        
        String sqlUpdate = "UPDATE "+Common.DB_TABLE_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description"
            +" WHERE `name`=:name";
        
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    MapSqlParameterSource privParams;
                    for (int i=0; i < new_settings.length(); i++) {
                        JSONObject setting = new_settings.getJSONObject(i);
                        //First check if this Setting exists
                        if (setting.isNull("name")) {
                            return GeneralException
                            .getError("102", GeneralException.ERRORS_102
                                    +": On updating current setting, failed to obtain 'name' parameter "+new_settings.toString());
                        }
                        Setting thisSetting = getCurrentSettingsByName(setting.getString("name"));
                        
                        privParams = new MapSqlParameterSource();
                        privParams.addValue("name", setting.getString("name"));
                        privParams.addValue("label", setting.getString("label"));
                        privParams.addValue("setting_value", setting.getString("setting_value"));
                        privParams.addValue("setting_group", setting.getString("setting_group"));
                        privParams.addValue("description", setting.getString("description"));
                        if (thisSetting != null ) {
                            privParams.addValue("name", thisSetting.getName());
                            privParams.addValue("label", thisSetting.getLabel());
                            privParams.addValue("setting_value", thisSetting.getSetting_value());
                            privParams.addValue("setting_group", thisSetting.getGroup());
                            privParams.addValue("description", thisSetting.getDescription());
                            long privId = jdbcTemplate.update(sqlUpdate, privParams);
                        } else {
                            long privId = jdbcTemplate.update(sql, privParams);
                        }
                    }

                    //Now insert audit Trail
                    /*String actionInsert = Common.recordAction(sessionUser, 
                            "Added new admin "+newUser.toString(), jdbcTemplate);*/

                    //If it failed to execute the statement to record this action
                    /*if (!actionInsert.equals("success")) {
                        status.setRollbackOnly();
                        return actionInsert;
                    }*/

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
        
        return result;
    }
    
    
    private String updateCurrentMerchantSettings(JSONArray new_settings, Long merchant_id) {
        
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_MERCHANTS_SETTINGS+" "
            +" SET `name`=:name,"
            +" `merchant_id`=:merchant_id, "
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description";
        
        String sqlUpdate = "UPDATE "+Common.DB_MERCHANTS_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description"
            +" WHERE `name`=:name AND `merchant_id`=:merchant_id";



        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    MapSqlParameterSource privParams;
                    for (int i=0; i < new_settings.length(); i++) {
                        JSONObject setting = new_settings.getJSONObject(i);
                        //First check if this Setting exists
                        if (setting.isNull("name")) {
                            return GeneralException
                            .getError("102", GeneralException.ERRORS_102
                                    +": On updating current setting, failed to obtain 'name' parameter "+new_settings.toString());
                        }
                        
                        //Long merchant_id = setting.getLong("merchant_id");
                        Setting thisSetting = getCurrentMerchantSettingsByName(setting.getString("name"), 
                                merchant_id);
                        
                        Setting defaultSetting = getCurrentSettingsByName(setting.getString("name"));
                        
                        privParams = new MapSqlParameterSource();
                        privParams.addValue("name", setting.getString("name"));
                        privParams.addValue("label", setting.getString("label"));
                        privParams.addValue("setting_value", setting.getString("setting_value"));
                        privParams.addValue("setting_group", setting.getString("setting_group"));
                        privParams.addValue("description", setting.getString("description"));
                        privParams.addValue("merchant_id", merchant_id);
                        long privId;
                        if (thisSetting != null ) {
                            privParams.addValue("name", thisSetting.getName());
                            privParams.addValue("label", thisSetting.getLabel());
                            privParams.addValue("setting_value", thisSetting.getSetting_value());
                            privParams.addValue("setting_group", thisSetting.getGroup());
                            privParams.addValue("description", thisSetting.getDescription());
                             privId = jdbcTemplate.update(sqlUpdate, privParams);
                        } else {
                            if (defaultSetting == null) {
                                privParams.addValue("setting_value", "");
                            } else {
                                privParams.addValue("setting_value", defaultSetting.getSetting_value());
                            }
                            privId = jdbcTemplate.update(sql, privParams);
                        }
                    }

                    //Now insert audit Trail
                    /*String actionInsert = Common.recordAction(sessionUser, 
                            "Added new admin "+newUser.toString(), jdbcTemplate);*/

                    //If it failed to execute the statement to record this action
                    /*if (!actionInsert.equals("success")) {
                        status.setRollbackOnly();
                        return actionInsert;
                    }*/

                    //transactionManager.commit(status);

                    return "success";
                } catch (Exception e) {
                    //transactionManager.rollback(status);
                    e.printStackTrace();
                    Logger.getLogger(AuthenticationController.class.getName())
                            .log(Level.SEVERE, "Reaches Here - "+e.getMessage(), "");
                    status.setRollbackOnly();
                    return GeneralException
                        .getError("102", GeneralException.ERRORS_102+": "+e.getMessage());
                }
            }
        });
        
        return result;
    }
    
    
    /*
    * Updates settings on HTTP request.
    */
    private String updateCurrentSettings(JSONArray new_settings, User sessionUser) {
        
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_TABLE_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description";
        
        String sqlUpdate = "UPDATE "+Common.DB_TABLE_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description"
            +" WHERE `name`=:name";
        
        
        
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    MapSqlParameterSource privParams;
                    for (int i=0; i < new_settings.length(); i++) {
                        JSONObject setting = new_settings.getJSONObject(i);
                        //First check if this Setting exists
                        Setting thisSetting = getCurrentSettingsByName(setting.getString("name"));
                        privParams = new MapSqlParameterSource();
                        privParams.addValue("name", setting.getString("name"));
                        privParams.addValue("label", setting.getString("label"));
                        privParams.addValue("setting_value", setting.getString("setting_value"));
                        privParams.addValue("setting_group", setting.getString("setting_group"));
                        privParams.addValue("description", setting.getString("description"));
                        if (thisSetting != null ) {
                            long privId = jdbcTemplate.update(sqlUpdate, privParams);
                        } else {
                            long privId = jdbcTemplate.update(sql, privParams);
                        }
                    }

                    //Now insert audit Trail
                    String actionInsert = Common.recordAction(sessionUser, 
                            "Updated Settings to: "+new_settings.toString(), jdbcTemplate);

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
        return result;
    }
    
    private String updateCurrentMerchantSettings(JSONArray new_settings, User sessionUser) {
        
        //Now add the user to database
        String sql = "INSERT INTO "+Common.DB_MERCHANTS_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `merchant_id`=:merchant_id, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description";
        
        String sqlUpdate = "UPDATE "+Common.DB_MERCHANTS_SETTINGS+" "
            +" SET `name`=:name,"
            +" `label`=:label, "
            +" `setting_value`=:setting_value,"
            +" `setting_group`=:setting_group,"
            +" `description`=:description"
            +" WHERE `name`=:name AND `merchant_id`=:merchant_id";
        
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        String result = template.execute(new TransactionCallback<String>() {
            @Override
            public String doInTransaction(TransactionStatus status) {
                try {

                    MapSqlParameterSource privParams;
                    for (int i=0; i < new_settings.length(); i++) {
                        JSONObject setting = new_settings.getJSONObject(i);
                        //First check if this Setting exists
                        Long merchant_id = setting.getLong("merchant_id");
                        Setting thisSetting = getCurrentMerchantSettingsByName(setting.getString("name"), merchant_id);
                        //Setting defaultSetting = getCurrentSettingsByName(setting.getString("name"));
                        privParams = new MapSqlParameterSource();
                        privParams.addValue("name", setting.getString("name"));
                        privParams.addValue("label", setting.getString("label"));
                        privParams.addValue("setting_value", setting.getString("setting_value"));
                        privParams.addValue("setting_group", setting.getString("setting_group"));
                        privParams.addValue("description", setting.getString("description"));
                        privParams.addValue("merchant_id", merchant_id);

                        if (thisSetting != null ) {
                            long privId = jdbcTemplate.update(sqlUpdate, privParams);
                        } else {
                            //privParams.addValue("setting_value", defaultSetting.getSetting_value());
                            //defaultSetting
                            long privId = jdbcTemplate.update(sql, privParams);

                        }

                    }

                    //Now insert audit Trail
                    String actionInsert = Common.recordAction(sessionUser, 
                            "Updated Merchant Settings to: "+new_settings.toString(), jdbcTemplate);

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
        return result;
    }
}
