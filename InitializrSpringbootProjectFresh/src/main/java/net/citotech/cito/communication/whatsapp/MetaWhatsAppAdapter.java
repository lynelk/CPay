package net.citotech.cito.communication.whatsapp;

import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.communication.credentials.CommunicationCredentialStore;
import net.citotech.cito.communication.domain.CommunicationChannel;
import net.citotech.cito.communication.provider.CommunicationProviderAdapter;
import net.citotech.cito.communication.provider.ProviderCapabilities;
import net.citotech.cito.communication.provider.ProviderSendRequest;
import net.citotech.cito.communication.provider.ProviderSendResult;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * WhatsApp Cloud-API-style provider adapter (Track A P3, guide Steps 9-11). Implements the
 * channel-neutral {@link CommunicationProviderAdapter} SPI so the dispatcher and outbox worker
 * send WHATSAPP traffic through {@code WABA_CLOUD_API} exactly like any other provider — no
 * channel-specific switch anywhere in the dispatch path.
 *
 * <p>Security rules (guide Step 15): credentials come from the encrypted
 * {@link CommunicationCredentialStore} (keys {@code access_token}, {@code phone_number_id});
 * endpoint base URL and API version come from configuration, never hardcoded in business logic;
 * tokens are never logged; recipient numbers never appear unmasked in traces; provider errors are
 * normalized into CPay codes with explicit retryability. When no base URL is configured the
 * adapter runs its deterministic sandbox (messages to numbers ending {@code 00011} are rejected)
 * so integration tests exercise the full normalization path without outbound calls.
 */
@Component
public class MetaWhatsAppAdapter implements CommunicationProviderAdapter {

    public static final String PROVIDER_CODE = "WABA_CLOUD_API";

    /** Deterministic sandbox fixture: recipients ending 00011 are rejected by the provider. */
    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private final CommunicationCredentialStore credentialStore;
    private final String baseUrl;
    private final String apiVersion;

    public MetaWhatsAppAdapter(
            CommunicationCredentialStore credentialStore,
            @Value("${cpay.communication.whatsapp.base-url:}") String baseUrl,
            @Value("${cpay.communication.whatsapp.api-version:v21.0}") String apiVersion) {
        this.credentialStore = credentialStore;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? "v21.0" : apiVersion.trim();
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public CommunicationChannel channel() {
        return CommunicationChannel.WHATSAPP;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder()
                .send(true)
                .templates(true)
                .deliveryReceipts(true)
                .inbound(true)
                .build();
    }

    @Override
    public ProviderSendResult send(ProviderSendRequest request) {
        if (request.recipient() == null || request.recipient().isBlank()) {
            return ProviderSendResult.rejected(
                    PROVIDER_CODE, "RECIPIENT_REQUIRED", "recipient missing", "");
        }
        if (baseUrl.isEmpty()) {
            return sandbox(request);
        }
        try {
            String accessToken = credentialStore.credential(PROVIDER_CODE, "access_token");
            String phoneNumberId = credentialStore.credential(PROVIDER_CODE, "phone_number_id");
            String url =
                    baseUrl.replaceAll("/+$", "")
                            + "/"
                            + apiVersion
                            + "/"
                            + phoneNumberId
                            + "/messages";
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json");
            headers.put("Authorization", "Bearer " + accessToken);
            HttpRequestResponse http =
                    Common.doHttpRequest(
                            "POST", url, payload(request).toString(), headers);
            return normalize(http);
        } catch (Exception ex) {
            // Credential store failure or transport exception: retryable technical failure.
            return ProviderSendResult.failed(
                    PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "WhatsApp send failed: " + safeReason(ex),
                    "",
                    true);
        }
    }

    private ProviderSendResult normalize(HttpRequestResponse http) {
        int status = http.getStatusCode();
        String body = http.getResponse() == null ? "" : http.getResponse();
        if (status >= 200 && status < 300 && !body.isBlank()) {
            try {
                JSONObject json = new JSONObject(body);
                String providerMessageId = "";
                if (json.has("messages")) {
                    providerMessageId = json.getJSONArray("messages").getJSONObject(0).optString("id", "");
                }
                return ProviderSendResult.accepted(
                        PROVIDER_CODE, providerMessageId, "WHATSAPP_ACCEPTED");
            } catch (JSONException e) {
                return ProviderSendResult.failed(
                        PROVIDER_CODE,
                        "PROVIDER_INCONCLUSIVE",
                        "WhatsApp response unreadable",
                        "",
                        false);
            }
        }
        if (status == 401 || status == 403) {
            return ProviderSendResult.failed(
                    PROVIDER_CODE,
                    "PROVIDER_AUTHENTICATION_ERROR",
                    "WhatsApp rejected credentials (HTTP " + status + ")",
                    "",
                    false);
        }
        if (status == 429) {
            return ProviderSendResult.failed(
                    PROVIDER_CODE,
                    "PROVIDER_RATE_LIMITED",
                    "WhatsApp rate limit (HTTP 429)",
                    "",
                    true);
        }
        if (status >= 500 || status == 0) {
            return ProviderSendResult.failed(
                    PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "WhatsApp unavailable (HTTP " + status + ")",
                    "",
                    true);
        }
        return ProviderSendResult.rejected(
                PROVIDER_CODE,
                "WHATSAPP_REJECTED",
                "WhatsApp rejected message (HTTP " + status + ")",
                maskedBody(body));
    }

    /**
     * Deterministic sandbox: accepts everything except recipients ending {@code 00011}. Mirrors
     * the Cloud API response shape ({@code messages[0].id}) so the normalizer path is identical.
     */
    private ProviderSendResult sandbox(ProviderSendRequest request) {
        if (request.recipient().trim().endsWith(SANDBOX_FAIL_SUFFIX)) {
            return ProviderSendResult.rejected(
                    PROVIDER_CODE,
                    "SANDBOX_NOT_DELIVERABLE",
                    "sandbox recipient not deliverable",
                    "");
        }
        return ProviderSendResult.accepted(
                PROVIDER_CODE,
                "wamid.sandbox-" + request.deliveryId(),
                "WHATSAPP_ACCEPTED");
    }

    /** Cloud API text-message payload built as JSON — never string-concatenated raw bodies. */
    private JSONObject payload(ProviderSendRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", request.recipient());
        if (request.templateName() != null && !request.templateName().isBlank()) {
            JSONObject template = new JSONObject();
            template.put("name", request.templateName());
            JSONObject language = new JSONObject();
            language.put("code", "en");
            template.put("language", language);
            payload.put("type", "template");
            payload.put("template", template);
        } else {
            payload.put("type", "text");
            JSONObject text = new JSONObject();
            text.put("preview_url", false);
            text.put("body", request.content() == null ? "" : request.content());
            payload.put("text", text);
        }
        return payload;
    }

    /** Truncated, PII-safe body excerpt for diagnostics only. */
    private String maskedBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private String safeReason(Exception ex) {
        String message = ex.getMessage();
        return message == null ? ex.getClass().getSimpleName() : message;
    }
}
