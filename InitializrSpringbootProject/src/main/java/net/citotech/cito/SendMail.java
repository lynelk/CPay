/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import net.citotech.cito.Model.Setting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 *
 * @author josephtabajjwa
 */
@Component
public class SendMail implements EmailService {
    @Autowired
    public JavaMailSender emailSender;
    
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    
    public void sendSimpleMessage(
      String to, String subject, String text) {
        emailSender = getJavaMailSender();
        String from = Setting.getGeneralSettingByKey("email_from_address") != null ? 
                Setting.getGeneralSettingByKey("email_from_address")
                : "";
        
        SimpleMailMessage message = new SimpleMailMessage(); 
        message.setFrom(from);
        message.setTo(to); 
        message.setSubject(subject); 
        message.setText(text);
        emailSender.send(message);
    }
    
    @Bean
    public JavaMailSender getJavaMailSender() {
        String host = Setting.getGeneralSettingByKey("email_smpt_host") != null ? 
                Setting.getGeneralSettingByKey("email_smpt_host")
                : "";
        int port = Integer.parseInt(
                Setting.getGeneralSettingByKey("email_smpt_port") != null ? 
                Setting.getGeneralSettingByKey("email_smpt_port")
                : "0"
        );
        String username = Setting.getGeneralSettingByKey("email_smpt_username") != null ? 
                Setting.getGeneralSettingByKey("email_smpt_username")
                : "";
        String password = Setting.getGeneralSettingByKey("email_smpt_password") != null ? 
                Setting.getGeneralSettingByKey("email_smpt_password")
                : "";
        
        /*Logger.getLogger(AuthenticationController.class.getName()).log(Level.SEVERE, 
                "SMTP_DETAILS: "+host+" - "+port, "");*/
        
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        mailSender.setHost(host);
        mailSender.setPort(port);
        

        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", true);
        props.put("mail.debug", "true");
        
        Session session = Session.getDefaultInstance(props);
        MimeMessage msg = new MimeMessage(session);
       
        
        return mailSender;
    }
    
}

