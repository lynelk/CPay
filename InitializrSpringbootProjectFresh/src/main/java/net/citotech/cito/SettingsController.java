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
import java.util.Iterator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.User;
import net.citotech.cito.Model.UserPrivilege;
import org.springframework.beans.factory.annotation.Autowired;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
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
    
    
    static JSONArray parseSettingsCatalog(String settings, String fallbackGroup) {
        String catalog = settings == null ? "" : settings.trim();
        if (catalog.isEmpty()) {
            return new JSONArray();
        }
        if (catalog.startsWith("[")) {
            return normalizeSettingsCatalog(new JSONArray(catalog), fallbackGroup);
        }

        JSONObject legacySettings = new JSONObject(catalog);
        JSONArray normalized = new JSONArray();
        Iterator<String> keys = legacySettings.keys();
        while (keys.hasNext()) {
            String name = keys.next();
            normalized.put(settingsRow(
                name,
                labelFromSettingName(name),
                jsonValueText(legacySettings, name, ""),
                fallbackGroup,
                name));
        }
        return normalized;
    }

    private static JSONArray normalizeSettingsCatalog(JSONArray settings, String fallbackGroup) {
        JSONArray normalized = new JSONArray();
        for (int i = 0; i < settings.length(); i++) {
            JSONObject setting = settings.optJSONObject(i);
            if (setting == null) {
                continue;
            }
            String name = settingText(setting, "name", "").trim();
            if (name.isEmpty()) {
                continue;
            }
            normalized.put(settingsRow(
                name,
                settingText(setting, "label", labelFromSettingName(name)),
                settingText(setting, "setting_value", ""),
                settingText(setting, "setting_group", fallbackGroup),
                settingText(setting, "description", name)));
        }
        return normalized;
    }

    private static JSONObject settingsRow(String name, String label, String value, String group, String description) {
        JSONObject setting = new JSONObject();
        setting.put("name", name);
        setting.put("label", label == null || label.isBlank() ? labelFromSettingName(name) : label);
        setting.put("setting_value", value == null ? "" : value);
        setting.put("setting_group", group == null || group.isBlank() ? "Application" : group);
        setting.put("description", description == null || description.isBlank() ? name : description);
        return setting;
    }

    private static String settingText(JSONObject setting, String key, String defaultValue) {
        if (setting == null || !setting.has(key) || setting.isNull(key)) {
            return defaultValue;
        }
        Object value = setting.opt(key);
        if (value == null || JSONObject.NULL.equals(value)) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private static String jsonValueText(JSONObject setting, String key, String defaultValue) {
        Object value = setting.opt(key);
        if (value == null || JSONObject.NULL.equals(value)) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private static String labelFromSettingName(String name) {
        if (name == null || name.isBlank()) {
            return "Setting";
        }
        String[] parts = name.replaceAll("[._-]+", " ").trim().split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                label.append(part.substring(1).toLowerCase());
            }
        }
        return label.length() == 0 ? "Setting" : label.toString();
    }

    private static String invalidSettingsCatalogError() {
        return GeneralException
            .getError("102", GeneralException.ERRORS_102 + ": Settings catalog is empty or invalid.");
    }

    private static String validateSettingsPayload(JSONArray newSettings) {
        if (newSettings == null || newSettings.length() == 0) {
            return invalidSettingsCatalogError();
        }
        return "success";
    }
    @PostMapping(path="/getSettings")

    public String getSettings (@RequestBody String requestBody, 
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        //Set the response header
        
        //First set session variable
        HttpSession session = request.getSession();
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
            if (default_settings.length() == 0) {
                return invalidSettingsCatalogError();
            }
            
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
                    u_p_.put("label", settingText(jObject, "label", us.getLabel()));
                    u_p_.put("description", settingText(jObject, "description", us.getDescription()));
                    u_p_.put("setting_group", settingText(jObject, "setting_group", us.getGroup()));
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
        HttpSession session = request.getSession();
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
            if (default_settings.length() == 0) {
                return invalidSettingsCatalogError();
            }

            
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
                    u_p_.put("label", settingText(jObject, "label", us.getLabel()));
                    u_p_.put("description", settingText(jObject, "description", us.getDescription()));
                    u_p_.put("setting_group", settingText(jObject, "setting_group", us.getGroup()));
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
        if (settingsArray == null) {
            return null;
        }
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
        if (settingsArray == null) {
            return null;
        }
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
        HttpSession session = request.getSession();
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
        HttpSession session = request.getSession();
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
        return loadSettingsCatalog(Common.CLASS_PATH_DEFAULT_SETTINGS, "Application");
    }
    
    private JSONArray getDefaultMerchantSettings() throws IOException {
        return loadSettingsCatalog(Common.CLASS_PATH_DEFAULT_MERCHANT_SETTINGS, "Merchant");
    }

    private JSONArray loadSettingsCatalog(String path, String fallbackGroup) {
        try (InputStream resource = new ClassPathResource(path).getInputStream()) {
            String settings = StreamUtils.copyToString(resource, Charset.defaultCharset());
            return parseSettingsCatalog(settings, fallbackGroup);
        } catch (Exception ex) {
            Logger.getLogger(SettingsController.class.getName())
                    .log(Level.SEVERE, "Failed to load settings catalog " + path, ex);
            return new JSONArray();
        }
    }
    
    private List<Setting> getCurrentSettings() {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_SETTINGS;   
            
        RowMapper<Setting> rm = new RowMapper<Setting>() {
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

        RowMapper<Setting> rm = new RowMapper<Setting>() {
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
            
        RowMapper<Setting> rm = new RowMapper<Setting>() {
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
            
        RowMapper<Setting> rm = new RowMapper<Setting>() {
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
        String validation = validateSettingsPayload(new_settings);
        if (!validation.equals("success")) {
            return validation;
        }
        
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
        String validation = validateSettingsPayload(new_settings);
        if (!validation.equals("success")) {
            return validation;
        }
        
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
        String validation = validateSettingsPayload(new_settings);
        if (!validation.equals("success")) {
            return validation;
        }
        
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
        String validation = validateSettingsPayload(new_settings);
        if (!validation.equals("success")) {
            return validation;
        }
        
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

