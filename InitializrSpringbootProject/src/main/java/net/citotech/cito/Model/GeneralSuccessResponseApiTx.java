/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.GeneralException;
import net.citotech.cito.GeneralSuccessResponse;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

/**
 *
 * @author josephtabajjwa
 */
public class GeneralSuccessResponseApiTx extends GeneralSuccessResponse{
    
    public static String getMessage(String code, String message) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("code", code);
            obj.put("message", message);
        } catch (JSONException ex) {
            Logger.getLogger(GeneralException.class.getName()).log(Level.SEVERE, null, ex);
        }
        return obj.toString();
    }
}
