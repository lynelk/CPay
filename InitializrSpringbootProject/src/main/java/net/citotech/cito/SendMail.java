/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.Properties;
import net.citotech.cito.Model.Setting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
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
    
    public void sendSimpleMessage(String to, String subject, String text) {
        JavaMailSenderImpl mailSender = buildMailSender();
        String from = Setting.getGeneralSettingByKey("email_from_address") != null
                ? Setting.getGeneralSettingByKey("email_from_address") : "";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    @Bean
    public JavaMailSender getJavaMailSender() {
        return buildMailSender();
    }

    private JavaMailSenderImpl buildMailSender() {
        String host = Setting.getGeneralSettingByKey("email_smpt_host") != null
                ? Setting.getGeneralSettingByKey("email_smpt_host") : "";
        int port = Integer.parseInt(
                Setting.getGeneralSettingByKey("email_smpt_port") != null
                ? Setting.getGeneralSettingByKey("email_smpt_port") : "0");
        String username = Setting.getGeneralSettingByKey("email_smpt_username") != null
                ? Setting.getGeneralSettingByKey("email_smpt_username") : "";
        String password = Setting.getGeneralSettingByKey("email_smpt_password") != null
                ? Setting.getGeneralSettingByKey("email_smpt_password") : "";

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", true);
        props.put("mail.smtp.starttls.enable", true);
        props.put("mail.debug", "false");

        return mailSender;
    }
    
}

