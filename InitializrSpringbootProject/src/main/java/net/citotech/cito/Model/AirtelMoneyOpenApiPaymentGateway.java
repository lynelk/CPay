package net.citotech.cito.Model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import net.citotech.cito.Common;
import net.citotech.cito.SettingsController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

public class AirtelMoneyOpenApiPaymentGateway extends PaymentGateway {
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

    String publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCkq3XbDI1s8Lu7SpUBP+bqOs/MC6PKWz6n/0UkqTiOZqKqaoZClI3BUDTrSIJsrN1Qx7ivBzsaAYfsB0CygSSWay4iyUcnMVEDrNVOJwtWvHxpyWJC5RfKBrweW9b8klFa/CfKRtkK730apy0Kxjg+7fF0tB4O3Ic9Gxuv4pFkbQIDAQAB";

    @Value("${custom.lockfiledirectory}")
    private String lockfiledirectory;

    public static boolean isValidMisdn(String msisdn) {
        for (String p : prefix) {
            Matcher matcher = Pattern.compile("^" + p).matcher(msisdn);
            if (matcher.find()) {
                return true;
            }
        }
        return false;
    }

    public void setApiDetails(String global_url, String api_username, String api_password, String api_pin) {
        this.global_url = global_url;
        this.api_username = api_username;
        this.api_password = api_password;
        this.api_pin = api_pin;
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
            HttpRequestResponse response = Common.doHttpRequest("GET", this.global_url + "/standard/v1/users/balance", "", headers);
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
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
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
            return submit("POST", this.global_url + "/standard/v1/disbursements/", body.toString(), ref);
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
            return submit("POST", this.global_url + "/merchant/v1/payments/", body.toString(), ref);
        } catch (JSONException e) {
            return gatewayError(e.getMessage(), "UNDETERMINED", "");
        }
    }

    @Override
    public GateWayResponse checkStatus(String ref) {
        String path = this.segment.equals("collection")
                ? "/standard/v1/payments/" + ref + "/"
                : "/standard/v1/disbursements/" + ref + "/";
        return submit("GET", this.global_url + path, "", ref);
    }

    @Override
    public PaymentGateway.AccountInfo getAccountInfo(String msisdn) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    Boolean isTokenAboutToExpire() throws IOException {
        Token token = readToken();
        if (token == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(token.created_on.plusMinutes(TOKEN_TTL_MINUTES));
    }

    public Token getToken() throws IOException {
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

        HttpRequestResponse response = Common.doHttpRequest("POST", this.global_url + "/auth/oauth2/token", body.toString(), headers);
        if (response == null || response.getStatusCode() != 200 || response.getResponse().isEmpty()) {
            Logger.getLogger(SettingsController.class.getName()).log(Level.SEVERE, "Failed to get Airtel OpenAPI token", "");
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

    private Token readToken() throws IOException {
        File resource = tokenFile();
        String tokenFileContent = new String(Files.readAllBytes(resource.toPath())).trim();
        if (tokenFileContent.isEmpty()) {
            return null;
        }
        try {
            JSONObject allTokens = new JSONObject(tokenFileContent);
            if (allTokens.isNull(this.segment)) {
                return null;
            }
            JSONObject segmentToken = allTokens.getJSONObject(this.segment);
            LocalDateTime createdAt = LocalDateTime.parse(segmentToken.getString("created_on"), Common.getDateTimeFormater());
            return new Token(segmentToken.getString("token"), createdAt);
        } catch (JSONException e) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            return null;
        }
    }

    private void saveToken(String accessToken, LocalDateTime createdAt) {
        try {
            File resource = tokenFile();
            JSONObject newToken = new JSONObject();
            newToken.put("token", accessToken);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            newToken.put("created_on", createdAt.format(formatter));
            String existing = new String(Files.readAllBytes(resource.toPath())).trim();
            JSONObject allTokens = existing.isEmpty() ? new JSONObject() : new JSONObject(existing);
            allTokens.put(this.segment, newToken);
            Files.writeString(resource.toPath(), allTokens.toString());
        } catch (Exception e) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private File tokenFile() throws IOException {
        File directory = new File(lockfiledirectory == null ? "/tmp/cpay/locks/" : lockfiledirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        File resource = new File(directory, Common.CLASS_PATH_AIRTELOAPI_TOKEN_FILE);
        if (resource.createNewFile()) {
            Logger.getLogger(AirtelMoneyOpenApiPaymentGateway.class.getName()).log(Level.INFO,
                    "AirtelMoney token file created: " + resource.getAbsolutePath());
        }
        return resource;
    }

    private Map<String, String> standardHeaders(Token token) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + token.getToken());
        headers.put("X-Country", this.country);
        headers.put("X-Currency", this.base_currency);
        return headers;
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
