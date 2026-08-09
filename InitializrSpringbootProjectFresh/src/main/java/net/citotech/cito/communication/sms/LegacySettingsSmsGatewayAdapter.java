package net.citotech.cito.communication.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The pre-provider-router adapter (ISO domain mapping: communication/sms). This is a faithful port
 * of the legacy settings-driven HTTP gateway logic that used to live inline in {@code
 * TransactionsLogController.testSendPendingSmsCron()} and {@link SmsGateway}: it reads {@code
 * sms_api_url}, {@code sms_api_parameters}, {@code sms_api_http_method} and {@code
 * sms_gateway_name} from the settings table and sends one HTTP request (or one per phone for
 * gateways whose URL contains "speedamobile") with {CONTENT}/{MSISDNS} placeholders substituted.
 *
 * <p>It is selected via {@code CommunicationSmsConfig} so B1B provider adapters (Yo! SMS, Africa's
 * Talking, Twilio) can replace this without touching the delivery worker. The returned {@link
 * SmsSendResult} normalizes the raw gateway response into the P5 SMS status lifecycle: transport
 * failure (no response / status 0) and non-2xx provider rejections are both refundable failures,
 * matching the legacy 2xx-only acceptance check.
 */
@Component
public class LegacySettingsSmsGatewayAdapter implements SmsGatewayAdapter {

    private static final Logger logger =
            Logger.getLogger(LegacySettingsSmsGatewayAdapter.class.getName());

    private static final String SPEEDAMOBILE_MARKER = "speedamobile";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LegacySettingsSmsGatewayAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SmsSendResult send(SmsSendRequest request) {
        String url = settingValue("sms_api_url");
        String params = settingValue("sms_api_parameters");
        String method = settingValue("sms_api_http_method");
        if (blank(url) || blank(params) || blank(method)) {
            return SmsSendResult.failed(
                    "sms_api_url/sms_api_parameters/sms_api_http_method not configured", "");
        }

        if (url.contains(SPEEDAMOBILE_MARKER)) {
            return sendSpeedamobile(request, url, params, method);
        }
        return sendBatched(request, url, params, method);
    }

    /** One request carrying all recipients — the default gateway shape. */
    private SmsSendResult sendBatched(
            SmsSendRequest request, String url, String params, String method) {
        String cleaned = stripTrailingComma(request.recipients());
        String payload = params.replace("{CONTENT}", Common.urlEncodeValue(request.content()));
        payload = payload.replace("{MSISDNS}", cleaned);
        Map<String, String> headers = new HashMap<>();
        if ("POST".equalsIgnoreCase(method)) {
            headers.put("Content-Type", "application/x-www-form-urlencoded");
        }
        HttpRequestResponse response =
                Common.doHttpRequest(
                        method,
                        "POST".equalsIgnoreCase(method) ? url : url + "?" + payload,
                        "POST".equalsIgnoreCase(method) ? payload : "",
                        headers);
        return normalize(response);
    }

    /** One HTTP request per recipient, matching the legacy speedamobile fan-out. */
    private SmsSendResult sendSpeedamobile(
            SmsSendRequest request, String url, String params, String method) {
        HttpRequestResponse lastResponse = null;
        String cleaned = stripTrailingComma(request.recipients());
        for (String phone : cleaned.split(",")) {
            String payload = params.replace("{CONTENT}", Common.urlEncodeValue(request.content()));
            payload = payload.replace("{MSISDNS}", phone);
            Map<String, String> headers = new HashMap<>();
            if ("POST".equalsIgnoreCase(method)) {
                headers.put("Content-Type", "application/x-www-form-urlencoded");
            }
            lastResponse =
                    Common.doHttpRequest(
                            method,
                            "POST".equalsIgnoreCase(method) ? url : url + "?" + payload,
                            "POST".equalsIgnoreCase(method) ? payload : "",
                            headers);
        }
        return normalize(lastResponse);
    }

    /** Audit P5: only a 2xx is a successful send; anything else refunds. */
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
            net.citotech.cito.Model.Setting setting = Common.getSettings(name, jdbcTemplate);
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
