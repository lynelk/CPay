package net.citotech.cito.identity.provider.gnugrid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * gnuGrid CRB configuration (ISO domain mapping: identity/provider/gnugrid). Mirrors the S5
 * pilot's {@code @Value} style. Secrets (client id/secret) are injected from the environment /
 * secret manager, never committed. Provider endpoint paths are configurable so the exact
 * approved gnuGrid specification paths can be supplied per environment; defaults keep the pilot
 * contract for sandbox compatibility.
 */
@Component
public class GnuGridProperties {

    private final String baseUrl;
    private final String environment;
    private final String oauthTokenPath;
    private final String idValidationPath;
    private final String phoneValidationPath;
    private final String enquiriesPath;
    private final String creditScorePath;
    private final long oauthExpirySkewSeconds;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    public GnuGridProperties(
            @Value("${cpay.identity.gnugrid.base-url:}") String baseUrl,
            @Value("${cpay.identity.gnugrid.environment:SANDBOX}") String environment,
            @Value("${cpay.identity.gnugrid.oauth.token-path:/v1/oauth/token}") String oauthTokenPath,
            @Value("${cpay.identity.gnugrid.id-validation-path:/v1/verifications}") String idValidationPath,
            @Value("${cpay.identity.gnugrid.phone-validation-path:/v1/verifications/phone}") String phoneValidationPath,
            @Value("${cpay.identity.gnugrid.enquiries-path:/v1/enquiries}") String enquiriesPath,
            @Value("${cpay.identity.gnugrid.credit-score-path:/v1/credit-enquiries/credit-scores}") String creditScorePath,
            @Value("${cpay.identity.gnugrid.oauth.expiry-skew-seconds:60}") long oauthExpirySkewSeconds,
            @Value("${cpay.identity.gnugrid.connect-timeout-seconds:2}") int connectTimeoutSeconds,
            @Value("${cpay.identity.gnugrid.read-timeout-seconds:10}") int readTimeoutSeconds) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.environment = environment == null ? "SANDBOX" : environment.trim().toUpperCase();
        this.oauthTokenPath = oauthTokenPath == null ? "" : oauthTokenPath.trim();
        this.idValidationPath = idValidationPath == null ? "" : idValidationPath.trim();
        this.phoneValidationPath = phoneValidationPath == null ? "" : phoneValidationPath.trim();
        this.enquiriesPath = enquiriesPath == null ? "" : enquiriesPath.trim();
        this.creditScorePath = creditScorePath == null ? "" : creditScorePath.trim();
        this.oauthExpirySkewSeconds = oauthExpirySkewSeconds;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String environment() {
        return environment;
    }

    public String oauthTokenPath() {
        return oauthTokenPath;
    }

    public String idValidationPath() {
        return idValidationPath;
    }

    public String phoneValidationPath() {
        return phoneValidationPath;
    }

    public String enquiriesPath() {
        return enquiriesPath;
    }

    public String creditScorePath() {
        return creditScorePath;
    }

    public long oauthExpirySkewSeconds() {
        return oauthExpirySkewSeconds;
    }

    public int connectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int readTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public boolean isProduction() {
        return "PRODUCTION".equalsIgnoreCase(environment);
    }
}
