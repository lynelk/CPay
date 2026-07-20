/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import java.util.Properties;
import net.citotech.cito.Model.Setting;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 *
 * @author josephtabajjwa
 */
@Component
public class SendMail implements EmailService {
    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;
    
    public void sendSimpleMessage(String to, String subject, String text) {
        JavaMailSenderImpl mailSender = buildMailSender();
        String from = Setting.getGeneralSettingByKey("mail.smtp.from") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.from") : "";
        String username = Setting.getGeneralSettingByKey("mail.smtp.username") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.username") : "";

        if (from == null || from.trim().isEmpty()) {
            from = username;
        }
        if (from == null || from.trim().isEmpty()) {
            from = "noreply@localhost";
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private JavaMailSenderImpl buildMailSender() {
        String host = Setting.getGeneralSettingByKey("mail.smtp.host") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.host") : "localhost";
        int port = Integer.parseInt(
                Setting.getGeneralSettingByKey("mail.smtp.port") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.port") : "25");
        String username = Setting.getGeneralSettingByKey("mail.smtp.username") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.username") : "";
        String password = Setting.getGeneralSettingByKey("mail.smtp.password") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.password") : "";
        boolean auth = Boolean.parseBoolean(
                Setting.getGeneralSettingByKey("mail.smtp.auth") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.auth") : "false");
        boolean starttls = Boolean.parseBoolean(
                Setting.getGeneralSettingByKey("mail.smtp.starttls.enable") != null
                ? Setting.getGeneralSettingByKey("mail.smtp.starttls.enable") : "false");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", starttls);
        props.put("mail.debug", "false");

        return mailSender;
    }
    
}


