package net.citotech.cito.sharedprovider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCredentialService;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import net.citotech.cito.merchant.MerchantEnvironmentService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server-side credential-source decision for payment channels.
 *
 * Merchant-owned approved credentials always win. CPay/platform credentials are used only when an
 * ACTIVE entitlement exists for the exact merchant/channel/environment/country/currency/operation
 * scope. Secrets are decrypted only for execution and are never returned by admin read methods.
 */
@Service
public class SharedProviderAccessService {
    public static final String MERCHANT = "MERCHANT";
    public static final String PLATFORM_SHARED = "PLATFORM_SHARED";

    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantChannelCredentialService merchantCredentials;
    private final MerchantChannelCryptoService crypto;
    private final MerchantEnvironmentService environments;
    private final ObjectMapper objectMapper;

    public SharedProviderAccessService(
            NamedParameterJdbcTemplate jdbc,
            MerchantChannelCredentialService merchantCredentials,
            MerchantChannelCryptoService crypto,
            MerchantEnvironmentService environments,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.merchantCredentials = merchantCredentials;
        this.crypto = crypto;
        this.environments = environments;
        this.objectMapper = objectMapper;
    }

    public boolean isReady(
            Merchant merchant,
            String channelCode,
            String environment,
            String country,
            String currency,
            String operation,
            BigDecimal amount) {
        try {
            merchantCredentials.ensureChannelReady(merchant, channelCode, environment);
            return true;
        } catch (PaymentGatewayException ignored) {
            return findActiveEntitlement(merchant, channelCode, environment, country, currency, operation, amount) != null
                    && hasActivePlatformCredential(channelCode, environment, country, currency);
        }
    }

    /** Resolve the actual credential source. Call once, after routing has selected the adapter. */
    @Transactional
    public CredentialContext resolve(
            Merchant merchant,
            String channelCode,
            String environment,
            String country,
            String currency,
            String operation,
            BigDecimal amount) {
        requireMerchant(merchant);
        String env = environments.normalizedEnvironment(environment);
        try {
            merchantCredentials.ensureChannelReady(merchant, channelCode, env);
            return new CredentialContext(
                    MERCHANT,
                    merchantCredentials.loadDecrypted(merchant, channelCode, env),
                    null,
                    normalizeCountry(country),
                    normalizeCurrency(currency),
                    normalizeOperation(operation));
        } catch (PaymentGatewayException ignored) {
            // Deliberate fallback. A merchant without its own approved credentials must pass the
            // explicit shared-provider entitlement and platform-credential controls below.
        }

        Map<String, Object> entitlement =
                findActiveEntitlement(merchant, channelCode, env, country, currency, operation, amount);
        if (entitlement == null) {
            throw new PaymentGatewayException(
                    "Merchant has neither approved channel credentials nor an active CPay shared-provider entitlement for "
                            + channelCode);
        }
        Long entitlementId = number(entitlement.get("id"));
        consumeDailyLimit(entitlement, amount);
        Map<String, Object> credentials = loadPlatformCredential(channelCode, env, country, currency);
        return new CredentialContext(
                PLATFORM_SHARED,
                credentials,
                entitlementId,
                normalizeCountry(country),
                normalizeCurrency(currency),
                normalizeOperation(operation));
    }

    public List<Map<String, Object>> listEntitlements() {
        return jdbc.queryForList("SELECT id, merchant_id AS merchantId, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, operation, status, per_transaction_limit AS perTransactionLimit, daily_limit AS dailyLimit, requested_by AS requestedBy, requested_at AS requestedAt, approved_by AS approvedBy, approved_at AS approvedAt, notes FROM shared_provider_entitlements ORDER BY updated_at DESC", Map.of());
    }

    @Transactional
    public Map<String, Object> requestEntitlement(Map<String, Object> body, String actor) {
        String operation = normalizeOperation(text(body.get("operation")));
        BigDecimal perTx = decimalOrNull(body.get("perTransactionLimit"));
        BigDecimal daily = decimalOrNull(body.get("dailyLimit"));
        if (perTx != null && perTx.signum() <= 0) throw new PaymentGatewayException("perTransactionLimit must be positive");
        if (daily != null && daily.signum() <= 0) throw new PaymentGatewayException("dailyLimit must be positive");
        if (perTx != null && daily != null && perTx.compareTo(daily) > 0) {
            throw new PaymentGatewayException("perTransactionLimit cannot exceed dailyLimit");
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("merchant_id", requiredLong(body.get("merchantId"), "merchantId"))
                .addValue("channel_code", requiredText(body.get("channelCode"), "channelCode"))
                .addValue("environment", environments.normalizedEnvironment(requiredText(body.get("environment"), "environment")))
                .addValue("country", normalizeCountry(requiredText(body.get("countryCode"), "countryCode")))
                .addValue("currency", normalizeCurrency(requiredText(body.get("currencyCode"), "currencyCode")))
                .addValue("operation", operation)
                .addValue("per_tx", perTx)
                .addValue("daily", daily)
                .addValue("actor", requiredActor(actor))
                .addValue("notes", text(body.get("notes")));
        jdbc.update(
                "INSERT INTO shared_provider_entitlements (merchant_id, channel_code, environment, country_code, currency_code, operation, status, per_transaction_limit, daily_limit, requested_by, notes) "
                        + "VALUES (:merchant_id,:channel_code,:environment,:country,:currency,:operation,'PENDING',:per_tx,:daily,:actor,:notes) "
                        + "ON DUPLICATE KEY UPDATE status='PENDING', per_transaction_limit=:per_tx, daily_limit=:daily, requested_by=:actor, requested_at=CURRENT_TIMESTAMP(6), approved_by=NULL, approved_at=NULL, rejected_by=NULL, rejected_at=NULL, disabled_by=NULL, disabled_at=NULL, notes=:notes",
                p);
        return oneEntitlement(p);
    }

    @Transactional
    public Map<String, Object> approveEntitlement(long id, String actor) {
        String approver = requiredActor(actor);
        Map<String, Object> row = lockEntitlement(id);
        if (!"PENDING".equals(text(row.get("status")))) throw new PaymentGatewayException("Only PENDING entitlements can be approved");
        if (approver.equalsIgnoreCase(text(row.get("requested_by")))) {
            throw new PaymentGatewayException("Maker-checker violation: requester cannot approve the same entitlement");
        }
        jdbc.update(
                "UPDATE shared_provider_entitlements SET status='ACTIVE', approved_by=:actor, approved_at=CURRENT_TIMESTAMP(6) WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("actor", approver));
        return entitlementById(id);
    }

    @Transactional
    public Map<String, Object> rejectEntitlement(long id, String actor) {
        String checker = requiredActor(actor);
        Map<String, Object> row = lockEntitlement(id);
        if (!"PENDING".equals(text(row.get("status")))) throw new PaymentGatewayException("Only PENDING entitlements can be rejected");
        if (checker.equalsIgnoreCase(text(row.get("requested_by")))) {
            throw new PaymentGatewayException("Maker-checker violation: requester cannot reject the same entitlement");
        }
        jdbc.update(
                "UPDATE shared_provider_entitlements SET status='REJECTED', rejected_by=:actor, rejected_at=CURRENT_TIMESTAMP(6) WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("actor", checker));
        return entitlementById(id);
    }

    @Transactional
    public Map<String, Object> disableEntitlement(long id, String actor) {
        jdbc.update(
                "UPDATE shared_provider_entitlements SET status='DISABLED', disabled_by=:actor, disabled_at=CURRENT_TIMESTAMP(6) WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("actor", requiredActor(actor)));
        return entitlementById(id);
    }

    public List<Map<String, Object>> listPlatformCredentials() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, credential_mask AS credentialMask, status, created_by AS createdBy, updated_by AS updatedBy, approved_by AS approvedBy, approved_at AS approvedAt, updated_at AS updatedAt FROM platform_channel_credentials ORDER BY channel_code, environment, country_code, currency_code", Map.of());
        for (Map<String, Object> row : rows) {
            row.put("credentials", parseJson(text(row.remove("credentialMask"))));
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> savePlatformCredential(Map<String, Object> body, String actor) {
        Map<String, Object> credentials = map(body.get("credentials"));
        String channel = requiredText(body.get("channelCode"), "channelCode");
        String environment = environments.normalizedEnvironment(requiredText(body.get("environment"), "environment"));
        String country = normalizeCountry(requiredText(body.get("countryCode"), "countryCode"));
        String currency = normalizeCurrency(requiredText(body.get("currencyCode"), "currencyCode"));
        if ("PRODUCTION".equals(environment)) {
            requiredText(credentials.get("collectUrl"), "credentials.collectUrl");
            requiredText(credentials.get("payoutUrl"), "credentials.payoutUrl");
        }
        String who = requiredActor(actor);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("channel", channel)
                .addValue("environment", environment)
                .addValue("country", country)
                .addValue("currency", currency)
                .addValue("payload", crypto.encrypt(json(credentials)))
                .addValue("mask", json(mask(credentials)))
                .addValue("actor", who);
        jdbc.update(
                "INSERT INTO platform_channel_credentials (channel_code, environment, country_code, currency_code, credential_payload, credential_mask, status, created_by, updated_by) "
                        + "VALUES (:channel,:environment,:country,:currency,:payload,:mask,'CONFIGURED',:actor,:actor) "
                        + "ON DUPLICATE KEY UPDATE credential_payload=:payload, credential_mask=:mask, status='CONFIGURED', updated_by=:actor, approved_by=NULL, approved_at=NULL, disabled_by=NULL, disabled_at=NULL",
                p);
        return platformCredential(channel, environment, country, currency);
    }

    @Transactional
    public Map<String, Object> approvePlatformCredential(long id, String actor) {
        String approver = requiredActor(actor);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, updated_by FROM platform_channel_credentials WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource().addValue("id", id));
        if (rows.isEmpty()) throw new PaymentGatewayException("Platform credential not found");
        Map<String, Object> row = rows.get(0);
        if (!"CONFIGURED".equals(text(row.get("status")))) throw new PaymentGatewayException("Only CONFIGURED platform credentials can be approved");
        if (approver.equalsIgnoreCase(text(row.get("updated_by")))) {
            throw new PaymentGatewayException("Maker-checker violation: credential editor cannot approve the same credential");
        }
        jdbc.update(
                "UPDATE platform_channel_credentials SET status='ACTIVE', approved_by=:actor, approved_at=CURRENT_TIMESTAMP(6) WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("actor", approver));
        return platformCredentialById(id);
    }

    @Transactional
    public Map<String, Object> disablePlatformCredential(long id, String actor) {
        jdbc.update(
                "UPDATE platform_channel_credentials SET status='DISABLED', disabled_by=:actor, disabled_at=CURRENT_TIMESTAMP(6) WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("actor", requiredActor(actor)));
        return platformCredentialById(id);
    }

    private Map<String, Object> findActiveEntitlement(
            Merchant merchant,
            String channel,
            String environment,
            String country,
            String currency,
            String operation,
            BigDecimal amount) {
        requireMerchant(merchant);
        MapSqlParameterSource p = scope(merchant.getId(), channel, environment, country, currency, operation);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, status, per_transaction_limit, daily_limit, requested_by FROM shared_provider_entitlements "
                        + "WHERE merchant_id=:merchant_id AND channel_code=:channel AND environment=:environment AND country_code=:country AND currency_code=:currency AND operation=:operation AND status='ACTIVE' LIMIT 1",
                p);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        BigDecimal perTx = decimalOrNull(row.get("per_transaction_limit"));
        if (perTx != null && amount != null && amount.compareTo(perTx) > 0) return null;
        return row;
    }

    private boolean hasActivePlatformCredential(String channel, String environment, String country, String currency) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM platform_channel_credentials WHERE channel_code=:channel AND environment=:environment AND country_code=:country AND currency_code=:currency AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("channel", channel)
                        .addValue("environment", environments.normalizedEnvironment(environment))
                        .addValue("country", normalizeCountry(country))
                        .addValue("currency", normalizeCurrency(currency)),
                Integer.class);
        return count != null && count > 0;
    }

    private Map<String, Object> loadPlatformCredential(String channel, String environment, String country, String currency) {
        List<String> rows = jdbc.query(
                "SELECT credential_payload FROM platform_channel_credentials WHERE channel_code=:channel AND environment=:environment AND country_code=:country AND currency_code=:currency AND status='ACTIVE' LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("channel", channel)
                        .addValue("environment", environments.normalizedEnvironment(environment))
                        .addValue("country", normalizeCountry(country))
                        .addValue("currency", normalizeCurrency(currency)),
                (rs, i) -> rs.getString(1));
        if (rows.isEmpty()) throw new PaymentGatewayException("No approved CPay platform credential is configured for " + channel);
        return parseJson(crypto.decrypt(rows.get(0)));
    }

    private void consumeDailyLimit(Map<String, Object> entitlement, BigDecimal amount) {
        BigDecimal dailyLimit = decimalOrNull(entitlement.get("daily_limit"));
        if (dailyLimit == null || amount == null) return;
        Long entitlementId = number(entitlement.get("id"));
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", entitlementId)
                .addValue("day", LocalDate.now())
                .addValue("operation", "AUTHORIZED")
                .addValue("amount", amount);
        jdbc.update(
                "INSERT IGNORE INTO shared_provider_daily_usage (entitlement_id, usage_date, operation, approved_amount, transaction_count) VALUES (:id,:day,:operation,0,0)",
                p);
        List<Map<String, Object>> usage = jdbc.queryForList(
                "SELECT id, approved_amount FROM shared_provider_daily_usage WHERE entitlement_id=:id AND usage_date=:day AND operation=:operation FOR UPDATE",
                p);
        BigDecimal used = usage.isEmpty() ? BigDecimal.ZERO : decimal(usage.get(0).get("approved_amount"));
        BigDecimal proposed = used.add(amount);
        if (proposed.compareTo(dailyLimit) > 0) {
            throw new PaymentGatewayException("CPay shared-provider daily limit exceeded");
        }
        p.addValue("proposed", proposed);
        jdbc.update(
                "UPDATE shared_provider_daily_usage SET approved_amount=:proposed, transaction_count=transaction_count+1 WHERE entitlement_id=:id AND usage_date=:day AND operation=:operation",
                p);
    }

    private MapSqlParameterSource scope(Long merchantId, String channel, String environment, String country, String currency, String operation) {
        return new MapSqlParameterSource()
                .addValue("merchant_id", merchantId)
                .addValue("channel", channel)
                .addValue("environment", environments.normalizedEnvironment(environment))
                .addValue("country", normalizeCountry(country))
                .addValue("currency", normalizeCurrency(currency))
                .addValue("operation", normalizeOperation(operation));
    }

    private Map<String, Object> oneEntitlement(MapSqlParameterSource p) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, merchant_id AS merchantId, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, operation, status, per_transaction_limit AS perTransactionLimit, daily_limit AS dailyLimit, requested_by AS requestedBy, requested_at AS requestedAt, approved_by AS approvedBy, approved_at AS approvedAt, notes FROM shared_provider_entitlements WHERE merchant_id=:merchant_id AND channel_code=:channel_code AND environment=:environment AND country_code=:country AND currency_code=:currency AND operation=:operation LIMIT 1",
                p);
        if (rows.isEmpty()) throw new PaymentGatewayException("Entitlement was not saved");
        return rows.get(0);
    }

    private Map<String, Object> lockEntitlement(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM shared_provider_entitlements WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource().addValue("id", id));
        if (rows.isEmpty()) throw new PaymentGatewayException("Shared-provider entitlement not found");
        return rows.get(0);
    }

    private Map<String, Object> entitlementById(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, merchant_id AS merchantId, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, operation, status, per_transaction_limit AS perTransactionLimit, daily_limit AS dailyLimit, requested_by AS requestedBy, requested_at AS requestedAt, approved_by AS approvedBy, approved_at AS approvedAt, notes FROM shared_provider_entitlements WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id));
        if (rows.isEmpty()) throw new PaymentGatewayException("Shared-provider entitlement not found");
        return rows.get(0);
    }

    private Map<String, Object> platformCredential(String channel, String environment, String country, String currency) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, credential_mask AS credentialMask, status, created_by AS createdBy, updated_by AS updatedBy, approved_by AS approvedBy, approved_at AS approvedAt FROM platform_channel_credentials WHERE channel_code=:channel AND environment=:environment AND country_code=:country AND currency_code=:currency LIMIT 1",
                new MapSqlParameterSource().addValue("channel", channel).addValue("environment", environment).addValue("country", country).addValue("currency", currency));
        if (rows.isEmpty()) throw new PaymentGatewayException("Platform credential not found");
        return safeCredential(rows.get(0));
    }

    private Map<String, Object> platformCredentialById(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, channel_code AS channelCode, environment, country_code AS countryCode, currency_code AS currencyCode, credential_mask AS credentialMask, status, created_by AS createdBy, updated_by AS updatedBy, approved_by AS approvedBy, approved_at AS approvedAt FROM platform_channel_credentials WHERE id=:id",
                new MapSqlParameterSource().addValue("id", id));
        if (rows.isEmpty()) throw new PaymentGatewayException("Platform credential not found");
        return safeCredential(rows.get(0));
    }

    private Map<String, Object> safeCredential(Map<String, Object> row) {
        Map<String, Object> safe = new LinkedHashMap<>(row);
        safe.put("credentials", parseJson(text(safe.remove("credentialMask"))));
        return safe;
    }

    private Map<String, Object> mask(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = text(entry.getValue());
            if (key.toLowerCase(Locale.ROOT).contains("url")) result.put(key, value);
            else if (value.length() <= 4) result.put(key, "****");
            else result.put(key, value.substring(0, 2) + "****" + value.substring(value.length() - 2));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?>) return new LinkedHashMap<>((Map<String, Object>) value);
        throw new PaymentGatewayException("credentials object is required");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (Exception e) { throw new PaymentGatewayException("Unable to read credential configuration"); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception e) { throw new PaymentGatewayException("Unable to encode credential configuration"); }
    }

    private void requireMerchant(Merchant merchant) {
        if (merchant == null || merchant.getId() == null) throw new PaymentGatewayException("Merchant is required");
    }

    private String normalizeCountry(String value) { return requiredText(value, "country").toUpperCase(Locale.ROOT); }
    private String normalizeCurrency(String value) { return requiredText(value, "currency").toUpperCase(Locale.ROOT); }
    private String normalizeOperation(String value) {
        String operation = requiredText(value, "operation").toUpperCase(Locale.ROOT);
        if (!operation.equals("COLLECT") && !operation.equals("PAYOUT")) throw new PaymentGatewayException("operation must be COLLECT or PAYOUT");
        return operation;
    }
    private String requiredActor(String actor) { return requiredText(actor, "actor"); }
    private String requiredText(Object value, String field) {
        String v = text(value);
        if (v.isEmpty()) throw new PaymentGatewayException(field + " is required");
        return v;
    }
    private long requiredLong(Object value, String field) {
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(requiredText(value, field)); }
        catch (NumberFormatException e) { throw new PaymentGatewayException(field + " must be a number"); }
    }
    private Long number(Object value) { return value instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(value)); }
    private BigDecimal decimal(Object value) { return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value)); }
    private BigDecimal decimalOrNull(Object value) {
        if (value == null || text(value).isEmpty()) return null;
        try { return decimal(value); }
        catch (Exception e) { throw new PaymentGatewayException("Invalid monetary limit"); }
    }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    public record CredentialContext(
            String source,
            Map<String, Object> credentials,
            Long entitlementId,
            String countryCode,
            String currencyCode,
            String operation) {
        public boolean shared() { return PLATFORM_SHARED.equals(source); }
    }
}
