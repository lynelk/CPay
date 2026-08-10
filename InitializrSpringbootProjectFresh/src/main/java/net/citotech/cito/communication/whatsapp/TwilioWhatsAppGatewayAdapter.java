package net.citotech.cito.communication.whatsapp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.Model.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Twilio WhatsApp adapter. Uses the same Twilio account credentials as SMS but a dedicated
 * {@code twilio_whatsapp_from_number}; no secret is committed to code or routing metadata.
 */
@Component
public class TwilioWhatsAppGatewayAdapter implements WhatsAppGatewayAdapter {

    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppGatewayAdapter.class);
    private static final String API_TEMPLATE =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TwilioWhatsAppGatewayAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String providerCode() {
        return "TWILIO_WHATSAPP";
    }

    @Override
    public WhatsAppSendResult send(WhatsAppSendRequest request) {
        String accountSid = settingValue("twilio_account_sid");
        String authToken = settingValue("twilio_auth_token");
        String fromNumber = settingValue("twilio_whatsapp_from_number");
        if (blank(accountSid) || blank(authToken) || blank(fromNumber)) {
            return WhatsAppSendResult.failed(
                    "twilio_account_sid/twilio_auth_token/twilio_whatsapp_from_number not configured",
                    "");
        }

        String apiUrl = settingValue("twilio_whatsapp_api_url");
        if (blank(apiUrl)) {
            apiUrl = String.format(API_TEMPLATE, accountSid);
        }

        HttpRequestResponse last = null;
        for (String recipient : request.recipients().replaceAll("[,]$", "").split(",")) {
            String target = whatsappAddress(recipient);
            String source = whatsappAddress(fromNumber);
            String payload =
                    "From="
                            + Common.urlEncodeValue(source)
                            + "&To="
                            + Common.urlEncodeValue(target)
                            + "&Body="
                            + Common.urlEncodeValue(request.content());
            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", "application/x-www-form-urlencoded");
            String credentials =
                    Base64.getEncoder()
                            .encodeToString(
                                    (accountSid + ":" + authToken)
                                            .getBytes(StandardCharsets.UTF_8));
            headers.put("Authorization", "Basic " + credentials);
            last = Common.doHttpRequest("POST", apiUrl, payload, headers);
        }
        return normalize(last);
    }

    private WhatsAppSendResult normalize(HttpRequestResponse response) {
        if (response == null || response.getStatusCode() == 0) {
            return WhatsAppSendResult.failed(
                    response == null ? "No gateway response" : response.toString(),
                    response == null ? "" : response.getResponse());
        }
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            return WhatsAppSendResult.sent(response.toString(), response.getResponse());
        }
        return WhatsAppSendResult.rejected(response.toString(), response.getResponse());
    }

    private String whatsappAddress(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.startsWith("whatsapp:") ? normalized : "whatsapp:" + normalized;
    }

    private String settingValue(String name) {
        try {
            Setting setting = Common.getSettings(name, jdbcTemplate);
            return setting == null ? "" : setting.getSetting_value();
        } catch (Exception ex) {
            log.warn("Failed to read WhatsApp setting {}", name, ex);
            return "";
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
