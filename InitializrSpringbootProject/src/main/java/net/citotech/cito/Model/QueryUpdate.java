/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito.Model;

import java.math.BigInteger;

/**
 *
 * @author josephtabajjwa
 */
public class QueryUpdate {
    BigInteger id;
    String query_id;
    String created_on;
    String sql;
    String rollback;

    public BigInteger getId() {
        return id;
    }

    public void setId(BigInteger id) {
        this.id = id;
    }

    public String getQuery_id() {
        return query_id;
    }

    public void setQuery_id(String query_id) {
        this.query_id = query_id;
    }

    public String getCreated_on() {
        return created_on;
    }

    public void setCreated_on(String created_on) {
        this.created_on = created_on;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getRollback() {
        return rollback;
    }

    public void setRollback(String rollback) {
        this.rollback = rollback;
    }
    
}
