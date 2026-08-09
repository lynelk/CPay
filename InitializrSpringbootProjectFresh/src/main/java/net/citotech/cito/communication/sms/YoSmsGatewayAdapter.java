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
 * Yo! SMS provider adapter (ISO domain mapping: communication/sms, track B1b). Sends via the Yo!
 * Payments HTTP SMS API ({@code GET/POST} to {@code yo_sms_api_url}, default {@code
 * https://sms.yo.co.ug/yosms/api/v2/send}) with the standard form parameters {@code origin}, {@code
 * destinations} (comma-separated), {@code message}, {@code username}, {@code password} and {@code
 * sender_id}.
 *
 * <p>Credentials come from the settings table ({@code yo_sms_username}, {@code yo_sms_password},
 * {@code yo_sms_sender_id}) so no secret is hard-coded or committed. A missing credential returns a
 * refundable failure (FAILED) rather than crashing the batch. The response is normalized
 * identically to the legacy adapter: only a 2xx counts as SENT; anything else is REJECTED and get a
 * charge reversal, matching audit P5.
 */
@Component
public class YoSmsGatewayAdapter implements SmsGatewayAdapter {

    private static final Logger logger = Logger.getLogger(YoSmsGatewayAdapter.class.getName());

    private static final String DEFAULT_API_URL = "https://sms.yo.co.ug/yosms/api/v2/send";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public YoSmsGatewayAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        String username = settingValue("yo_sms_username");
        String password = settingValue("yo_sms_password");
        String senderId = settingValue("yo_sms_sender_id");
        String apiUrl = settingValue("yo_sms_api_url");
        if (blank(username) || blank(password)) {
            return SmsSendResult.failed("yo_sms_username/yo_sms_password not configured", "");
        }
        if (blank(apiUrl)) {
            apiUrl = DEFAULT_API_URL;
        }

        // One batched request carrying all recipients, mirroring the legacy non-speedamobile path.
        String payload =
                "origin="
                        + Common.urlEncodeValue(senderId)
                        + "&destinations="
                        + Common.urlEncodeValue(stripTrailingComma(request.recipients()))
                        + "&message="
                        + Common.urlEncodeValue(request.content())
                        + "&username="
                        + Common.urlEncodeValue(username)
                        + "&password="
                        + Common.urlEncodeValue(password);
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");

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
