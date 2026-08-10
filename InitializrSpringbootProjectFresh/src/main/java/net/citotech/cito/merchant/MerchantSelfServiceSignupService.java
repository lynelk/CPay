package net.citotech.cito.merchant;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.KeyPairStrings;
import net.citotech.cito.compliance.ComplianceCaseService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantSelfServiceSignupService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ComplianceCaseService complianceCaseService;
    private final MerchantEmailVerificationService emailVerificationService;

    @Autowired
    public MerchantSelfServiceSignupService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ComplianceCaseService complianceCaseService,
            MerchantEmailVerificationService emailVerificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.complianceCaseService = complianceCaseService;
        this.emailVerificationService = emailVerificationService;
    }

    public MerchantSelfServiceSignupService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.complianceCaseService = null;
        this.emailVerificationService = null;
    }

    @Transactional
    public Map<String, Object> signup(Map<String, Object> body) {
        String businessName = required(body, "businessName");
        String shortName = required(body, "shortName");
        String accountType = normalizeAccountType(value(body, "accountType", "business"));
        String contactName = required(body, "contactName");
        String email = required(body, "email").toLowerCase();
        String phone = required(body, "phone");
        String password = required(body, "password");
        ensureEmailNotRegistered(email);
        String accountNumber = generateMerchantNumber();
        KeyPairStrings keys = Common.generateKeyPair();

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("name", businessName);
        p.addValue("account_number", accountNumber);
        p.addValue("created_by", "SELF_SERVICE");
        p.addValue("status", "PENDING_APPROVAL");
        p.addValue("short_name", shortName);
        p.addValue(
                "allowed_apis",
                Common.API_MOBILE_MONEY_PAYIN
                        + ","
                        + Common.API_MOBILE_MONEY_PAYOUT
                        + ","
                        + Common.API_TRANSACTION_CHECKSTATUS
                        + ","
                        + Common.API_BALANCE_CHECK
                        + ","
                        + Common.API_SEND_SMS);
        p.addValue("account_type", accountType);
        p.addValue("public_key", keys.getPublic_key());
        p.addValue("private_key", MerchantKeyCryptoRegistry.encryptForStorage(keys.getPrivate_key()));
        KeyHolder merchantKey = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO merchants SET name=:name, account_number=:account_number, created_by=:created_by, status=:status, short_name=:short_name, allowed_apis=:allowed_apis, account_type=:account_type, public_key=:public_key, private_key=:private_key",
                p,
                merchantKey);
        BigInteger merchantId = generatedKey(merchantKey);

        MapSqlParameterSource admin = new MapSqlParameterSource();
        admin.addValue("merchant_id", merchantId);
        admin.addValue("name", contactName);
        admin.addValue("email", email);
        admin.addValue("phone", phone);
        admin.addValue("password", PasswordUtils.hashPassword(password));
        admin.addValue("status", "ACTIVE");
        // The self-registering account creator is explicitly the OWNER. No privilege rows are
        // created: MerchantRole is the sole merchant-user authorization source.
        admin.addValue("role", MerchantRole.OWNER.name());
        KeyHolder adminKey = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO merchant_admins SET merchant_id=:merchant_id, name=:name, email=:email, phone=:phone, password=:password, status=:status, role=:role",
                admin,
                adminKey);
        BigInteger adminId = generatedKey(adminKey);

        if (complianceCaseService != null) {
            complianceCaseService.upsertProfile(
                    "MERCHANT",
                    merchantId.longValue(),
                    "KYB",
                    "STANDARD",
                    "PENDING",
                    "UNKNOWN",
                    "[\"business_registration\",\"tax_identification\",\"beneficial_owners\"]",
                    "Self-service signup requires KYB review before production activation.",
                    null);
        }
        if (emailVerificationService != null) {
            emailVerificationService.sendVerificationEmail(adminId.longValue(), email, contactName);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "000");
        response.put(
                "message",
                "Merchant registration submitted successfully. The account is pending approval before production use.");
        response.put("accountNumber", accountNumber);
        response.put("merchantStatus", "PENDING_APPROVAL");
        response.put("publicKey", keys.getPublic_key());
        return response;
    }

    private BigInteger generatedKey(KeyHolder keyHolder) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new PaymentGatewayException("Database insert did not return a generated id");
        }
        if (key instanceof BigInteger bigInteger) return bigInteger;
        return BigInteger.valueOf(key.longValue());
    }

    private void ensureEmailNotRegistered(String email) {
        MapSqlParameterSource p = new MapSqlParameterSource("email", email);
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchant_admins WHERE email=:email", p, Integer.class);
        if (count != null && count > 0) {
            throw new PaymentGatewayException("This email is already registered");
        }
    }

    private String generateMerchantNumber() {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchants", new MapSqlParameterSource(), Integer.class);
        int base = 1000000 + (count == null ? 0 : count);
        while (exists(String.valueOf(base))) base++;
        return String.valueOf(base);
    }

    private boolean exists(String accountNumber) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchants WHERE account_number=:account_number",
                        new MapSqlParameterSource("account_number", accountNumber),
                        Integer.class);
        return count != null && count > 0;
    }

    private String required(Map<String, Object> body, String key) {
        String value = value(body, key, "");
        if (value.isEmpty()) throw new PaymentGatewayException(key + " is required");
        return value;
    }

    private String value(Map<String, Object> body, String key, String fallback) {
        Object value = body == null ? null : body.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private String normalizeAccountType(String accountType) {
        String normalized =
                value(Map.of("accountType", accountType), "accountType", "business")
                        .toLowerCase(Locale.ROOT);
        if (!"business".equals(normalized) && !"personal".equals(normalized)) {
            throw new PaymentGatewayException("accountType must be business or personal");
        }
        return normalized;
    }
}
