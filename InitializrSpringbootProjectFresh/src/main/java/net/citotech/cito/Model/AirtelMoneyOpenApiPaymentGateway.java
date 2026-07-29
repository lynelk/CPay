package net.citotech.cito.Model;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import net.citotech.cito.Common;
import net.citotech.cito.gateway.ProviderToken;
import net.citotech.cito.gateway.ProviderTokenStoreRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AirtelMoneyOpenApiPaymentGateway extends PaymentGateway {
    // Audit H1: converted from java.util.logging to SLF4J (money-path class: Airtel OpenAPI gateway).
    private static final Logger logger = LoggerFactory.getLogger(AirtelMoneyOpenApiPaymentGateway.class);
    private static final int TOKEN_TTL_MINUTES = 1;

    String xml_sent = "";
    String xml_returned = "";
    String mode = "TEST";
    String global_url = "https://openapiuat.airtel.africa";
    String env = "mtnuganda";
    String base_currency = "UGX";
    String country = "UG";
    String segment = "collection";
    String api_pin = "";
    String tokenPath = "/auth/oauth2/token";
    String collectionsPath = "/merchant/v2/payments/";
    String disbursementsPath = "/standard/v2/disbursements/";
    String balancePath = "/standard/v2/users/balance";
    String collectionStatusPath = "/standard/v2/payments/{reference}/";
    String disbursementStatusPath = "/standard/v2/disbursements/{reference}/";

    static public String BALANCE_TYPE = "airtelmm_balance";
    public static String[] prefix = {"25675", "25670", "25676"};
    public static String gateway_id = "AirtelMoneyOpenApiPaymentGateway";
    public static String gateway_currency_code = "AIRTELMM";

    String api_collections_user = "";
    String api_collections_key = "";
    String api_collections_subscription = "";
    String api_disbursements_user = "";
    String api_disbursements_key = "";
    String api_disbursements_subscription = "";
    
    String publicKey = "";

    public static boolean isValidMisdn(String msisdn) {
        Boolean fromRoutingTable = net.citotech.cito.gateway.ChannelRoutingRegistry.matchesConfiguredPrefix(gateway_id, msisdn);
        if (fromRoutingTable != null) {
            return fromRoutingTable;
        }
        for (String p : prefix) {
            Matcher matcher = Pattern.compile("^" + p).matcher(msisdn);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }


    public void setApiDetails(String global_url, String api_username, String api_password, String api_pin) {
        if (global_url != null && !global_url.trim().isEmpty()) {
            this.global_url = global_url.trim();
        }
        this.api_username = api_username;
        this.api_password = api_password;
        this.api_pin = api_pin;

    }

    public void setEndpointDetails(
            String tokenPath,
            String collectionsPath,
            String disbursementsPath,
            String balancePath,
            String collectionStatusPath,
            String disbursementStatusPath) {
        this.tokenPath = valueOrCurrent(tokenPath, this.tokenPath);
        this.collectionsPath = valueOrCurrent(collectionsPath, this.collectionsPath);
        this.disbursementsPath = valueOrCurrent(disbursementsPath, this.disbursementsPath);
        this.balancePath = valueOrCurrent(balancePath, this.balancePath);
        this.collectionStatusPath = valueOrCurrent(collectionStatusPath, this.collectionStatusPath);
        this.disbursementStatusPath = valueOrCurrent(disbursementStatusPath, this.disbursementStatusPath);
    }

    String tokenUrl() {
        return endpoint(tokenPath);
    }

    String collectionUrl() {
        return endpoint(collectionsPath);
    }

    String disbursementUrl() {
        return endpoint(disbursementsPath);
    }

    String balanceUrl() {
        return endpoint(balancePath);
    }

    String statusUrl(String segment, String reference) {
        String template = "collection".equalsIgnoreCase(segment) ? collectionStatusPath : disbursementStatusPath;
        String encodedRef = encodePathSegment(reference);
        if (template.contains("{reference}")) {
            return endpoint(template.replace("{reference}", encodedRef));
        }
        if (template.contains("{id}")) {
            return endpoint(template.replace("{id}", encodedRef));
        }
        return endpoint(appendPath(template, encodedRef + "/"));
    }

    public void setPublicKey(String publicKey) {
        if (publicKey != null && !publicKey.isEmpty()) {
            this.publicKey = publicKey;
        }
    }

    public String getPublicKey() {
        return publicKey;
    }

    static public String getGatewayCurrencyCode() {
        return gateway_currency_code;
    }

    static public String getGatewayId() {
        return gateway_id;
    }

    public String getSegment() {
        return segment;
    }

    public void setSegment(String segment) {
        this.segment = segment;
    }

    @Override
    public Double getBalance() {
        return getBalance("collection");
    }

    @Override
    public Double getBalance(String account) {
        try {
            Token token = getToken();
            if (token == null) {
                return 0.0;
            }
            Map<String, String> headers = standardHeaders(token);
            HttpRequestResponse response = Common.doHttpRequest("GET", balanceUrl(), "", headers);
            if (response != null && response.getStatusCode() == 401) {
                // Audit C2: see the matching comment in submit() above.
                Token refreshed = requestToken();
                if (refreshed != null) {
                    response = Common.doHttpRequest("GET", balanceUrl(), "", standardHeaders(refreshed));
                }
            }
            if (response == null || response.getStatusCode() != 200 || response.getResponse().isEmpty()) {
                return 0.0;
            }
            JSONObject json = new JSONObject(response.getResponse());
            if (!json.isNull("data")) {
                JSONObject data = json.getJSONObject("data");
                if (!data.isNull("balance")) {
                    return Double.parseDouble(data.getString("balance").replace(",", ""));
                }
            }
            return 0.0;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return 0.0;
        }
    }

    @Override
    public GateWayResponse doPayOut(Double amount, String payee, String ref, String narrative) {
        this.segment = "disbursement";
        try {
            JSONObject body = new JSONObject();
            body.put("pin", RSAUtil.encrypt(this.api_pin, this.publicKey));
            JSONObject transaction = new JSONObject();
            transaction.put("amount", amount);
            transaction.put("id", ref);
            body.put("transaction", transaction);
            JSONObject payeeObject = new JSONObject();
            payeeObject.put("msisdn", stripCountryCode(payee));
            body.put("payee", payeeObject);
            body.put("reference", narrative);
            return submit("POST", disbursementUrl(), body.toString(), ref);
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException |
                 NoSuchPaddingException | NoSuchAlgorithmException | JSONException e) {
            return gatewayError(e.getMessage(), "FAILED", "");
        }
    }

    @Override
    public GateWayResponse doPayIn(Double amount, String payer, String ref, String narrative) {
        this.segment = "collection";
        try {
            JSONObject body = new JSONObject();
            JSONObject transaction = new JSONObject();
            transaction.put("amount", amount);
            transaction.put("country", this.country);
            transaction.put("currency", this.base_currency);
            transaction.put("id", ref);
            body.put("transaction", transaction);
            JSONObject subscriber = new JSONObject();
            subscriber.put("currency", this.base_currency);
            subscriber.put("country", this.country);
            subscriber.put("msisdn", stripCountryCode(payer));
            body.put("subscriber", subscriber);
            body.put("reference", narrative);
            return submit("POST", collectionUrl(), body.toString(), ref);
        } catch (JSONException e) {
            return gatewayError(e.getMessage(), "UNDETERMINED", "");
        }
    }

    @Override
    public GateWayResponse checkStatus(String ref) {
        return submit("GET", statusUrl(this.segment, ref), "", ref);
    }

    @Override
    public AccountInfo getAccountInfo(String msisdn) {
        AccountInfo info = new AccountInfo();
        info.setMsisdn(msisdn);
        try {
            this.segment = "collection";
            Token token = this.getToken();
            if (token == null) return info;
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token.getToken());
            headers.put("X-Country", this.country);
            headers.put("X-Currency", this.base_currency);

            String url = endpoint("/standard/v2/users/" + encodePathSegment(msisdn));
            HttpRequestResponse rs = Common.doHttpRequest("GET", url, "", headers);
            if (rs == null || rs.getStatusCode() != 200) return info;
            JSONObject r = new JSONObject(rs.getResponse());
            if (!r.isNull("data")) {
                JSONObject data = r.getJSONObject("data");
                if (!data.isNull("first_name")) info.setFirstName(data.getString("first_name"));
                if (!data.isNull("last_name")) info.setLastName(data.getString("last_name"));
                if (!data.isNull("is_barred")) info.setStatus(data.getBoolean("is_barred") ? "BARRED" : "ACTIVE");
                if (!data.isNull("msisdn")) info.setMsisdn(data.getString("msisdn"));
            }
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
        }
        return info;
    }

    public Token getToken() throws IOException, JSONException {
        Token token = readToken();
        if (token == null || LocalDateTime.now().isAfter(token.created_on.plusMinutes(TOKEN_TTL_MINUTES))) {
            return requestToken();
        }
        return token;
    }

    public Token requestToken() throws JSONException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        JSONObject body = new JSONObject();
        body.put("client_id", this.api_username);
        body.put("client_secret", this.api_password);
        body.put("grant_type", "client_credentials");

        HttpRequestResponse response = Common.doHttpRequest("POST", tokenUrl(), body.toString(), headers);
        if (response == null || response.getStatusCode() != 200 || response.getResponse().isEmpty()) {
            logger.error("Failed to get Airtel OpenAPI token");
            return null;
        }
        JSONObject tokenJson = new JSONObject(response.getResponse());
        String accessToken = tokenJson.getString("access_token");
        LocalDateTime createdAt = LocalDateTime.now();
        saveToken(accessToken, createdAt);
        return new Token(accessToken, createdAt);
    }

    private GateWayResponse submit(String method, String url, String data, String ref) {
        try {
            Token token = getToken();
            if (token == null) {
                return gatewayError("Failed to obtain Airtel OpenAPI token", "UNDETERMINED", "");
            }
            HttpRequestResponse response = Common.doHttpRequest(method, url, data, standardHeaders(token));
            if (response != null && response.getStatusCode() == 401) {
                // Audit C2: the provider rejected this token even though our own TTL-based check
                // (getToken()) considered it still valid - revoked early, clock skew, or a
                // provider-side session invalidation. Force a fresh token and retry exactly once
                // rather than failing a transaction we could still complete.
                Token refreshed = requestToken();
                if (refreshed != null) {
                    response = Common.doHttpRequest(method, url, data, standardHeaders(refreshed));
                }
            }
            if (response == null) {
                return gatewayError("No response from Airtel OpenAPI", "UNDETERMINED", url + data);
            }
            GateWayResponse gatewayResponse = new GateWayResponse();
            gatewayResponse.setHttpStatus(String.valueOf(response.getStatusCode()));
            gatewayResponse.setRequestTrace(response.toString());
            gatewayResponse.setOurUniqueTxId(ref);
            if (response.getStatusCode() == 200) {
                applySuccessResponse(gatewayResponse, response);
            } else {
                gatewayResponse.setStatus("ERROR");
                gatewayResponse.setTransactionStatus("FAILED");
                gatewayResponse.setMessage(response.getResponse());
            }
            return gatewayResponse;
        } catch (Exception e) {
            return gatewayError(e.getMessage(), "UNDETERMINED", url + data);
        }
    }

    private void applySuccessResponse(GateWayResponse gatewayResponse, HttpRequestResponse response) throws JSONException {
        gatewayResponse.setStatus("OK");
        gatewayResponse.setTransactionStatus("PENDING");
        gatewayResponse.setMessage("Request submitted to the network successfully.");
        if (response.getResponse() == null || response.getResponse().isEmpty()) {
            return;
        }
        JSONObject json = new JSONObject(response.getResponse());
        if (!json.isNull("data") && !json.getJSONObject("data").isNull("transaction")) {
            JSONObject transaction = json.getJSONObject("data").getJSONObject("transaction");
            String status = transaction.isNull("status") ? "" : transaction.getString("status");
            String networkId = transaction.isNull("airtel_money_id") ? "" : transaction.optString("airtel_money_id", transaction.optString("reference_id", ""));
            gatewayResponse.setNetworkId(networkId);
            if ("TS".equalsIgnoreCase(status)) {
                gatewayResponse.setTransactionStatus("SUCCESSFUL");
            } else if ("TF".equalsIgnoreCase(status)) {
                gatewayResponse.setTransactionStatus("FAILED");
            } else if ("TA".equalsIgnoreCase(status)) {
                gatewayResponse.setTransactionStatus("UNDETERMINED");
            }
        }
        if (!json.isNull("status")) {
            JSONObject status = json.getJSONObject("status");
            String resultCode = status.isNull("result_code") ? "" : status.getString("result_code");
            String message = status.isNull("message") ? gatewayResponse.getMessage() : status.getString("message");
            gatewayResponse.setMessage(message);
            if (isFailedErrorList(resultCode)) {
                gatewayResponse.setStatus("ERROR");
                gatewayResponse.setTransactionStatus("FAILED");
            }
        }
    }

    private Token readToken() {
        // Tokens live only in the encrypted provider_tokens DB store (see ProviderTokenStoreService) -
        // no plaintext on-disk cache.
        Optional<ProviderToken> databaseToken = ProviderTokenStoreRegistry.findValid(gateway_id, this.segment, tokenEnvironment());
        if (databaseToken.isPresent()) {
            return new Token(databaseToken.get().getTokenValue(), LocalDateTime.now());
        }
        return null;
    }

    private void saveToken(String accessToken, LocalDateTime createdAt) {
        ProviderTokenStoreRegistry.save(
                gateway_id,
                this.segment,
                tokenEnvironment(),
                accessToken,
                Instant.now().plus(TOKEN_TTL_MINUTES, ChronoUnit.MINUTES));
    }

    private Map<String, String> standardHeaders(Token token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Authorization", "Bearer " + token.getToken());
        headers.put("X-Country", this.country);
        headers.put("X-Currency", this.base_currency);
        return headers;
    }

    private String tokenEnvironment() {
        if (this.mode != null && this.mode.toUpperCase().contains("PROD")) {
            return "PRODUCTION";
        }
        if (this.global_url != null && this.global_url.toLowerCase().contains("openapi.airtel")) {
            return "PRODUCTION";
        }
        return "SANDBOX";
    }

    private String endpoint(String pathOrUrl) {
        String configured = valueOrCurrent(pathOrUrl, "");
        if (configured.startsWith("http://") || configured.startsWith("https://")) {
            return configured;
        }
        String base = this.global_url == null || this.global_url.trim().isEmpty()
                ? "https://openapiuat.airtel.africa"
                : this.global_url.trim();
        return appendPath(base, configured);
    }

    private String appendPath(String base, String path) {
        if (path == null || path.trim().isEmpty()) {
            return base;
        }
        String cleanBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        return cleanBase + cleanPath;
    }

    private String valueOrCurrent(String candidate, String current) {
        return candidate == null || candidate.trim().isEmpty() ? current : candidate.trim();
    }

    private String encodePathSegment(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String stripCountryCode(String msisdn) {
        return msisdn != null && msisdn.length() > 3 ? msisdn.substring(3) : msisdn;
    }

    private GateWayResponse gatewayError(String message, String transactionStatus, String trace) {
        GateWayResponse response = new GateWayResponse();
        response.setHttpStatus("0");
        response.setMessage(message == null ? "Airtel OpenAPI request failed" : message);
        response.setStatus("ERROR");
        response.setTransactionStatus(transactionStatus);
        response.setRequestTrace(trace == null ? "" : trace);
        return response;
    }

    private Boolean isFailedErrorList(String error) {
        String[] errors = {"ESB000004", "ESB000008", "ESB000011", "ESB000014", "ESB000033", "ESB000034",
                "ESB000035", "ESB000036", "ESB000039", "ESB000045"};
        for (String code : errors) {
            if (code.equals(error)) {
                return true;
            }
        }
        return false;
    }

    public class Token {
        String token;
        LocalDateTime created_on;

        public Token(String token, LocalDateTime created_on) {
            this.token = token;
            this.created_on = created_on;
        }

        public String getToken() {
            return this.token;
        }

        public String toString() {
            return "Token: " + this.token + "\nCreated On: " + this.created_on.format(Common.getDateTimeFormater());
        }
    }
}

