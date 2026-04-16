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
public class User implements java.io.Serializable {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private String status;
    private String created_on;
    private String updated_on;
    private String password;
    private String is_verification_timedout;
    private String email_verification_code;
    
    private List<UserPrivilege> privileges;

    public String getEmail_verification_code() {
        return email_verification_code;
    }

    public void setEmail_verification_code(String email_verification_code) {
        this.email_verification_code = email_verification_code;
    }
    
    

    public String getIs_verification_timedout() {
        return is_verification_timedout;
    }

    public void setIs_verification_timedout(String is_verification_timedout) {
        this.is_verification_timedout = is_verification_timedout;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public String getUpdated_on() {
        return updated_on;
    }

    public void setUpdated_on(String updated_on) {
        this.updated_on = updated_on;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<UserPrivilege> getPrivileges() {
        return privileges;
    }

    public void setPrivileges(List<UserPrivilege> privileges) {
        this.privileges = privileges;
    }
    
    
    
    public String toString() {
        return String.format("[%s %s - %s - %s - %s]", id, name, email, phone, status);
    }
}
