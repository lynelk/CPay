/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

/**
 *
 * @author josephtabajjwa
 */
public class UserPrivilege implements java.io.Serializable{
    private Long id;
    private String privilege;
    private Long admin_id;
    private String created_on;
    private String udpated_on;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPrivilege() {
        return privilege;
    }

    public void setPrivilege(String privilege) {
        this.privilege = privilege;
    }

    public Long getAdmin_id() {
        return admin_id;
    }

    public void setAdmin_id(Long admin_id) {
        this.admin_id = admin_id;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public String getUdpated_on() {
        return udpated_on;
    }

    public void setUdpated_on(String udpated_on) {
        this.udpated_on = udpated_on;
    }
    
    
}
