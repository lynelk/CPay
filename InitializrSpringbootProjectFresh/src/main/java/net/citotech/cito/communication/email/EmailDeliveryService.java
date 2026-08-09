package net.citotech.cito.communication.email;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * SMTP delivery for the communication/email domain (ISO domain mapping, track B2).
 *
 * <p>Replaces the ad-hoc {@code JavaMailSenderImpl} construction in legacy {@code SendMail} with an
 * explicit service that reads the same {@code mail.smtp.*} settings-table keys, so existing
 * deployments keep working without new configuration. Unlike the legacy path (which swallows {@code
 * MailException} with {@code printStackTrace()}), every failure is returned as a FAILED, refundable
 * {@link EmailSendResult} so callers and the billing meter (B5b) can see it.
 */
@Service
public class EmailDeliveryService {

    private static final Logger logger = Logger.getLogger(EmailDeliveryService.class.getName());

    private static final int DEFAULT_PORT = 25;
    private static final String DEFAULT_FROM = "noreply@localhost";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EmailDeliveryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Sends one plain-text email, returning a typed result. A blank recipient or body, or missing
     * SMTP host settings, yields a refundable FAILED without attempting a network call.
     */
    public EmailSendResult send(EmailSendRequest request) {
        if (request == null || blank(request.to()) || blank(request.body())) {
            return EmailSendResult.failed("to/body must not be blank", "");
        }

        String host = settingValue("mail.smtp.host");
        if (blank(host)) {
            return EmailSendResult.failed("mail.smtp.host not configured", "");
        }
        int port = parsePort(settingValue("mail.smtp.port"));
        String username = settingValue("mail.smtp.username");
        String password = settingValue("mail.smtp.password");
        String from = resolveFrom(username);

        JavaMailSenderImpl mailSender = buildMailSender(host, port, username, password);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());

        try {
            mailSender.send(message);
            return EmailSendResult.sent(host + ":" + port, "");
        } catch (Exception ex) { // MailException + unchecked (e.g. IllegalArgumentException)
            logger.log(Level.WARNING, "Email send failed", ex);
            return EmailSendResult.failed(host + ":" + port + " " + ex.getMessage(), "");
        }
    }

    private JavaMailSenderImpl buildMailSender(
            String host, int port, String username, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", settingValue("mail.smtp.auth"));
        props.put("mail.smtp.starttls.enable", settingValue("mail.smtp.starttls.enable"));
        props.put("mail.debug", "false");
        return mailSender;
    }

    private String settingValue(String name) {
        try {
            Setting setting = Common.getSettings(name, jdbcTemplate);
            return setting == null ? "" : setting.getSetting_value();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to read mail setting " + name, ex);
            return "";
        }
    }

    private String resolveFrom(String username) {
        String from = settingValue("mail.smtp.from");
        if (!blank(from)) {
            return from;
        }
        return blank(username) ? DEFAULT_FROM : username;
    }

    private int parsePort(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ex) {
            return DEFAULT_PORT;
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
