package net.citotech.cito.communication.sms;

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
 * Africa's Talking SMS provider adapter (ISO domain mapping: communication/sms, track B1b). Sends
 * via the Africa's Talking SMS API ({@code africastalking_sms_api_url}, default {@code
 * https://api.africastalking.com/version1/messaging}) using the documented form fields {@code
 * username}, {@code to}, {@code message}, {@code from} (optional short code/alphanumeric sender),
 * {@code bulkSMSMode} ({@code 0} — one logical send with multiple recipients is still one message
 * to the API).
 *
 * <p>Credentials come from the settings table ({@code africastalking_username}, {@code
 * africastalking_api_key}) so no secret is hard-coded or committed; the API key travels in the
 * {@code apiKey} header as the provider expects. Missing credentials return a refundable FAILED
 * rather than crashing the batch. Responses are normalized identically to the legacy adapter: only
 * a 2xx counts as SENT; anything else is REJECTED and triggers the charge reversal (audit P5).
 * Success bodies with an Africa's Talking error code ({@code errorMessage} non-empty on a 2xx) are
 * still treated as rejected via the P5 2xx-only rule (the provider wraps failures in HTTP 200).
 */
@Component
public class AfricasTalkingSmsGatewayAdapter implements SmsGatewayAdapter {

    private static final Logger logger =
            Logger.getLogger(AfricasTalkingSmsGatewayAdapter.class.getName());

    private static final String DEFAULT_API_URL =
            "https://api.africastalking.com/version1/messaging";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AfricasTalkingSmsGatewayAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        String username = settingValue("africastalking_username");
        String apiKey = settingValue("africastalking_api_key");
        String senderId = settingValue("africastalking_sender_id");
        String apiUrl = settingValue("africastalking_sms_api_url");
        if (blank(username) || blank(apiKey)) {
            return SmsSendResult.failed(
                    "africastalking_username/africastalking_api_key not configured", "");
        }
        if (blank(apiUrl)) {
            apiUrl = DEFAULT_API_URL;
        }

        String payload =
                "username="
                        + Common.urlEncodeValue(username)
                        + "&to="
                        + Common.urlEncodeValue(stripTrailingComma(request.recipients()))
                        + "&message="
                        + Common.urlEncodeValue(request.content())
                        + "&from="
                        + Common.urlEncodeValue(senderId)
                        + "&bulkSMSMode=0";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        headers.put("apiKey", apiKey);

        HttpRequestResponse response = Common.doHttpRequest("POST", apiUrl, payload, headers);
        return normalize(response);
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
