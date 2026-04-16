/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.util.List;

/**
 *
 * @author josephtabajjwa
 */
public class Merchant {
    Long id;
    String name;
    String status;
    String account_number;
    String created_on;
    String updated_on;
    String account_type;
    String created_by;
    String public_key;
    String private_key;
    List<MerchantUser> users;
    String[] allowed_apis;
    String short_name;

    public String getShort_name() {
        return short_name;
    }

    public void setShort_name(String short_name) {
        this.short_name = short_name;
    }

    public String[] getAllowed_apis() {
        return allowed_apis;
    }

    public void setAllowed_apis(String[] allowed_apis) {
        this.allowed_apis = allowed_apis;
    }
    
    

    public String getPublic_key() {
        return public_key;
    }

    public void setPublic_key(String public_key) {
        this.public_key = public_key;
    }

    public String getPrivate_key() {
        return private_key;
    }

    public void setPrivate_key(String private_key) {
        this.private_key = private_key;
    }
    
    
    public String getUpdated_on() {
        return updated_on;
    }

    public void setUpdated_on(String updated_on) {
        this.updated_on = updated_on;
    }
    
    

    public List<MerchantUser> getUsers() {
        return users;
    }

    public void setUsers(List<MerchantUser> users) {
        this.users = users;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccount_number() {
        return account_number;
    }

    public void setAccount_number(String account_number) {
        this.account_number = account_number;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public String getAccount_type() {
        return account_type;
    }

    public void setAccount_type(String account_type) {
        this.account_type = account_type;
    }

    public String getCreated_by() {
        return created_by;
    }

    public void setCreated_by(String created_by) {
        this.created_by = created_by;
    }
    
    public String toString() {
        return "Name: "+name+", Account Number: "+account_number+", account_type: "+account_type;
    }
}
