package net.citotech.cito.virtualaccount;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VirtualAccountService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;

    public VirtualAccountService(
            NamedParameterJdbcTemplate jdbcTemplate, CitoEntitlementService entitlementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
    }

    @Transactional
    public Map<String, Object> issue(
            long merchantId,
            String environment,
            String countryCode,
            String currencyCode,
            String accountType,
            String accountName,
            String customerReference,
            String purposeReference,
            Instant expiresAt,
            String actor) {
        requireMerchant(merchantId);
        String env = environment(environment);
        entitlementService.requireEntitlement(merchantId, "VIRTUAL_ACCOUNTS", env);
        String country = country(countryCode);
        String currency = currency(currencyCode);
        String type = accountType == null || accountType.isBlank()
                ? "TEMPORARY"
                : accountType.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("TEMPORARY", "PERMANENT").contains(type)) {
            throw new PaymentGatewayException("accountType must be TEMPORARY or PERMANENT");
        }
        if ("TEMPORARY".equals(type) && expiresAt == null) {
            expiresAt = Instant.now().plusSeconds(7L * 24 * 3600);
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new PaymentGatewayException("expiresAt must be in the future");
        }
        Provider provider = provider(country, currency, env);
        if ("PRODUCTION".equals(env)
                && (!provider.certified() || !"ACTIVE".equals(provider.status()))) {
            throw new PaymentGatewayException(
                    "Production virtual-account issuance requires an active certified provider connector");
        }
        String reference = reference("VA");
        String accountNumber;
        String bankCode;
        String bankName;
        if ("SANDBOX".equals(env) && "CITO_SANDBOX".equals(provider.providerCode())) {
            accountNumber = sandboxAccountNumber(merchantId, reference);
            bankCode = "CITO-SBX";
            bankName = "Cito Sandbox Bank";
        } else {
            throw new PaymentGatewayException(
                    "The certified production provider connector must issue the external account before Cito can persist it");
        }
        jdbcTemplate.update(
                "INSERT INTO virtual_accounts "
                        + "(merchant_id, account_reference, provider_id, environment, account_type, account_name, account_number, "
                        + "bank_code, bank_name, customer_reference, purpose_reference, status, expires_at, created_by) "
                        + "VALUES (:merchant_id, :reference, :provider_id, :environment, :account_type, :account_name, :account_number, "
                        + ":bank_code, :bank_name, :customer_reference, :purpose_reference, 'ACTIVE', :expires_at, :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("provider_id", provider.id())
                        .addValue("environment", env)
                        .addValue("account_type", type)
                        .addValue("account_name", required(accountName, "accountName"))
                        .addValue("account_number", accountNumber)
                        .addValue("bank_code", bankCode)
                        .addValue("bank_name", bankName)
                        .addValue("customer_reference", blankToNull(customerReference))
                        .addValue("purpose_reference", blankToNull(purposeReference))
                        .addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt))
                        .addValue("created_by", blankToNull(actor)));
        return account(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> registerExternalProductionAccount(
            long merchantId,
            String providerCode,
            String countryCode,
            String currencyCode,
            String accountType,
            String accountName,
            String accountNumber,
            String bankCode,
            String bankName,
            String customerReference,
            String purposeReference,
            Instant expiresAt,
            String actor) {
        entitlementService.requireEntitlement(merchantId, "VIRTUAL_ACCOUNTS", "PRODUCTION");
        Provider provider = providerByCode(providerCode, country(countryCode), currency(currencyCode), "PRODUCTION");
        if (!provider.certified() || !"ACTIVE".equals(provider.status())) {
            throw new PaymentGatewayException("Virtual-account provider is not certified and active");
        }
        String reference = reference("VA");
        jdbcTemplate.update(
                "INSERT INTO virtual_accounts "
                        + "(merchant_id, account_reference, provider_id, environment, account_type, account_name, account_number, bank_code, bank_name, "
                        + "customer_reference, purpose_reference, status, expires_at, created_by) "
                        + "VALUES (:merchant_id, :reference, :provider_id, 'PRODUCTION', :account_type, :account_name, :account_number, :bank_code, :bank_name, "
                        + ":customer_reference, :purpose_reference, 'ACTIVE', :expires_at, :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("provider_id", provider.id())
                        .addValue("account_type", required(accountType, "accountType").toUpperCase(Locale.ROOT))
                        .addValue("account_name", required(accountName, "accountName"))
                        .addValue("account_number", required(accountNumber, "accountNumber"))
                        .addValue("bank_code", blankToNull(bankCode))
                        .addValue("bank_name", blankToNull(bankName))
                        .addValue("customer_reference", blankToNull(customerReference))
                        .addValue("purpose_reference", blankToNull(purposeReference))
                        .addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt))
                        .addValue("created_by", blankToNull(actor)));
        return account(merchantId, reference);
    }

    public List<Map<String, Object>> accounts(long merchantId, String environment) {
        String env = environment(environment);
        return jdbcTemplate.queryForList(
                "SELECT a.account_reference AS accountReference, a.environment, a.account_type AS accountType, a.account_name AS accountName, "
                        + "a.account_number AS accountNumber, a.bank_code AS bankCode, a.bank_name AS bankName, "
                        + "a.customer_reference AS customerReference, a.purpose_reference AS purposeReference, a.status, a.expires_at AS expiresAt, "
                        + "p.provider_code AS providerCode, p.provider_name AS providerName, a.created_at AS createdAt "
                        + "FROM virtual_accounts a JOIN virtual_account_providers p ON p.id=a.provider_id "
                        + "WHERE a.merchant_id=:merchant_id AND a.environment=:environment ORDER BY a.id DESC",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("environment", env));
    }

    @Transactional
    public Map<String, Object> close(long merchantId, String accountReference, String actor) {
        int updated = jdbcTemplate.update(
                "UPDATE virtual_accounts SET status='CLOSED', closed_at=CURRENT_TIMESTAMP "
                        + "WHERE merchant_id=:merchant_id AND account_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(accountReference, "accountReference")));
        if (updated == 0) {
            throw new PaymentGatewayException("Active virtual account was not found");
        }
        return account(merchantId, accountReference);
    }

    @Transactional
    public Map<String, Object> recordIncomingTransfer(
            String providerCode,
            String countryCode,
            String currencyCode,
            String environment,
            String accountNumber,
            String providerTransferReference,
            BigDecimal amount,
            String senderName,
            String senderReference,
            String narration) {
        String env = environment(environment);
        Provider provider = providerByCode(providerCode, country(countryCode), currency(currencyCode), env);
        if (!provider.certified() || !"ACTIVE".equals(provider.status())) {
            throw new PaymentGatewayException("Virtual-account provider is not active");
        }
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(
                "SELECT id, merchant_id, account_reference, purpose_reference FROM virtual_accounts "
                        + "WHERE provider_id=:provider_id AND account_number=:account_number AND environment=:environment "
                        + "AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)",
                new MapSqlParameterSource()
                        .addValue("provider_id", provider.id())
                        .addValue("account_number", required(accountNumber, "accountNumber"))
                        .addValue("environment", env));
        if (accounts.isEmpty()) {
            throw new PaymentGatewayException("Active virtual account was not found");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("amount must be greater than zero");
        }
        Map<String, Object> account = accounts.get(0);
        String reference = reference("VATX");
        jdbcTemplate.update(
                "INSERT INTO virtual_account_incoming_transfers "
                        + "(transfer_reference, provider_id, virtual_account_id, provider_transfer_reference, amount, currency_code, sender_name, "
                        + "sender_reference, narration, status) VALUES (:reference, :provider_id, :virtual_account_id, :provider_transfer_reference, "
                        + ":amount, :currency_code, :sender_name, :sender_reference, :narration, 'RECEIVED')",
                new MapSqlParameterSource()
                        .addValue("reference", reference)
                        .addValue("provider_id", provider.id())
                        .addValue("virtual_account_id", account.get("id"))
                        .addValue("provider_transfer_reference", blankToNull(providerTransferReference))
                        .addValue("amount", amount)
                        .addValue("currency_code", provider.currencyCode())
                        .addValue("sender_name", blankToNull(senderName))
                        .addValue("sender_reference", blankToNull(senderReference))
                        .addValue("narration", blankToNull(narration)));
        Long transferId = jdbcTemplate.queryForObject(
                "SELECT id FROM virtual_account_incoming_transfers WHERE transfer_reference=:reference",
                new MapSqlParameterSource("reference", reference), Long.class);
        if (transferId == null) {
            throw new PaymentGatewayException("Unable to persist incoming transfer");
        }
        String purposeReference = account.get("purpose_reference") == null ? null : String.valueOf(account.get("purpose_reference"));
        if (purposeReference != null && !purposeReference.isBlank()) {
            jdbcTemplate.update(
                    "INSERT INTO virtual_account_matches "
                            + "(transfer_id, merchant_id, transaction_reference, match_type, status, matched_by) "
                            + "VALUES (:transfer_id, :merchant_id, :transaction_reference, 'PURPOSE_REFERENCE', 'MATCHED', 'SYSTEM')",
                    new MapSqlParameterSource()
                            .addValue("transfer_id", transferId)
                            .addValue("merchant_id", account.get("merchant_id"))
                            .addValue("transaction_reference", purposeReference));
            jdbcTemplate.update(
                    "UPDATE virtual_account_incoming_transfers SET status='MATCHED', processed_at=CURRENT_TIMESTAMP WHERE id=:id",
                    new MapSqlParameterSource("id", transferId));
        }
        return transfer(reference);
    }

    public List<Map<String, Object>> transfers(long merchantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT t.transfer_reference AS transferReference, a.account_reference AS accountReference, t.provider_transfer_reference AS providerTransferReference, "
                        + "t.amount, t.currency_code AS currencyCode, t.sender_name AS senderName, t.sender_reference AS senderReference, t.narration, "
                        + "t.status, t.received_at AS receivedAt, t.processed_at AS processedAt FROM virtual_account_incoming_transfers t "
                        + "JOIN virtual_accounts a ON a.id=t.virtual_account_id WHERE a.merchant_id=:merchant_id ORDER BY t.id DESC LIMIT " + safeLimit,
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public Map<String, Object> configureProvider(
            String providerCode,
            String providerName,
            String countryCode,
            String currencyCode,
            String environment,
            String providerType,
            String connectorReference,
            boolean certified,
            boolean active,
            String actor) {
        String env = environment(environment);
        jdbcTemplate.update(
                "INSERT INTO virtual_account_providers "
                        + "(provider_code, provider_name, country_code, currency_code, environment, provider_type, connector_reference, certified, status, activated_by, activated_at) "
                        + "VALUES (:provider_code, :provider_name, :country_code, :currency_code, :environment, :provider_type, :connector_reference, "
                        + ":certified, :status, :actor, CASE WHEN :status='ACTIVE' THEN CURRENT_TIMESTAMP ELSE NULL END) "
                        + "ON DUPLICATE KEY UPDATE provider_name=VALUES(provider_name), provider_type=VALUES(provider_type), connector_reference=VALUES(connector_reference), "
                        + "certified=VALUES(certified), status=VALUES(status), activated_by=VALUES(activated_by), activated_at=VALUES(activated_at), updated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("provider_code", required(providerCode, "providerCode").toUpperCase(Locale.ROOT))
                        .addValue("provider_name", required(providerName, "providerName"))
                        .addValue("country_code", country(countryCode))
                        .addValue("currency_code", currency(currencyCode))
                        .addValue("environment", env)
                        .addValue("provider_type", required(providerType, "providerType").toUpperCase(Locale.ROOT))
                        .addValue("connector_reference", blankToNull(connectorReference))
                        .addValue("certified", certified ? "YES" : "NO")
                        .addValue("status", active ? "ACTIVE" : "INACTIVE")
                        .addValue("actor", required(actor, "actor")));
        return Map.of("providerCode", providerCode, "environment", env, "certified", certified, "active", active);
    }

    private Provider provider(String country, String currency, String environment) {
        List<Provider> rows = jdbcTemplate.query(
                "SELECT id, provider_code, currency_code, certified, status FROM virtual_account_providers "
                        + "WHERE country_code=:country_code AND currency_code=:currency_code AND environment=:environment AND status='ACTIVE' "
                        + "ORDER BY certified DESC, id LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("country_code", country)
                        .addValue("currency_code", currency)
                        .addValue("environment", environment),
                (rs, rowNum) -> new Provider(
                        rs.getLong("id"),
                        rs.getString("provider_code"),
                        rs.getString("currency_code"),
                        "YES".equalsIgnoreCase(rs.getString("certified")),
                        rs.getString("status")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("No active virtual-account provider is configured for this corridor");
        }
        return rows.get(0);
    }

    private Provider providerByCode(String providerCode, String country, String currency, String environment) {
        List<Provider> rows = jdbcTemplate.query(
                "SELECT id, provider_code, currency_code, certified, status FROM virtual_account_providers "
                        + "WHERE provider_code=:provider_code AND country_code=:country_code AND currency_code=:currency_code AND environment=:environment",
                new MapSqlParameterSource()
                        .addValue("provider_code", required(providerCode, "providerCode").toUpperCase(Locale.ROOT))
                        .addValue("country_code", country)
                        .addValue("currency_code", currency)
                        .addValue("environment", environment),
                (rs, rowNum) -> new Provider(
                        rs.getLong("id"),
                        rs.getString("provider_code"),
                        rs.getString("currency_code"),
                        "YES".equalsIgnoreCase(rs.getString("certified")),
                        rs.getString("status")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Virtual-account provider was not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> account(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT a.account_reference AS accountReference, a.environment, a.account_type AS accountType, a.account_name AS accountName, "
                        + "a.account_number AS accountNumber, a.bank_code AS bankCode, a.bank_name AS bankName, a.customer_reference AS customerReference, "
                        + "a.purpose_reference AS purposeReference, a.status, a.expires_at AS expiresAt, p.provider_code AS providerCode, p.provider_name AS providerName, "
                        + "a.created_at AS createdAt FROM virtual_accounts a JOIN virtual_account_providers p ON p.id=a.provider_id "
                        + "WHERE a.merchant_id=:merchant_id AND a.account_reference=:reference",
                new MapSqlParameterSource().addValue("merchant_id", merchantId).addValue("reference", reference));
    }

    private Map<String, Object> transfer(String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT transfer_reference AS transferReference, provider_transfer_reference AS providerTransferReference, amount, currency_code AS currencyCode, "
                        + "sender_name AS senderName, sender_reference AS senderReference, narration, status, received_at AS receivedAt, processed_at AS processedAt "
                        + "FROM virtual_account_incoming_transfers WHERE transfer_reference=:reference",
                new MapSqlParameterSource("reference", reference));
    }

    private String sandboxAccountNumber(long merchantId, String reference) {
        long hash = Integer.toUnsignedLong(reference.hashCode());
        return "99" + String.format(Locale.ROOT, "%06d", merchantId % 1_000_000) + String.format(Locale.ROOT, "%06d", hash % 1_000_000);
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String environment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private String country(String value) {
        String normalized = required(value, "countryCode").toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new PaymentGatewayException("countryCode must use ISO country format");
        }
        return normalized;
    }

    private String currency(String value) {
        String normalized = required(value, "currencyCode").toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new PaymentGatewayException("currencyCode must use ISO-4217 format");
        }
        return normalized;
    }

    private void requireMerchant(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private record Provider(long id, String providerCode, String currencyCode, boolean certified, String status) {}
}