package net.citotech.cito.identity.provider.gnugrid;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.identity.provider.ValidationProviderException;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB enquiries client (ISO domain mapping: identity/provider/gnugrid). Executes
 * individual / non-individual credit enquiries, KYC enquiries, and credit/KYC report retrieval
 * against the configured enquiries endpoint ({@code cpay.identity.gnugrid.enquiries-path}).
 *
 * <p>Consent and enquiry-purpose validation happen in CPay's orchestrator before this client is
 * called; the client carries the approved purpose/reason into the provider payload. Full report
 * bodies and PDFs are never stored in ordinary requests tables — the client emits normalized
 * metadata plus a {@code protectedArtifactReference} (payload digest) so sensitive evidence is
 * retained under an explicit protected-evidence policy.
 */
@Component
public class GnuGridEnquiriesClient {

    /** Deterministic sandbox fixture: identifiers ending {@code 00011} produce no report. */
    static final String SANDBOX_FAIL_SUFFIX = "00011";

    private final GnuGridProperties properties;
    private final GnuGridTokenManager tokenManager;

    public GnuGridEnquiriesClient(
            GnuGridProperties properties, GnuGridTokenManager tokenManager) {
        this.properties = properties;
        this.tokenManager = tokenManager;
    }

    /**
     * Creates or retrieves an enquiry/report. For {@code operation=RETRIEVE} the enquiry reference
     * (from a prior pending response) is resolved against the provider.
     */
    public ProviderEnquiryResult execute(
            String reference, String operation, Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "SUBJECT_DATA_INVALID",
                    "gnuGrid enquiry requires subject attributes");
        }
        String baseUrl = properties.baseUrl();
        if (baseUrl.isEmpty()) {
            return sandbox(reference, operation, attributes);
        }
        boolean retrieve = "RETRIEVE".equalsIgnoreCase(operation);
        String path = properties.enquiriesPath();
        if (retrieve) {
            String enquiryReference = attributes.getOrDefault("enquiryReference", "");
            if (enquiryReference.isBlank()) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "SUBJECT_DATA_INVALID",
                        "gnuGrid report retrieval requires an enquiry reference");
            }
            path = path.replaceAll("/+$", "") + "/" + enquiryReference;
        }
        String url = baseUrl.replaceAll("/+$", "") + path;
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + tokenManager.accessToken());
        headers.put("X-CPay-Reference", reference);
        try {
            HttpRequestResponse http =
                    Common.doHttpRequest(
                            retrieve ? "GET" : "POST", url,
                            retrieve ? null : requestBody(reference, attributes), headers);
            int status = http.getStatusCode();
            String body = http.getResponse() == null ? "" : http.getResponse();
            if (status >= 200 && status < 300 && !body.isBlank()) {
                return normalize(reference, body, retrieve);
            }
            if (status == 401) {
                throw new ValidationProviderException(
                        GnuGridTokenManager.PROVIDER_CODE,
                        "PROVIDER_AUTHENTICATION_ERROR",
                        "gnuGrid enquiry rejected the access token (HTTP 401)");
            }
            throw mapHttpFailure(status, body);
        } catch (ValidationProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_TEMPORARILY_UNAVAILABLE",
                    "gnuGrid enquiry request failed");
        }
    }

    private ProviderEnquiryResult normalize(
            String reference, String body, boolean retrieve) {
        try {
            JSONObject json = new JSONObject(body);
            String providerReference =
                    json.optString(
                            "enquiryReference",
                            json.optString("providerReference", ""));
            if (json.optString("status", "").equalsIgnoreCase("PENDING")) {
                return new ProviderEnquiryResult(
                        "PENDING",
                        "ENQUIRY_PENDING",
                        providerReference,
                        Map.of("enquiryReference", providerReference),
                        null);
            }
            if (providerReference.isEmpty()) {
                providerReference = "ref-" + reference;
            }
            String state = json.optString("state", json.optString("status", "COMPLETED"));
            if (state.equalsIgnoreCase("NO_MATCH")
                    || state.equalsIgnoreCase("NOT_FOUND")) {
                return new ProviderEnquiryResult(
                        "COMPLETED",
                        "CREDIT_REPORT_NOT_FOUND",
                        providerReference,
                        Map.of(),
                        null);
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            putIfPresent(attributes, "reportReference", providerReference);
            putIfPresent(attributes, "creditScore", json.optString("creditScore", ""));
            putIfPresent(attributes, "creditLimit", json.optString("creditLimit", ""));
            putIfPresent(attributes, "reportDate", json.optString("reportDate", ""));
            putIfPresent(attributes, "format", json.optString("format", "STRUCTURED"));
            String digest = digestOf(body);
            return new ProviderEnquiryResult(
                    "COMPLETED",
                    "CREDIT_REPORT_AVAILABLE",
                    providerReference,
                    attributes,
                    digest);
        } catch (JSONException e) {
            throw new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    "PROVIDER_INCONCLUSIVE",
                    "gnuGrid enquiry response was unreadable");
        }
    }

    private ProviderEnquiryResult sandbox(
            String reference, String operation, Map<String, String> attributes) {
        String id = attributes.getOrDefault("nin", attributes.getOrDefault("registrationNumber", ""));
        String providerReference = "sandbox-" + reference;
        if (id.endsWith(SANDBOX_FAIL_SUFFIX)) {
            return new ProviderEnquiryResult(
                    "COMPLETED", "SANDBOX_REPORT_NOT_FOUND", providerReference, Map.of(), null);
        }
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("reportReference", providerReference);
        meta.put("format", "STRUCTURED");
        putIfPresent(meta, "creditScore", attributes.getOrDefault("sandboxScore", "720"));
        return new ProviderEnquiryResult(
                "COMPLETED",
                "SANDBOX_REPORT_AVAILABLE",
                providerReference,
                meta,
                digestOf("sandbox:" + reference));
    }

    private ValidationProviderException mapHttpFailure(int status, String body) {
        String subscriptionCode = subscriptionCodeFromBody(body);
        if (subscriptionCode != null) {
            return new ValidationProviderException(
                    GnuGridTokenManager.PROVIDER_CODE,
                    subscriptionCode,
                    "gnuGrid enquiry rejected by provider subscription state");
        }
        return new ValidationProviderException(
                GnuGridTokenManager.PROVIDER_CODE,
                status == 0 || status >= 500
                        ? "PROVIDER_TEMPORARILY_UNAVAILABLE"
                        : "VALIDATION_REQUEST_INVALID",
                "gnuGrid enquiry failed (HTTP " + status + ")");
    }

    private String subscriptionCodeFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            String state = new JSONObject(body)
                    .optString("state", new JSONObject(body).optString("status", ""))
                    .trim()
                    .toUpperCase(Locale.ROOT);
            return switch (state) {
                case "INSUFFICIENT", "NONE" -> "PROVIDER_BILLING_UNAVAILABLE";
                case "EXPIRED" -> "PROVIDER_SUBSCRIPTION_EXPIRED";
                case "CANCELLED" -> "PROVIDER_SUBSCRIPTION_CANCELLED";
                case "AUTHENTICATION_PROBLEM" -> "PROVIDER_AUTHENTICATION_ERROR";
                case "CONNECTION_PROBLEM", "UNEXPECTED_ERROR", "ERROR", "EMPTY_RESPONSE" ->
                        "PROVIDER_TEMPORARILY_UNAVAILABLE";
                default -> null;
            };
        } catch (JSONException e) {
            return null;
        }
    }

    private String requestBody(String reference, Map<String, String> attributes) {
        StringBuilder body = new StringBuilder();
        body.append("{\"reference\":\"")
                .append(esc(reference))
                .append("\",\"subjectType\":\"")
                .append(esc(attributes.getOrDefault("subjectType", "INDIVIDUAL")))
                .append("\",\"identifierType\":\"")
                .append(esc(attributes.getOrDefault("identifierType", "NATIONAL_ID")))
                .append("\",\"identifierValue\":\"")
                .append(esc(attributes.getOrDefault("identifierValue", "")))
                .append("\",\"purpose\":\"")
                .append(esc(attributes.getOrDefault("purpose", "")))
                .append("\",\"consentReference\":\"")
                .append(esc(attributes.getOrDefault("consentReference", "")))
                .append("\",\"reportFormat\":\"")
                .append(esc(attributes.getOrDefault("reportFormat", "STRUCTURED")))
                .append("\"}");
        return body.toString();
    }

    private String digestOf(String payload) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String esc(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    /**
     * Normalized enquiry/report outcome. {@code protectedArtifactReference} is a payload digest /
     * protected-evidence reference — never the raw report body.
     */
    public record ProviderEnquiryResult(
            String status,
            String normalizedCode,
            String providerReference,
            Map<String, String> attributes,
            String protectedArtifactReference) {}
}
