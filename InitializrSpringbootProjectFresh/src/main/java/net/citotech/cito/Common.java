package net.citotech.cito;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.citotech.cito.Model.*;
import net.citotech.cito.async.ManagedAsyncTasks;
import net.citotech.cito.callback.CallbackTaskRepository;
import net.citotech.cito.merchant.MerchantKeyCryptoRegistry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @author josephtabajjwa
 */
public class Common {
    private static final net.citotech.cito.legacy.LegacyCommonSupport LEGACY_SUPPORT =
            new net.citotech.cito.legacy.LegacyCommonSupport();

    public static final String DB_TABLE_ADMIN = "admins";
    public static final String DB_TABLE_ADMIN_PRIVILEGES = "admin_privileges";
    public static final String DB_TABLE_AUDIT_TRAIL = "audit_trail";
    public static final String DB_TABLE_AUDIT_TRAIL_MERCHANT = "merchants_audit_trail";
    public static final String DB_TABLE_SETTINGS = "settings";
    public static final String DB_TABLE_MERCHANTS = "merchants";
    public static final String DB_TABLE_MERCHANT_USERS = "merchant_admins";
    public static final String DB_TABLE_MERCHANT_TRANSACTION_LOG = "merchant_transactions_log";
    public static final String DB_TABLE_MERCHANT_BATCH_TRANSACTION_LOG =
            "merchant_batch_transactions_log";
    public static final String DB_TABLE_MERCHANT_BATCH_TRANSACTION_BENEFICIARIES = "beneficiaries";
    public static final String DB_TABLE_MERCHANT_STATEMENT = "merchant_statement";
    public static final String DB_TABLE_CHARGING_DETAILS = "charging_details";
    public static final String DB_TABLE_MERCHANT_ADMIN_PRIVILEGES = "merchant_admin_privileges";
    public static final String DB_TABLE_DB_CHANGES = "db_changes";
    public static final String DB_TABLE_MERCHANT_SMS = "merchant_sms";
    public static final String DB_MERCHANTS_SETTINGS = "merchant_settings";

    // Settings classpath keys
    public static final String CLASS_PATH_DEFAULT_SETTINGS = "settings/default_settings.json";
    public static final String CLASS_PATH_DEFAULT_MERCHANT_SETTINGS =
            "settings/default_merchant_settings.json";
    public static final String CLASS_PATH_GENERAL_SETTINGS = "settings/general_settings.json";
    public static final String CLASS_PATH_GENERAL_DBCHANGES_DIR = "dbchanges";

    public static File safeLockFile(String lockFileDirectory, String fileName) throws IOException {
        if (fileName == null || !fileName.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("Invalid lock file name.");
        }

        Path base =
                Paths.get(lockFileDirectory == null ? "" : lockFileDirectory)
                        .toAbsolutePath()
                        .normalize();
        Files.createDirectories(base);

        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            throw new IOException("Invalid lock file path.");
        }

        return resolved.toFile();
    }

    public static final String CLASS_PATH_CHECK_TX_LOCK = "check_tx.lock";
    public static final String CLASS_PATH_SEND_SMS_SERVICE_TX_LOCK = "send_sms_service_tx.lock";
    public static final String CLASS_PATH_UPLOAD_DIRECTORY = "uploadDir";
    public static final String CLASS_PATH_PAYMENTS_CRON_TX_LOCK = "payments_cron_tx.lock";

    @FunctionalInterface
    public interface OutboundHttpExecutor {
        HttpRequestResponse execute(
                String method, String url, String data, Map<String, String> headers);
    }

    private static volatile OutboundHttpExecutor outboundHttpExecutor;

    /** Application base URL used in outbound email links (e.g. password-reset). */
    private static volatile String appBaseUrl = "";

    /**
     * Called at startup by {@link net.citotech.cito.config.SslConfig} to set the application base
     * URL used in outbound email links.
     */
    public static void setAppBaseUrl(String url) {
        appBaseUrl = (url != null) ? url : "";
    }

    /**
     * IP addresses of reverse proxies/load balancers this deployment sits behind. Empty by default,
     * meaning X-Forwarded-For/X-Real-IP are never trusted until explicitly configured (via {@code
     * cpay.security.trusted-proxy-ips}) - those headers are attacker-controlled on any direct
     * connection, and trusting them unconditionally lets a client spoof the IP used for
     * rate-limiting and audit logging.
     */
    private static volatile Set<String> trustedProxyIps = Set.of();

    /**
     * Called at startup by {@link net.citotech.cito.config.SslConfig} to set the trusted proxy
     * hop(s) whose X-Forwarded-For/X-Real-IP headers {@link #getIpAddress} may trust.
     */
    public static void setTrustedProxyIps(String commaSeparatedIps) {
        if (commaSeparatedIps == null || commaSeparatedIps.isBlank()) {
            trustedProxyIps = Set.of();
            return;
        }
        trustedProxyIps =
                Arrays.stream(commaSeparatedIps.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public static String jsonText(JSONObject obj, String key, String defaultValue) {
        return LEGACY_SUPPORT.jsonText(obj, key, defaultValue);
    }

    public static void setOutboundHttpExecutor(OutboundHttpExecutor executor) {
        outboundHttpExecutor = executor;
    }

    private static final String NUMERIC_STRING = "0123456789";
    private static final String ALPHA_NUMERIC_STRING = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final int HTTP_REQUEST_TIMEOUT_MILLISECONDS = 30000;
    public static final int HTTP_REQUEST_READTIMEOUT_MILLISECONDS = 60000;

    public static final String API_MOBILE_MONEY_PAYIN = "MOBILE_MONEY_PAYIN";
    public static final String API_MOBILE_MONEY_PAYOUT = "MOBILE_MONEY_PAYOUT";
    public static final String API_MULTIPLE_PAYOUT = "MULTIPLE_PAYOUT";
    public static final String API_MULTIPLE_CHECKSTATUS = "MULTIPLE_CHECKSTATUS";
    public static final String API_TRANSACTION_CHECKSTATUS = "TRANSACTION_CHECKSTATUS";
    public static final String API_BALANCE_CHECK = "BALANCE_CHECK";
    public static final String API_ACCOUNT_VALIDATION = "ACCOUNT_VALIDATION";
    public static final String API_STATEMENT_EXPORT = "STATEMENT_EXPORT";
    public static final String API_SEND_SMS = "API_SEND_SMS";

    /*
     * Returns random numeric string
     * @Parma count: is the length you would like
     */
    public static String randomNumericString(int count) {
        return LEGACY_SUPPORT.randomNumericString(count);
    }

    public static String randomUrlSafeToken(int byteCount) {
        if (byteCount < 16) {
            throw new IllegalArgumentException("Token entropy must be at least 128 bits.");
        }
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String recordAction(
            User user, String action, NamedParameterJdbcTemplate jdbcTemplate) {
        // Audit F8: hash-chained, append-only (see V28 - a DB trigger rejects UPDATE/DELETE on this
        // table outright). entry_hash covers this row's content plus the chain's current tip, so
        // altering or deleting any row breaks every hash chained after it - verified on demand by
        // net.citotech.cito.audit.AuditChainVerificationService.
        String prevHash =
                net.citotech.cito.audit.AuditChainService.fetchLastHash(
                        Common.DB_TABLE_AUDIT_TRAIL, jdbcTemplate);
        String entryHash =
                net.citotech.cito.audit.AuditChainService.computeEntryHash(
                        prevHash, user.getName(), user.getEmail(), null, action);

        // Now add the user to database
        String sql =
                "INSERT INTO "
                        + Common.DB_TABLE_AUDIT_TRAIL
                        + " "
                        + " SET `user_name`=:user_name,"
                        + " `user_id`=:user_id, "
                        + " `action`=:action,"
                        + " `prev_hash`=:prev_hash,"
                        + " `entry_hash`=:entry_hash";

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("user_name", user.getName());
        parameters.put("user_id", user.getEmail());
        parameters.put("action", action);
        parameters.put("prev_hash", prevHash);
        parameters.put("entry_hash", entryHash);

        try {
            jdbcTemplate.update(sql, parameters);
            // Now insert privileges
            return "success";
        } catch (Exception e) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    public static String recordMerchantAction(
            MerchantUser user, String action, NamedParameterJdbcTemplate jdbcTemplate) {
        // Audit F8: hash-chained, append-only - see recordAction's comment above for the full
        // rationale; this is the same chain applied to the merchant-side audit table.
        String merchantIdStr =
                user.getMerchant_id() == null ? null : String.valueOf(user.getMerchant_id());
        String prevHash =
                net.citotech.cito.audit.AuditChainService.fetchLastHash(
                        Common.DB_TABLE_AUDIT_TRAIL_MERCHANT, jdbcTemplate);
        String entryHash =
                net.citotech.cito.audit.AuditChainService.computeEntryHash(
                        prevHash, user.getName(), user.getEmail(), merchantIdStr, action);

        // Now add the user to database
        String sql =
                "INSERT INTO "
                        + Common.DB_TABLE_AUDIT_TRAIL_MERCHANT
                        + " "
                        + " SET `user_name`=:user_name,"
                        + " `user_id`=:user_id, "
                        + " `merchant_id`=:merchant_id, "
                        + " `action`=:action,"
                        + " `prev_hash`=:prev_hash,"
                        + " `entry_hash`=:entry_hash";

        Map<String, Object> parameters = new HashMap<String, Object>();

        parameters.put("user_name", user.getName());
        parameters.put("user_id", user.getEmail());
        parameters.put("merchant_id", user.getMerchant_id());
        parameters.put("action", action);
        parameters.put("prev_hash", prevHash);
        parameters.put("entry_hash", entryHash);

        try {
            jdbcTemplate.update(sql, parameters);
            // Now insert privileges
            return "success";
        } catch (Exception e) {
            return GeneralException.getError("102", GeneralException.ERRORS_102);
        }
    }

    /*
     * Returns random alpha numeric string.
     * @Parma count: is the length you would like
     */
    public static String randomAlphaNumericString(int count) {
        return LEGACY_SUPPORT.randomAlphaNumericString(count);
    }

    /*
     * Checks to see if the user is allowed to access or perform an account
     *
     */
    static Boolean isUserAllowedAccessToThis(String permission, User user) {
        List<UserPrivilege> uPermissions = user.getPrivileges();
        for (UserPrivilege p : uPermissions) {
            String privilege = p.getPrivilege();
            if (privilege != null && privilege.equals(permission)) {
                return true;
            }
        }
        return false;
    }

    /*
     * Retrieves settings page
     *
     * Returns Settings Object or null.
     */
    public static Setting getSettings(
            String settings_name, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", settings_name);
        String sqlSelect = "SELECT *  FROM " + Common.DB_TABLE_SETTINGS + " " + " WHERE name=:name";
        RowMapper<Setting> rm =
                (rs, rowNum) -> {
                    Setting setting = new Setting();
                    setting.setName(rs.getString("name"));
                    setting.setLabel(rs.getString("label"));
                    setting.setSetting_value(rs.getString("setting_value"));
                    setting.setId(rs.getLong("id"));
                    setting.setGroup(rs.getString("setting_group"));
                    setting.setDescription(rs.getString("description"));
                    return setting;
                };
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }

    /*
     * Retrieves settings page
     *
     * Returns Settings Object or null.
     */
    public static Setting getMerchantSettings(
            String settings_name, Long merchant_id, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("name", settings_name);
        parameters.addValue("merchant_id", merchant_id);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_MERCHANTS_SETTINGS
                        + " "
                        + " WHERE name=:name AND merchant_id=:merchant_id ";
        RowMapper<Setting> rm =
                (rs, rowNum) -> {
                    Setting setting = new Setting();
                    setting.setName(rs.getString("name"));
                    setting.setLabel(rs.getString("label"));
                    setting.setSetting_value(rs.getString("setting_value"));
                    setting.setId(rs.getLong("id"));
                    setting.setGroup(rs.getString("setting_group"));
                    setting.setDescription(rs.getString("description"));
                    setting.setMerchant_id(rs.getLong("merchant_id"));
                    return setting;
                };
        List<Setting> listSettings = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listSettings.size() > 0) {
            return listSettings.get(0);
        } else {
            return null;
        }
    }

    public static DateTimeFormatter getDateTimeFormater() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return formatter;
    }

    public static String getCurrentDate() {
        LocalDateTime dt = LocalDateTime.now();
        return dt.format(Common.getDateTimeFormater());
    }

    /*
     *
     * Helper method to make http requests.
     *
     *
     * @Param method: This may be set to GET, POST, PUT, DELETE.
     * @Param url: This is the url to call.
     * @Param data: This is the data to be sent.
     * @Param headers: a hashmap of headers.
     *
     * Returns HttpRequestREsponse class
     */

    public static HttpRequestResponse doHttpRequest(
            String method, String url, String data, Map<String, String> headers) {
        // Audit H2: forward the current request's correlation id downstream (provider APIs,
        // webhook/callback deliveries) so our logs and the receiving system's logs for the same
        // call can be cross-referenced. Never overrides a header the caller explicitly set.
        Map<String, String> requestHeaders =
                headers == null ? new HashMap<>() : new HashMap<>(headers);
        String requestId = org.slf4j.MDC.get("request_id");
        if (requestId != null
                && !requestHeaders.containsKey(
                        net.citotech.cito.config.RequestCorrelationFilter.REQUEST_ID_HEADER)) {
            requestHeaders.put(
                    net.citotech.cito.config.RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
        }

        OutboundHttpExecutor executor = outboundHttpExecutor;
        if (executor != null) {
            try {
                return executor.execute(method, url, data, requestHeaders);
            } catch (Exception ex) {
                Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
                return failedHttpResponse(url, data, requestHeaders, ex);
            }
        }

        return doHttpRequestWithUrlConnection(method, url, data, requestHeaders);
    }

    private static HttpRequestResponse doHttpRequestWithUrlConnection(
            String method, String url, String data, Map<String, String> headers) {
        HttpRequestResponse r = new HttpRequestResponse();
        r.setUrl(url);
        r.setRequestData(data);
        r.setRequestHeaders(headers);
        try {
            URL rquestUrl = URI.create(url).toURL();
            HttpURLConnection con = (HttpURLConnection) rquestUrl.openConnection();

            con.setRequestMethod(method);

            for (Map.Entry<String, String> h : headers.entrySet()) {
                con.setRequestProperty(h.getKey(), h.getValue());
            }

            con.setConnectTimeout(HTTP_REQUEST_TIMEOUT_MILLISECONDS);
            con.setReadTimeout(HTTP_REQUEST_READTIMEOUT_MILLISECONDS);
            con.setDoOutput(true);

            // methods without the body.
            List<String> methods = new ArrayList<>();
            methods.add("DELETE");
            methods.add("PUT");
            methods.add("POST");

            if (methods.contains(method)) {
                DataOutputStream out = new DataOutputStream(con.getOutputStream());
                out.writeBytes(data);
                out.flush();
                out.close();
            }

            // Now read the content of the response
            int status = con.getResponseCode();

            Reader streamReader = null;
            if (status > 299) {
                if (con.getErrorStream() == null) {
                    streamReader = null;
                } else {
                    streamReader = new InputStreamReader(con.getErrorStream());
                }
            } else {
                streamReader = new InputStreamReader(con.getInputStream());
            }

            // streamReader = new InputStreamReader(con.getInputStream());
            StringBuffer content = new StringBuffer();
            if (streamReader != null) {
                BufferedReader in = new BufferedReader(streamReader);
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }
                in.close();
                r.setStatusCode(status);
                r.setResponse(content.toString());
                r.setRequestHeaders(headers);
            } else {
                content.append("");
                r.setStatusCode(status);
                r.setResponse(content.toString());
                r.setRequestHeaders(headers);
            }
            Map<String, String> rHeaders = new HashMap<>();

            con.getHeaderFields().entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .forEach(
                            entry -> {
                                List<String> headerValues = entry.getValue();
                                String sHeaderValue = "";
                                Iterator<String> it = headerValues.iterator();
                                if (it.hasNext()) {
                                    sHeaderValue += it.next();
                                    while (it.hasNext()) {
                                        sHeaderValue += it.next();
                                    }
                                }

                                rHeaders.put(entry.getKey(), sHeaderValue);
                            });
            r.setResponseHeaders(rHeaders);
            r.setErrorMessage("");
            con.disconnect();

            return r;
        } catch (MalformedURLException ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        } catch (IOException ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        } catch (Exception ex) {
            r.setResponse("");
            r.setErrorMessage(ex.getMessage());
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return r;
        }
    }

    private static HttpRequestResponse failedHttpResponse(
            String url, String data, Map<String, String> headers, Exception ex) {
        HttpRequestResponse r = new HttpRequestResponse();
        r.setUrl(url);
        r.setRequestData(data);
        r.setRequestHeaders(headers);
        r.setResponse("");
        r.setErrorMessage(
                ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        return r;
    }

    public static String base64Encode(String content) {
        return Base64.getEncoder().encodeToString(content.getBytes());
    }

    public static String base64Decode(String content) {
        byte[] decodedBytes = Base64.getDecoder().decode(content);
        return new String(decodedBytes);
    }

    public static PublicKey getPublicKeyFromBase64String(String base64String) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            X509EncodedKeySpec keySpecX509 =
                    new X509EncodedKeySpec(Base64.getMimeDecoder().decode(base64String));
            RSAPublicKey pubKey = (RSAPublicKey) kf.generatePublic(keySpecX509);
            return pubKey;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }

    public static PrivateKey getPrivateKeyFromBase64String(String base64String) {
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpecPKCS8 =
                    new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(base64String));
            PrivateKey privKey = kf.generatePrivate(keySpecPKCS8);
            return privKey;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        } catch (InvalidKeySpecException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }

    public static String generateSha256String(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.reset();
            digest.update(data.getBytes("utf8"));
            String sha256 = String.format("%064x", new BigInteger(1, digest.digest()));
            return sha256;
        } catch (Exception e) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, e.getMessage(), e);
            return "";
        }
    }

    public static String generateUuid() {
        UUID uuid = UUID.randomUUID();
        String r = uuid.toString();
        return r; // r.substring(0, 25);
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     *
     * Returns Merchant object or null.
     */
    public static Merchant getMerchantById(String id, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_id", id);
        String sqlSelect =
                "SELECT *  FROM " + Common.DB_TABLE_MERCHANTS + " " + " WHERE id=:account_id";
        RowMapper<Merchant> rm =
                (rs, rowNum) -> {
                    Merchant m = new Merchant();
                    m.setName(rs.getString("name"));
                    m.setShort_name(rs.getString("short_name"));
                    m.setAccount_number(rs.getString("account_number"));
                    m.setStatus(rs.getString("status"));
                    m.setId(rs.getLong("id"));
                    m.setCreated_on(rs.getString("created_on"));
                    m.setCreated_by(rs.getString("created_by"));
                    m.setAccount_type(rs.getString("account_type"));
                    m.setPublic_key(rs.getString("public_key"));
                    m.setPrivate_key(
                            MerchantKeyCryptoRegistry.decryptForUse(rs.getString("private_key")));
                    m.setHmac_secret(
                            MerchantKeyCryptoRegistry.decryptForUse(rs.getString("hmac_secret")));
                    String allowed_apis_string =
                            rs.getString("allowed_apis") != null
                                    ? rs.getString("allowed_apis")
                                    : "";

                    String[] allowed_apis;
                    if (allowed_apis_string.isEmpty()) {
                        allowed_apis = new String[0];
                    } else {
                        allowed_apis = allowed_apis_string.split(",");
                    }
                    m.setAllowed_apis(allowed_apis);

                    m.setUsers(getMerchantUsers(m, jdbcTemplate));
                    return m;
                };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: The customer's reference as submitted in the API request.
     * @Param merchant_id: This is the customer's long id
     * Returns Merchant object or null.
     */
    public static Transaction getMerchantTxByTheirRef(
            String reference, String merchant_id, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchant_id);
        parameters.addValue("tx_merchant_ref", reference);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " WHERE merchant_id=:merchant_id AND tx_merchant_ref=:tx_merchant_ref";
        RowMapper<Transaction> rm = Common.getTransactionRowMapper();
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    public static Transaction getTxByRef(
            String reference, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tx_unique_id", reference);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " WHERE tx_unique_id=:tx_unique_id";
        RowMapper<Transaction> rm = Common.getTransactionRowMapper();
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    public static Transaction getTxByNetworkRef(
            String networkRef, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("tx_gateway_ref", networkRef);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " WHERE tx_gateway_ref=:tx_gateway_ref FOR UPDATE";
        RowMapper<Transaction> rm = Common.getTransactionRowMapper();
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: This is our reference.
     * Returns Merchant object or null.
     */
    public static Transaction getTxBySafaricomRef(
            String networkRef, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("safaricom_request_reference", networkRef);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " WHERE safaricom_request_reference=:safaricom_request_reference FOR UPDATE";
        RowMapper<Transaction> rm = Common.getTransactionRowMapper();
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their id.
     * @Param reference: The customer's reference as submitted in the API request.
     * @Param merchant_id: This is the customer's long id
     * Returns Merchant object or null.
     */
    public static Transaction getTxByBatchIdBeneficiaryId(
            long batch_id, long beneficiaryId, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("beneficiary_id", beneficiaryId);
        parameters.addValue("batch_id", batch_id);
        // ORDER BY id DESC (audit B8): a retried beneficiary gets a new transaction row rather
        // than reusing the failed one, so this must return the most recent attempt - without
        // this, a retry's fresh row would be invisible to every caller here, which always found
        // the original (oldest, permanently FAILED) row instead.
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                        + " "
                        + " WHERE beneficiary_id=:beneficiary_id "
                        + " AND merchant_batch_transactions_log_id=:batch_id "
                        + " ORDER BY id DESC LIMIT 1";

        RowMapper<Transaction> rm = Common.getTransactionRowMapper();
        List<Transaction> listTxs = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listTxs.size() > 0) {
            return listTxs.get(0);
        } else {
            return null;
        }
    }

    /*
     * Queries the database to get the Merchant
     * by their account_number.
     *
     * Returns Merchant object or null.
     */
    public static Merchant getMerchantByAccountNumber(
            String acc_number, NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_number", acc_number);
        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANTS
                        + " "
                        + " WHERE account_number=:account_number";

        RowMapper<Merchant> rm =
                (rs, rowNum) -> {
                    Merchant m = new Merchant();
                    m.setName(rs.getString("name"));
                    m.setAccount_number(rs.getString("account_number"));
                    m.setStatus(rs.getString("status"));
                    m.setId(rs.getLong("id"));
                    m.setCreated_on(rs.getString("created_on"));
                    m.setCreated_by(rs.getString("created_by"));
                    m.setAccount_type(rs.getString("account_type"));
                    m.setPublic_key(rs.getString("public_key"));
                    m.setPrivate_key(
                            MerchantKeyCryptoRegistry.decryptForUse(rs.getString("private_key")));
                    m.setHmac_secret(
                            MerchantKeyCryptoRegistry.decryptForUse(rs.getString("hmac_secret")));
                    m.setShort_name(rs.getString("short_name"));
                    // Get allowed APIs
                    String allowed_apis_string =
                            rs.getString("allowed_apis") != null
                                    ? rs.getString("allowed_apis")
                                    : "";

                    String[] allowed_apis;
                    if (allowed_apis_string.isEmpty()) {
                        allowed_apis = new String[0];
                    } else {
                        allowed_apis = allowed_apis_string.split(",");
                    }
                    m.setAllowed_apis(allowed_apis);

                    m.setUsers(getMerchantUsers(m, jdbcTemplate));
                    return m;
                };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }

    /*
    * Queries the database to get the user
    * by their email address.
    *
    * Returns User object or null.
    *
    static public Merchant getMerchantByAccountNumber(String account,
            NamedParameterJdbcTemplate jdbcTemplate) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("account_number", account);
        String sqlSelect = "SELECT *  FROM "+Common.DB_TABLE_MERCHANTS+" "
                + " WHERE account_number=:account_number";
        RowMapper<Merchant> rm = (rs, rowNum) -> {
                Merchant m = new Merchant();
                m.setName(rs.getString("name"));
                m.setAccount_number(rs.getString("account_number"));
                m.setStatus(rs.getString("status"));
                m.setId(rs.getLong("id"));
                m.setCreated_on(rs.getString("created_on"));
                m.setCreated_by(rs.getString("created_by"));
                m.setAccount_type(rs.getString("account_type"));
                m.setUsers(getMerchantUsers(m, jdbcTemplate));
                return m;
        };
        List<Merchant> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);
        if (listUsers.size() > 0) {
            return listUsers.get(0);
        } else {
            return null;
        }
    }*/

    public static List<MerchantUser> getMerchantUsers(
            Merchant merchant, NamedParameterJdbcTemplate jdbcTemplate) {
        String sqlSelect =
                "SELECT *,"
                        + " IF((DATE_ADD(email_verification_sent_on, INTERVAL 5 MINUTE) < NOW())"
                        + ", 'TRUE', 'FALSE' ) AS is_verification_timedout "
                        + " FROM "
                        + Common.DB_TABLE_MERCHANT_USERS
                        + " "
                        + " WHERE ";
        sqlSelect += " merchant_id=:merchant_id";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("merchant_id", merchant.getId());

        RowMapper<MerchantUser> rm =
                (rs, rowNum) -> {
                    MerchantUser user = new MerchantUser();
                    user.setName(rs.getString("name"));
                    user.setId(rs.getLong("id"));
                    user.setCreated_on(rs.getString("created_on"));
                    user.setEmail(rs.getString("email"));
                    user.setPhone(rs.getString("phone"));
                    user.setStatus(rs.getString("status"));
                    user.setIs_verification_timedout(rs.getString("is_verification_timedout"));
                    user.setEmail_verification_code(rs.getString("email_verification_code"));

                    user.setPrivileges(getUserPrivileges(user, jdbcTemplate));
                    return user;
                };

        List<MerchantUser> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        return listUsers;
    }

    public static List<UserPrivilege> getUserPrivileges(
            User user, NamedParameterJdbcTemplate jdbcTemplate) {
        String sqlSelect = "SELECT * FROM " + Common.DB_TABLE_MERCHANT_ADMIN_PRIVILEGES + " WHERE ";
        sqlSelect += " admin_id=:admin_id";

        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("admin_id", user.getId());

        RowMapper<UserPrivilege> rm =
                (rs, rowNum) -> {
                    UserPrivilege up = new UserPrivilege();
                    up.setPrivilege(rs.getString("privilege"));
                    up.setId(rs.getLong("id"));
                    up.setCreated_on(rs.getString("created_on"));
                    up.setUdpated_on(rs.getString("updated_on"));
                    return up;
                };

        List<UserPrivilege> listUsers = jdbcTemplate.query(sqlSelect, parameters, rm);

        return listUsers;
    }

    public static ArrayList<Balance> getMerchantBalances(
            String merchant_id, NamedParameterJdbcTemplate jdbcTemplate) {
        // Set the response header

        MapSqlParameterSource parameters = new MapSqlParameterSource();

        parameters.addValue("merchant_id", merchant_id);

        String sqlSelect =
                "SELECT *  FROM "
                        + Common.DB_TABLE_MERCHANT_STATEMENT
                        + " "
                        + "WHERE merchant_id = :merchant_id"
                        + " ORDER BY id DESC LIMIT 1";

        RowMapper<Statement> rm =
                (rs, rowNum) -> {
                    Statement t = new Statement();
                    t.setId(rs.getLong("id"));
                    t.setAmount(rs.getDouble("amount"));
                    t.setTransactions_log_id(rs.getLong("transactions_log_id"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setAirtelmm_balance(rs.getDouble("airtelmm_balance"));
                    t.setMtnmm_balance(rs.getDouble("mtnmm_balance"));
                    t.setDescription(rs.getString("description"));
                    t.setTx_type(rs.getString("tx_type"));
                    t.setSms_balance(rs.getDouble("sms_balance"));
                    t.setSafaricom_balance(rs.getDouble(SafariComPaymentGateway.BALANCE_TYPE));
                    return t;
                };

        // ResultSet rs;
        List<Statement> listS = jdbcTemplate.query(sqlSelect, parameters, rm);
        ArrayList<Balance> balances = new ArrayList<>();
        for (Statement us : listS) {
            String code = MTNMoMoPaymentGateway.getGatewayCurrencyCode();
            Double amount = us.getMtnmm_balance();
            String gateway_id = MTNMoMoPaymentGateway.getGatewayId();
            Balance mtn_mm = new Balance(code, amount, gateway_id);
            mtn_mm.setBalance_type(Balance.BALANCE_TYPE_MTNMM_BALANCE);

            // Airtel
            String airtelmm_code = AirtelMoneyPaymentGateway.getGatewayCurrencyCode();
            Double airtelmm_amount = us.getAirtelmm_balance();
            String airtelmm_gateway_id = AirtelMoneyPaymentGateway.getGatewayId();
            Balance airtelmm_mtn_mm =
                    new Balance(airtelmm_code, airtelmm_amount, airtelmm_gateway_id);
            airtelmm_mtn_mm.setBalance_type(Balance.BALANCE_TYPE_AIRTELMM_BALANCE);

            // Safaricom
            String safaricom_balance_code = SafariComPaymentGateway.getGatewayCurrencyCode();
            Double safaricom_balance_amount = us.getSafaricom_balance();
            String safaricom_balance_gateway_id = SafariComPaymentGateway.getGatewayId();
            Balance safaricom_balance_mm =
                    new Balance(
                            safaricom_balance_code,
                            safaricom_balance_amount,
                            safaricom_balance_gateway_id);
            safaricom_balance_mm.setBalance_type(Balance.BALANCE_TYPE_SAFARICOMMM_BALANCE);

            // Other balances
            String sms_code = SmsGateway.getGatewayCurrencyCode();
            Double sms_amount = us.getSms_balance();
            String sms_gateway_id = SmsGateway.getGatewayId();
            Balance sms_balance = new Balance(sms_code, sms_amount, sms_gateway_id);
            sms_balance.setBalance_type(Balance.BALANCE_TYPE_SMS_BALANCE);

            balances.add(mtn_mm);
            balances.add(airtelmm_mtn_mm);
            balances.add(safaricom_balance_mm);
            balances.add(sms_balance);
        }

        return balances;
    }

    public static String numberFormat(Double n) {
        String formattedNumber = String.format("%,.2f", n);
        return formattedNumber;
    }

    public static KeyPairStrings generateKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            KeyPair kp = kpg.generateKeyPair();

            Base64.Encoder encoder = Base64.getMimeEncoder();

            Key pub = kp.getPublic();
            Key pvt = kp.getPrivate();

            String private_k = "-----BEGIN PRIVATE KEY-----\n";
            private_k += encoder.encodeToString(pvt.getEncoded());
            private_k += "\n-----END PRIVATE KEY-----\n";

            String public_k = "-----BEGIN PUBLIC KEY-----\n";
            public_k += encoder.encodeToString(pub.getEncoded());
            public_k += "\n-----END PUBLIC KEY-----\n";

            // Logger.getLogger(Common.class.getName()).log(Level.SEVERE, private_k, "");

            KeyPairStrings r = new KeyPairStrings(public_k, private_k);
            return r;
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(Common.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public static String implodeStringJsonArray(JSONArray strings) throws JSONException {
        String r = "";
        for (int i = 0; i < strings.length(); i++) {
            String s = strings.getString(i).trim();
            if (s.isEmpty()) {
                continue;
            }
            r += s + ",";
        }
        if (r.length() > 0) {
            r = r.substring(0, (r.length() - 1));
        }
        return r;
    }

    /*
     * @Param String balance_type: This is the balance type, check Common.
     * @Param Statement tx : This is the statement transaction.
     * Returns success | JSON String with errors.
     */

    public static String recordStatementTx(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyStatementEngine.recordStatementTx(tx, balance_type, jdbcTemplate, transactionManager);
    }

    public static String recordStatementTxWithoutTransaction(
            Statement tx,
            String balance_type,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            TransactionStatus status) {
        return LegacyStatementEngine.recordStatementTxWithoutTransaction(
                tx, balance_type, jdbcTemplate, transactionManager, status);
    }

    /**
     * Shared CR/DR balance-update core for {@link #recordStatementTx} and {@link
     * #recordStatementTxWithoutTransaction}. The two callers differ only in whether they open their
     * own transaction or reuse an ambient {@link TransactionStatus}; the balance lookup,
     * insufficient-funds check, and statement insert are otherwise identical.
     */
    
    
    
    
    public static void sendEmailOnUpdatingMerchantUserPassword(
            MerchantUser u, String password, NamedParameterJdbcTemplate jdbcTemplate) {
        // Now send verification email
        Setting emailContentManage =
                Common.getSettings("email_tmp_on_creating_merchant_user", jdbcTemplate);
        String emailContent_ = emailContentManage.getSetting_value().replace("{name}", u.getName());
        emailContent_ = emailContent_.replace("{url}", appBaseUrl);
        emailContent_ = emailContent_.replace("{merchant_number}", u.getMerchant_number());
        emailContent_ = emailContent_.replace("{username}", u.getEmail());
        final String emailContent = emailContent_.replace("{password}", password);

        final String subject = "Merchant User Credentials";
        final String to = u.getEmail();

        ManagedAsyncTasks.run(
                "sendEmailOnUpdatingMerchantUserPassword",
                () -> {
                    SendMail mail = new SendMail();
                    mail.sendSimpleMessage(to, subject, emailContent);
                });
    }

    /*
     * Returns true if the system is configured to use merchant-specific provider credentials.
     * When true, float_stock, suspense, and revenue account postings are skipped.
     */
    public static boolean useMerchantProviderCredentials(NamedParameterJdbcTemplate jdbcTemplate) {
        return SettingsRegistry.getBoolean("use_merchant_provider_credentials", jdbcTemplate);
    }

    /*
     * Returns gateway tracking accounts as [float_stock, suspense_stock, revenue_stock].
     * Returns [null, null, null] when useMerchantCreds is true (accounts not used).
     * Each element is assigned exactly once so callers can treat them as effectively final.
     */
    public static Merchant[] resolveGatewayAccounts(
            boolean useMerchantCreds, NamedParameterJdbcTemplate jdbcTemplate) {
        if (useMerchantCreds) return new Merchant[] {null, null, null};
        Setting stock = Common.getSettings("float_stock_account", jdbcTemplate);
        Setting suspense = Common.getSettings("suspense_account", jdbcTemplate);
        Setting revenue = Common.getSettings("revenue_account", jdbcTemplate);
        return new Merchant[] {
            Common.getMerchantByAccountNumber(stock.getSetting_value().trim(), jdbcTemplate),
            Common.getMerchantByAccountNumber(suspense.getSetting_value().trim(), jdbcTemplate),
            Common.getMerchantByAccountNumber(revenue.getSetting_value().trim(), jdbcTemplate)
        };
    }

    /*
     * Builds the API response for a retried request whose tx_merchant_ref already exists on this
     * merchant (audit D1). Legacy doPayIn/doPayOut previously rejected every duplicate reference with
     * a bare "already submitted" error, so a client retrying after a timeout could not tell its retry
     * apart from a real conflict and had no way to learn the original request's actual outcome. A
     * terminal transaction (SUCCESSFUL/FAILED) now replays its real outcome instead, mirroring v2's
     * IdempotencyService replay behavior; a still-PENDING transaction keeps returning the original
     * ambiguous rejection since there is no final outcome yet to hand back.
     */
    
    /*
     * Runs the same risk/fraud authorization the v2 orchestration path already runs (audit I1) -
     * blocklist, sanctions screening, single-transaction cap, daily merchant cap - against this
     * legacy request. Returns null when the request is allowed to proceed, or an error response
     * string when RiskDecisionRegistry declines it. A no-op (returns null) only if the registry
     * hasn't been wired by Spring (an uninitialized test context, not a real deployment).
     */
    
    /*
     * DoPayIn makes a payin transaction.
     */
    public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyMoneyMovementEngine.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);
    }

    /*
     * @Param skipRiskCheck: true when the caller (PaymentOrchestrationService) already ran
     * RiskDecisionService against this exact request before calling doPayIn - avoids evaluating
     * (and recording a second risk_decisions audit row for) the same request twice. Legacy direct
     * callers (Api.java, TransactionsLogController.java) never ran a risk check before, so they use
     * the 4-arg overload above, which always runs it.
     */
    public static String doPayIn(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {
        return LegacyMoneyMovementEngine.doPayIn(
                newTx, merchant, jdbcTemplate, transactionManager, skipRiskCheck);
    }

    /*
     * DoPayOut makes a payout transaction.
     */
    public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return LegacyMoneyMovementEngine.doPayOut(newTx, merchant, jdbcTemplate, transactionManager);
    }

    /*
     * @Param skipRiskCheck: see doPayIn's overload above - true only when the caller already ran
     * RiskDecisionService against this exact request itself (PaymentOrchestrationService).
     */
    public static String doPayOut(
            Transaction newTx,
            Merchant merchant,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            boolean skipRiskCheck) {
        return LegacyMoneyMovementEngine.doPayOut(
                newTx, merchant, jdbcTemplate, transactionManager, skipRiskCheck);
    }

    /*Returns a hash string*/
    public static String getSha256EncodedString(String originalString)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(originalString.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(encodedhash);
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static RowMapper<Transaction> getTransactionRowMapper() {
        RowMapper<Transaction> rm =
                (rs, rowNum) -> {
                    Transaction t = new Transaction();
                    t.setId(rs.getLong("id"));
                    t.setCharging_method(rs.getString("charging_method"));
                    t.setCharges(rs.getDouble("charges"));
                    t.setOriginal_amount(rs.getDouble("original_amount"));
                    t.setCreated_on(rs.getString("created_on"));
                    t.setUpdated_on(rs.getString("updated_on"));
                    t.setGateway_id(rs.getString("gateway_id"));
                    t.setStatus(rs.getString("status"));
                    t.setMerchant_id(rs.getString("merchant_id"));
                    t.setTx_description(rs.getString("tx_description"));
                    t.setTx_gateway_ref(rs.getString("tx_gateway_ref"));
                    t.setTx_merchant_description(rs.getString("tx_merchant_description"));
                    t.setTx_request_trace(rs.getString("tx_request_trace"));
                    t.setTx_unique_id(rs.getString("tx_unique_id"));
                    t.setTx_update_trace(rs.getString("tx_update_trace"));
                    t.setPayer_number(rs.getString("payer_number"));
                    t.setTx_type(rs.getString("tx_type"));
                    t.setCallback_trace(rs.getString("callback_trace"));
                    t.setTx_merchant_ref(rs.getString("tx_merchant_ref"));
                    t.setTx_cost(rs.getDouble("tx_cost"));
                    t.setCallback_url(rs.getString("callback_url"));
                    t.setSafaricomRequestReference(rs.getString("safaricom_request_reference"));
                    t.setOriginate_ip(rs.getString("originate_ip"));
                    t.setCurrency(rs.getString("currency"));
                    t.setCallback_status(rs.getString("callback_status"));
                    return t;
                };
        return rm;
    }

    public static String getExtensionByStringHandling(String filename) {
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            return filename.substring(i + 1);
        } else {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || !trustedProxyIps.contains(remoteAddr)) {
            // The direct TCP peer is not a configured trusted proxy: X-Forwarded-For/X-Real-IP
            // are client-supplied and spoofable, so they must be ignored entirely.
            return remoteAddr == null ? "" : remoteAddr;
        }
        String forwardedFor = firstHeaderValue(request.getHeader("X-Forwarded-For"));
        if (!forwardedFor.isEmpty()) {
            return forwardedFor;
        }
        String realIp = firstHeaderValue(request.getHeader("X-Real-IP"));
        if (!realIp.isEmpty()) {
            return realIp;
        }
        return remoteAddr;
    }

    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.trim().isEmpty()) {
            return "";
        }
        return headerValue.split(",")[0].trim();
    }

    public static String urlEncodeValue(String value) {
        return LEGACY_SUPPORT.urlEncodeValue(value);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        return java.math.BigDecimal.valueOf(value).setScale(places, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    public static void enqueueMerchantCallback(
            Transaction tx, Merchant merchant, NamedParameterJdbcTemplate jdbcTemplate) {
        if (tx == null
                || merchant == null
                || jdbcTemplate == null
                || tx.getCallback_url() == null
                || tx.getCallback_url().trim().isEmpty()) {
            return;
        }
        try {
            JSONObject requestBody = buildMerchantCallbackBody(tx, merchant);
            CallbackTaskRepository repository = new CallbackTaskRepository(jdbcTemplate);
            repository.enqueue(
                    merchant.getId(),
                    String.valueOf(tx.getId()),
                    tx.getTx_merchant_ref(),
                    tx.getCallback_url(),
                    requestBody.toString());

            jdbcTemplate.update(
                    "UPDATE "
                            + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " SET callback_status='QUEUED'"
                            + " WHERE id=:id AND (callback_status IS NULL OR callback_status IN ('PENDING','RETRY','FAILED'))",
                    new MapSqlParameterSource("id", tx.getId()));
        } catch (Exception ex) {
            Logger.getLogger(Common.class.getName())
                    .log(
                            Level.SEVERE,
                            "Unable to enqueue merchant callback for transaction " + tx.getId(),
                            ex);
            jdbcTemplate.update(
                    "UPDATE "
                            + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " SET callback_status='FAILED' WHERE id=:id AND callback_status != 'QUEUED'",
                    new MapSqlParameterSource("id", tx.getId()));
        }
    }

    static JSONObject buildMerchantCallbackBody(Transaction tx, Merchant merchant)
            throws Exception {
        String amountToSign = tx.getOriginal_amount() + "";
        String signedData =
                tx.getPayer_number()
                        + amountToSign
                        + tx.getCreated_on()
                        + tx.getTx_merchant_ref()
                        + tx.getStatus()
                        + tx.getTx_merchant_description()
                        + tx.getTx_gateway_ref();

        JSONObject body = new JSONObject();
        body.put("amount", amountToSign);
        body.put("payer_number", tx.getPayer_number());
        body.put("reference", tx.getTx_merchant_ref());
        body.put("network_ref", tx.getTx_gateway_ref());
        body.put("status", tx.getStatus());
        body.put("description", tx.getTx_merchant_description());
        body.put("completed_on", tx.getUpdated_on());
        body.put("created_on", tx.getCreated_on());
        body.put("currency", tx.getCurrency());
        body.put("SignedData", signedData);

        String signature = legacyCallbackSignature(merchant, signedData);
        if (!signature.isEmpty()) {
            body.put("signature", signature);
            body.put(
                    "signature_algorithm",
                    merchant.getHmac_secret() != null && !merchant.getHmac_secret().trim().isEmpty()
                            ? "HMAC-SHA256"
                            : "RSA-SHA256");
        }
        return body;
    }

    private static String legacyCallbackSignature(Merchant merchant, String signedData)
            throws Exception {
        if (merchant.getHmac_secret() != null && !merchant.getHmac_secret().trim().isEmpty()) {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(
                    new javax.crypto.spec.SecretKeySpec(
                            merchant.getHmac_secret().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return Base64.getEncoder()
                    .encodeToString(mac.doFinal(signedData.getBytes(StandardCharsets.UTF_8)));
        }
        if (merchant.getPrivate_key() == null || merchant.getPrivate_key().trim().isEmpty()) {
            return "";
        }
        Signature sign = Signature.getInstance("SHA256withRSA");
        String cleanedKey =
                merchant.getPrivate_key()
                        .replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        PrivateKey privateKey = Common.getPrivateKeyFromBase64String(cleanedKey);
        if (privateKey == null) {
            return "";
        }
        sign.initSign(privateKey);
        sign.update(signedData.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(sign.sign());
    }

    //
    public static String updateTx(
            Transaction tx,
            NamedParameterJdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        return TransactionResolutionEngine.updateTx(tx, jdbcTemplate, transactionManager);
    }

    }
