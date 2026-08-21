package net.citotech.cito.identity.provider.gnugrid;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB credit-score client (ISO domain mapping: identity/provider/gnugrid). Requests
 * scores from the configured credit-score endpoint
 * ({@code cpay.identity.gnugrid.credit-score-path}; default
 * {@code /v1/credit-enquiries/credit-scores}). Supports the documented score families — CRB, MNO,
 * SACCO, and approved combined types. Conditional requirements (e.g. an MNO score needs a phone
 * number, a CRB score needs an approved identifier) are validated before the request. The client
 * returns {@link ProviderScoreResult} evidence only; it never derives a loan or CPay risk band.
 */
@Component
public class GnuGridCreditScoreClient {

    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private static final Map<String, Set<String>> REQUIRED_ATTRIBUTES =
            Map.of(
                    "CRB", Set.of("identifierValue"),
                    "MNO", Set.of("msisdn"),
                    "SACCO", Set.of("identifierValue"),
                    "COMBINED", Set.of("identifierValue", "msisdn"));

    private final GnuGridProperties properties;
    private final GnuGridTokenManager tokenManager;

    public GnuGridCreditScoreClient(
            GnuGridProperties properties, GnuGridTokenManager tokenManager) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    /**
     * Requests a credit score. {@code attributes} should carry {@code scoreType} (CRB/MNO/SACCO/
     * COMBINED), {@code identifierType}/{@code identifierValue} where required, {@code msisdn}
     * where required, and {@code consentReference}/{@code purpose} for the protected enquiry.
     */
    public ProviderScoreResult score(String reference, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "SUBJECT_DATA_INVALID",
                    "gnuGrid credit score requires subject attributes");
        }
        String scoreType = attributes.getOrDefault("scoreType", "CRB").trim().toUpperCase();
        if (!REQUIRED_ATTRIBUTES.containsKey(scoreType)) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "VALIDATION_REQUEST_INVALID",
                    "unsupported credit score type: " + scoreType);
        }
        for (String required : REQUIRED_ATTRIBUTES.get(scoreType)) {
            if (isBlank(attributes.get(required))) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "SUBJECT_DATA_INVALID",
                        "gnuGrid credit score type " + scoreType
                                + " requires attribute: " + required);
            }
        }
        String baseUrl = properties.baseUrl();
        if (baseUrl.isEmpty()) {
            return sandbox(reference, scoreType, attributes);
        }
        String url = baseUrl.replaceAll("/+$", "") + properties.creditScorePath();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + tokenManager.accessToken());
        headers.put("X-CPay-Reference", reference);
        try {
            HttpRequestResponse http =
                    Common.doHttpRequest(
                            "POST", url, requestBody(reference, scoreType, attributes), headers);
            int status = http.getStatusCode();
            String body = http.getResponse() == null ? "" : http.getResponse();
            if (status >= 200 && status < 300 && !body.isBlank()) {
                return normalize(reference, scoreType, body);
            }
            if (status == 401) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "PROVIDER_AUTHENTICATION_ERROR",
                        "gnuGrid credit score rejected the access token (HTTP 401)");
            }
            throw mapHttpFailure(status, body);
        } catch (ValidationProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "gnuGrid credit score request failed");
        }
    }

    private ProviderScoreResult normalize(String reference, String scoreType, String body) {
        try {
            JSONObject json = new JSONObject(body);
            if (json.optString("state", "").equalsIgnoreCase("NO_MATCH")
                    || json.optString("state", "").equalsIgnoreCase("NOT_FOUND")) {
                return new ProviderScoreResult(
                        "SCORE_NOT_FOUND",
                        scoreType,
                        null,
                        null,
                        json.optString("providerReference", "ref-" + reference),
                        null);
            }
            String providerReference =
                    json.optString(
                            "scoreReference",
                            json.optString("providerReference", "ref-" + reference));
            String score = json.optString("score", null);
            if (score == null || score.isBlank()) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "PROVIDER_INCONCLUSIVE",
                        "gnuGrid credit score response did not contain a score");
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            putIfPresent(attributes, "scoreType", scoreType);
            putIfPresent(attributes, "score", score);
            putIfPresent(attributes, "providerBand", json.optString("band", json.optString("scoreBand", "")));
            putIfPresent(attributes, "calculatedAt", json.optString("calculatedAt", ""));
            return new ProviderScoreResult(
                    "SCORE_AVAILABLE", scoreType, score, json.optString("band", null),
                    providerReference, attributes);
        } catch (JSONException e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_INCONCLUSIVE",
                    "gnuGrid credit score response was unreadable");
        }
    }

    private ProviderScoreResult sandbox(
            String reference, String scoreType, Map<String, String> attributes) {
        String failKey = "CRB".equals(scoreType) ? "identifierValue" : "msisdn";
        String failValue = attributes.getOrDefault(failKey, "");
        String providerReference = "sandbox-" + reference;
        if (failValue.endsWith(SANDBOX_FAIL_SUFFIX)) {
            return new ProviderScoreResult(
                    "SCORE_NOT_FOUND", scoreType, null, null, providerReference, null);
        }
        String score = "CRB".equals(scoreType) ? "680" : "730";
        return new ProviderScoreResult(
                "SCORE_AVAILABLE", scoreType, score, "SANDBOX_BAND", providerReference,
                Map.of("scoreType", scoreType, "score", score));
    }

    private ValidationProviderException mapHttpFailure(int status, String body) {
        if (body != null && !body.isBlank()) {
            String state = "";
            try {
                state = new JSONObject(body).optString("state", "").toUpperCase();
            } catch (JSONException ignored) {
                // fall through
            }
            switch (state) {
                case "INSUFFICIENT", "NONE" -> {
                    return new ValidationProviderException(
                            GnuGridTokenManager.PROVIDER_CODE,
                            "PROVIDER_BILLING_UNAVAILABLE",
                            "gnuGrid credit score rejected by provider subscription state");
                }
                case "EXPIRED" -> {
                    return new ValidationProviderException(
                            GnuGridTokenManager.PROVIDER_CODE,
                            "PROVIDER_SUBSCRIPTION_EXPIRED",
                            "gnuGrid credit score subscription expired");
                }
                case "CANCELLED" -> {
                    return new ValidationProviderException(
                            GnuGridTokenManager.PROVIDER_CODE,
                            "PROVIDER_SUBSCRIPTION_CANCELLED",
                            "gnuGrid credit score subscription cancelled");
                }
                case "AUTHENTICATION_PROBLEM" -> {
                    return new ValidationProviderException(
                            GnuGridTokenManager.PROVIDER_CODE,
                            "PROVIDER_AUTHENTICATION_ERROR",
                            "gnuGrid credit score authentication problem");
                }
                default -> {
                    // handled below
                }
            }
        }
        return new ValidationProviderException(
                GnuGridTokenManager.PROVIDER_CODE,
                status == 0 || status >= 500
                        ? "PROVIDER_TEMPORARILY_UNAVAILABLE"
                        : "VALIDATION_REQUEST_INVALID",
                "gnuGrid credit score failed (HTTP " + status + ")");
    }

    private String requestBody(
            String reference, String scoreType, Map<String, String> attributes) {
        StringBuilder body = new StringBuilder();
        body.append("{\"reference\":\"")
                .append(esc(reference))
                .append("\",\"scoreType\":\"")
                .append(esc(scoreType))
                .append("\",\"identifierType\":\"")
                .append(esc(attributes.getOrDefault("identifierType", "NATIONAL_ID")))
                .append("\",\"identifierValue\":\"")
                .append(esc(attributes.getOrDefault("identifierValue", "")))
                .append("\",\"msisdn\":\"")
                .append(esc(attributes.getOrDefault("msisdn", "")))
                .append("\",\"purpose\":\"")
                .append(esc(attributes.getOrDefault("purpose", "")))
                .append("\",\"consentReference\":\"")
                .append(esc(attributes.getOrDefault("consentReference", "")))
                .append("\"}");
        return body.toString();
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    /**
     * Normalized score evidence with provenance (provider code, score type, provider band, and
     * contract version come from the adapter context). A score is evidence — a separate versioned
     * CPay risk/lending policy makes any business decision.
     */
    public record ProviderScoreResult(
            String status,
            String scoreType,
            String score,
            String providerBand,
            String providerReference,
            Map<String, String> attributes) {}
}
