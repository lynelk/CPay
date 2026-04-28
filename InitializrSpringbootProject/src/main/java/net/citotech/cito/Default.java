/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.citotech.cito.Model.QueryUpdate;
import net.citotech.cito.Model.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 *
 * @author josephtabajjwa
 */
@RestController 
public class Default {
    
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    
    @Autowired
    TransactionTemplate transactionTemplate;
    
    @Autowired
    private PlatformTransactionManager transactionManager;
    
    /*@GetMapping(path="/portal")

    public String index () {
        return "index.html";
    }*/
    
    /*@RequestMapping(value = "/portal", method = RequestMethod.GET)
    public String getPostByIdHtml( ) throws IOException {
        return "/resources/index.html";
    }*/
    @GetMapping("/dashboard")
    public RedirectView dashboard(
      RedirectAttributes attributes) {
        //attributes.addFlashAttribute("flashAttribute", "redirectWithRedirectView");
        //attributes.addAttribute("attribute", "redirectWithRedirectView");
        return new RedirectView("/");
    }
    
    //dashboardMerchant
    @GetMapping("/dashboardMerchant")
    public RedirectView dashboardMerchant(
      RedirectAttributes attributes) {
        return new RedirectView("/");
    }
    
    /*
    * Admin portal for back end 
    */
    @GetMapping("/portal")
    public RedirectView portal(
      RedirectAttributes attributes) {
        attributes.addAttribute("uiportal", "portal");
        return new RedirectView("/");   
    }
        
}
