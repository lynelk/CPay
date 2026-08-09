package net.citotech.cito.communication.sms;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.Model.Setting;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Twilio SMS provider adapter (ISO domain mapping: communication/sms, track B1b). Sends via the
 * Twilio Messages API ({@code twilio_sms_api_url}, default {@code
 * https://api.twilio.com/2010-04-01/Accounts/{ACCOUNT_SID}/Messages.json}) using {@code Basic} auth
 * (Account SID + Auth Token) and the standard form fields {@code From}, {@code To}, {@code Body}.
 *
 * <p>Credentials come from the settings table ({@code twilio_account_sid}, {@code
 * twilio_auth_token}, {@code twilio_from_number}) so no secret is hard-coded or committed. Missing
 * credentials return a refundable FAILED rather than crashing the batch. Responses are normalized
 * identically to the legacy adapter: only a 2xx counts as SENT; anything else is REJECTED and
 * triggers the charge reversal (audit P5). Twilio 2xx responses carry message status (queued/sent)
 * and the trace is stored for audit.
 */
@Component
public class TwilioSmsGatewayAdapter implements SmsGatewayAdapter {

    private static final Logger logger = Logger.getLogger(TwilioSmsGatewayAdapter.class.getName());

    private static final String TWILIO_API_TEMPLATE =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TwilioSmsGatewayAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        String accountSid = settingValue("twilio_account_sid");
        String authToken = settingValue("twilio_auth_token");
        String fromNumber = settingValue("twilio_from_number");
        if (blank(accountSid) || blank(authToken) || blank(fromNumber)) {
            return SmsSendResult.failed(
                    "twilio_account_sid/twilio_auth_token/twilio_from_number not configured", "");
        }

        String apiUrl = settingValue("twilio_sms_api_url");
        if (blank(apiUrl)) {
            apiUrl = String.format(TWILIO_API_TEMPLATE, accountSid);
        }

        // Twilio is a per-recipient API: one logical SMS with N recipients becomes N Messages
        // requests (each a 2xx on success). Like the legacy speedamobile fan-out, the returned
        // result reflects the last request that was made.
        HttpRequestResponse lastResponse = null;
        for (String recipient : splitRecipients(stripTrailingComma(request.recipients()))) {
            String payload =
                    "From="
                            + Common.urlEncodeValue(fromNumber)
                            + "&To="
                            + Common.urlEncodeValue(recipient)
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

            lastResponse = Common.doHttpRequest("POST", apiUrl, payload, headers);
        }
        return normalize(lastResponse);
    }

    /** Audit P5: only a 2xx is a successful send; anything else gets reversed. */
    private SmsSendResult normalize(HttpRequestResponse response) {
        if (response == null || response.getStatusCode() == 0) {
            return SmsSendResult.failed(
                    response == null ? "No gateway response" : response.toString(),
                    response == null ? "" : response.getResponse());
        }
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            return SmsSendResult.sent(response.toString(), response.getResponse());
        }
        return SmsSendResult.rejected(response.toString(), response.getResponse());
    }

    private String[] splitRecipients(String recipients) {
        if (recipients == null || recipients.isBlank()) {
            return new String[] {""};
        }
        return recipients.split(",");
    }

    private String settingValue(String name) {
        try {
            Setting setting = Common.getSettings(name, jdbcTemplate);
            return setting == null ? "" : setting.getSetting_value();
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Failed to read SMS setting " + name, ex);
            return "";
        }
    }

    private String stripTrailingComma(String value) {
        return value == null ? "" : value.replaceAll("[,]$", "");
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
