package net.citotech.cito.identity.provider.gnugrid;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB OAuth client-credentials client (ISO domain mapping: identity/provider/gnugrid).
 * Requests an access token from {@code POST {baseUrl}/v1/oauth/token}. Uses the shared outbound
 * executor ({@link Common#doHttpRequest}) so timeouts, correlation, and metrics match every other
 * provider call. Client id/secret are injected from the environment / secret manager and are never
 * logged or embedded in exception messages.
 */
@Component
public class GnuGridOauthClient {

    private final GnuGridProperties properties;
    private final String clientId;
    private final String clientSecret;

    public GnuGridOauthClient(
            GnuGridProperties properties,
            @Value("${cpay.identity.gnugrid.oauth.client-id:}") String clientId,
            @Value("${cpay.identity.gnugrid.oauth.client-secret:}") String clientSecret) {
        this.properties = properties;
        this.clientId = clientId == null ? "" : clientId;
        this.clientSecret = clientSecret == null ? "" : clientSecret;
    }

    /**
     * POST /v1/oauth/token with {@code grant_type=client_credentials}. Returns the parsed token
     * fields; the caller (token manager) owns caching/lease/expiry-skew.
     */
    public GnuGridToken token() {
        String baseUrl = properties.baseUrl();
        if (baseUrl.isEmpty()) {
            throw new ValidationProviderException(
                    "GNUGRID", "PROVIDER_CONFIGURATION", "gnuGrid base-url is not configured");
        }
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            throw new ValidationProviderException(
                    "GNUGRID",
                    "PROVIDER_AUTHENTICATION_ERROR",
                    "gnuGrid OAuth client credentials are not configured");
        }
        String url = baseUrl.replaceAll("/+$", "") + properties.oauthTokenPath();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        try {
            HttpRequestResponse http =
                    Common.doHttpRequest("POST", url, encodeForm(form), headers);
            int status = http.getStatusCode();
            String body = http.getResponse() == null ? "" : http.getResponse();
            if (status >= 200 && status < 300 && !body.isBlank()) {
                JSONObject json = new JSONObject(body);
                String accessToken = json.optString("access_token", "");
                long expiresIn =
                        json.optLong("expires_in", 3600L);
                if (accessToken.isEmpty()) {
                    throw new ValidationProviderException(
                            "GNUGRID",
                            "PROVIDER_INCONCLUSIVE",
                            "gnuGrid OAuth response did not contain access_token");
                }
                String tokenType = json.optString("token_type", "Bearer");
                return new GnuGridToken(accessToken, tokenType, expiresIn);
            }
            throw new ValidationProviderException(
                    "GNUGRID",
                    status == 0
                            ? "PROVIDER_TEMPORARILY_UNAVAILABLE"
                            : "PROVIDER_AUTHENTICATION_ERROR",
                    "gnuGrid OAuth request failed (HTTP " + status + ")");
        } catch (ValidationProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationProviderException(
                    "GNUGRID",
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "gnuGrid OAuth request failed");
        }
    }

    private String encodeForm(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (!first) {
                body.append('&');
            }
            first = false;
            body.append(encode(entry.getKey()))
                    .append('=')
                    .append(encode(entry.getValue()));
        }
        return body.toString();
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 not supported", e);
        }
    }

    /** Immutable OAuth token response. */
    public record GnuGridToken(String accessToken, String tokenType, long expiresInSeconds) {}
}
