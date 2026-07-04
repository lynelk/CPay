/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 *
 * @author josephtabajjwa
 */
public class Setting {
    String name;
    String label;
    String description;
    String setting_value;
    Long id;
    String group;
    Long merchant_id;

    public Long getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(Long merchant_id) {
        this.merchant_id = merchant_id;
    }
    
    

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSetting_value() {
        return setting_value;
    }

    public void setSetting_value(String setting_value) {
        this.setting_value = setting_value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public static JSONArray getGeneralSettings() throws IOException {
        InputStream resource = new ClassPathResource(
            Common.CLASS_PATH_GENERAL_SETTINGS).getInputStream();
        String settings = StreamUtils.copyToString(resource, Charset.defaultCharset());
        try {
            return new JSONArray(settings);
        } catch (JSONException ex) {
            try {
                JSONObject singleSetting = new JSONObject(settings);
                JSONArray fallback = new JSONArray();
                fallback.put(singleSetting);
                return fallback;
            } catch (JSONException innerEx) {
                Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, null, innerEx);
                return new JSONArray();
            }
        }
    }
    
    public static String getGeneralSettingByKey(String key) {
        try {
            JSONArray r_ = getGeneralSettings();
            if (r_ == null) {
                return null;
            }
            for (int i = 0; i < r_.length(); i++) {
                JSONObject jObject = r_.getJSONObject(i);
                if (jObject.optString("name", "").equals(key)) {
                    return jObject.optString("setting_value", null);
                }
                if (jObject.has(key)) {
                    return jObject.optString(key, null);
                }
            }
            return null;
        } catch (IOException ex) {
            Logger.getLogger(Setting.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        } catch (JSONException ex) {
            Logger.getLogger(Setting.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}

