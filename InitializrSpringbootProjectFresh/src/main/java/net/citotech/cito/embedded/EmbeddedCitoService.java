package net.citotech.cito.embedded;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citotech.cito.Common;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddedCitoService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;

    public EmbeddedCitoService(
            NamedParameterJdbcTemplate jdbcTemplate, CitoEntitlementService entitlementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
    }

    @Transactional
    public Map<String, Object> ensurePartner(long merchantId, String partnerName, String actor) {
        entitlementService.requireEntitlement(merchantId, "EMBEDDED_CITO", "SANDBOX");
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT partner_reference AS partnerReference, partner_name AS partnerName, status, created_at AS createdAt "
                        + "FROM embedded_partners WHERE merchant_id=:merchant_id",
                new MapSqlParameterSource("merchant_id", merchantId));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String reference = reference("PARTNER");
        jdbcTemplate.update(
                "INSERT INTO embedded_partners (merchant_id, partner_reference, partner_name, status, created_by) "
                        + "VALUES (:merchant_id, :reference, :partner_name, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("partner_name", required(partnerName, "partnerName"))
                        .addValue("created_by", blankToNull(actor)));
        return partner(merchantId);
    }

    @Transactional
    public Map<String, Object> saveBrand(
            long merchantId,
            String brandName,
            String logoUrl,
            String primaryColor,
            String supportEmail,
            String customDomain,
            String termsUrl,
            String privacyUrl,
            String actor) {
        long partnerId = partnerId(merchantId);
        validateHexColor(primaryColor);
        jdbcTemplate.update(
                "INSERT INTO embedded_brand_profiles "
                        + "(partner_id, brand_name, logo_url, primary_color, support_email, custom_domain, terms_url, privacy_url, status, updated_by) "
                        + "VALUES (:partner_id, :brand_name, :logo_url, :primary_color, :support_email, :custom_domain, :terms_url, :privacy_url, 'ACTIVE', :updated_by) "
                        + "ON DUPLICATE KEY UPDATE brand_name=VALUES(brand_name), logo_url=VALUES(logo_url), primary_color=VALUES(primary_color), "
                        + "support_email=VALUES(support_email), custom_domain=VALUES(custom_domain), terms_url=VALUES(terms_url), "
                        + "privacy_url=VALUES(privacy_url), status='ACTIVE', updated_by=VALUES(updated_by), updated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("brand_name", required(brandName, "brandName"))
                        .addValue("logo_url", blankToNull(logoUrl))
                        .addValue("primary_color", blankToNull(primaryColor))
                        .addValue("support_email", blankToNull(supportEmail))
                        .addValue("custom_domain", blankToNull(customDomain))
                        .addValue("terms_url", blankToNull(termsUrl))
                        .addValue("privacy_url", blankToNull(privacyUrl))
                        .addValue("updated_by", blankToNull(actor)));
        return brand(partnerId);
    }

    @Transactional
    public Map<String, Object> createOnboardingSession(
            long merchantId,
            String intendedEmail,
            List<String> serviceCodes,
            String returnUrl,
            Instant expiresAt,
            String actor) {
        long partnerId = partnerId(merchantId);
        Instant expiry = expiresAt == null ? Instant.now().plusSeconds(3600) : expiresAt;
        if (!expiry.isAfter(Instant.now()) || expiry.isAfter(Instant.now().plusSeconds(24 * 3600))) {
            throw new PaymentGatewayException("Embedded onboarding sessions must expire within 24 hours");
        }
        List<String> services = normalizeServices(merchantId, serviceCodes, "SANDBOX");
        String token = "emb_" + Common.randomUrlSafeToken(32);
        String sessionReference = reference("EMBSESS");
        jdbcTemplate.update(
                "INSERT INTO embedded_onboarding_sessions "
                        + "(partner_id, session_reference, token_hash, token_prefix, intended_email, intended_service_codes_json, return_url, status, expires_at, created_by) "
                        + "VALUES (:partner_id, :session_reference, :token_hash, :token_prefix, :intended_email, :services_json, :return_url, 'ACTIVE', :expires_at, :created_by)",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("session_reference", sessionReference)
                        .addValue("token_hash", sha256(token))
                        .addValue("token_prefix", token.substring(0, Math.min(20, token.length())))
                        .addValue("intended_email", blankToNull(intendedEmail))
                        .addValue("services_json", jsonArray(services))
                        .addValue("return_url", blankToNull(returnUrl))
                        .addValue("expires_at", Timestamp.from(expiry))
                        .addValue("created_by", blankToNull(actor)));
        return Map.of(
                "sessionReference", sessionReference,
                "token", token,
                "expiresAt", expiry.toString(),
                "displayOnce", true,
                "services", services);
    }

    public Map<String, Object> resolvePublicSession(String token) {
        String tokenHash = sha256(required(token, "token"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.session_reference AS sessionReference, s.intended_email AS intendedEmail, "
                        + "s.intended_service_codes_json AS intendedServiceCodes, s.return_url AS returnUrl, s.expires_at AS expiresAt, "
                        + "p.partner_reference AS partnerReference, p.partner_name AS partnerName, b.brand_name AS brandName, b.logo_url AS logoUrl, "
                        + "b.primary_color AS primaryColor, b.support_email AS supportEmail, b.terms_url AS termsUrl, b.privacy_url AS privacyUrl "
                        + "FROM embedded_onboarding_sessions s JOIN embedded_partners p ON p.id=s.partner_id "
                        + "LEFT JOIN embedded_brand_profiles b ON b.partner_id=p.id AND b.status='ACTIVE' "
                        + "WHERE s.token_hash=:token_hash AND s.status='ACTIVE' AND s.expires_at>CURRENT_TIMESTAMP AND p.status='ACTIVE'",
                new MapSqlParameterSource("token_hash", tokenHash));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Embedded onboarding session is invalid or expired");
        }
        return rows.get(0);
    }

    @Transactional
    public void consumeSession(String token) {
        int updated = jdbcTemplate.update(
                "UPDATE embedded_onboarding_sessions SET status='CONSUMED', consumed_at=CURRENT_TIMESTAMP "
                        + "WHERE token_hash=:token_hash AND status='ACTIVE' AND expires_at>CURRENT_TIMESTAMP",
                new MapSqlParameterSource("token_hash", sha256(required(token, "token"))));
        if (updated == 0) {
            throw new PaymentGatewayException("Embedded onboarding session is invalid or expired");
        }
    }

    @Transactional
    public Map<String, Object> linkDownstreamMerchant(
            long partnerMerchantId, long downstreamMerchantId, String actor) {
        if (downstreamMerchantId <= 0 || downstreamMerchantId == partnerMerchantId) {
            throw new PaymentGatewayException("A valid downstream merchant is required");
        }
        long partnerId = partnerId(partnerMerchantId);
        ensureMerchantExists(downstreamMerchantId);
        String relationshipReference = reference("EMBREL");
        jdbcTemplate.update(
                "INSERT INTO embedded_partner_merchants "
                        + "(partner_id, downstream_merchant_id, relationship_reference, status, created_by) "
                        + "VALUES (:partner_id, :downstream_merchant_id, :reference, 'ACTIVE', :created_by) "
                        + "ON DUPLICATE KEY UPDATE status='ACTIVE', ended_at=NULL",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("downstream_merchant_id", downstreamMerchantId)
                        .addValue("reference", relationshipReference)
                        .addValue("created_by", blankToNull(actor)));
        return Map.of("downstreamMerchantId", downstreamMerchantId, "status", "ACTIVE");
    }

    @Transactional
    public Map<String, Object> delegateService(
            long partnerMerchantId,
            long downstreamMerchantId,
            String serviceCode,
            String environment,
            String actor) {
        long partnerId = partnerId(partnerMerchantId);
        ensureRelationship(partnerId, downstreamMerchantId);
        String env = normalizeEnvironment(environment);
        String service = required(serviceCode, "serviceCode").toUpperCase(Locale.ROOT);
        entitlementService.requireEntitlement(partnerMerchantId, service, env);
        jdbcTemplate.update(
                "INSERT INTO embedded_service_delegations "
                        + "(partner_id, downstream_merchant_id, service_code, environment, status, delegated_by) "
                        + "VALUES (:partner_id, :downstream_merchant_id, :service_code, :environment, 'ACTIVE', :delegated_by) "
                        + "ON DUPLICATE KEY UPDATE status='ACTIVE', delegated_by=VALUES(delegated_by), revoked_at=NULL",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("downstream_merchant_id", downstreamMerchantId)
                        .addValue("service_code", service)
                        .addValue("environment", env)
                        .addValue("delegated_by", required(actor, "actor")));
        return Map.of(
                "downstreamMerchantId", downstreamMerchantId,
                "serviceCode", service,
                "environment", env,
                "status", "ACTIVE");
    }

    @Transactional
    public Map<String, Object> saveCommission(
            long merchantId,
            String serviceCode,
            String commissionType,
            java.math.BigDecimal commissionValue,
            String currencyCode,
            String actor) {
        long partnerId = partnerId(merchantId);
        String type = required(commissionType, "commissionType").toUpperCase(Locale.ROOT);
        if (!Set.of("PERCENTAGE", "FIXED").contains(type)) {
            throw new PaymentGatewayException("commissionType must be PERCENTAGE or FIXED");
        }
        if (commissionValue == null || commissionValue.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new PaymentGatewayException("commissionValue cannot be negative");
        }
        if ("PERCENTAGE".equals(type)
                && commissionValue.compareTo(java.math.BigDecimal.valueOf(100)) > 0) {
            throw new PaymentGatewayException("Percentage commission cannot exceed 100");
        }
        jdbcTemplate.update(
                "INSERT INTO embedded_commission_rules "
                        + "(partner_id, service_code, commission_type, commission_value, currency_code, status, created_by) "
                        + "VALUES (:partner_id, :service_code, :commission_type, :commission_value, :currency_code, 'ACTIVE', :created_by) "
                        + "ON DUPLICATE KEY UPDATE commission_type=VALUES(commission_type), commission_value=VALUES(commission_value), "
                        + "currency_code=VALUES(currency_code), status='ACTIVE', updated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("service_code", required(serviceCode, "serviceCode").toUpperCase(Locale.ROOT))
                        .addValue("commission_type", type)
                        .addValue("commission_value", commissionValue)
                        .addValue("currency_code", blankToNull(currencyCode == null ? null : currencyCode.toUpperCase(Locale.ROOT)))
                        .addValue("created_by", blankToNull(actor)));
        return Map.of("serviceCode", serviceCode, "commissionType", type, "commissionValue", commissionValue);
    }

    public List<Map<String, Object>> downstreamMerchants(long merchantId) {
        long partnerId = partnerId(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT r.relationship_reference AS relationshipReference, r.downstream_merchant_id AS downstreamMerchantId, "
                        + "m.account_number AS accountNumber, m.name AS merchantName, r.status, r.created_at AS createdAt, r.ended_at AS endedAt "
                        + "FROM embedded_partner_merchants r JOIN merchants m ON m.id=r.downstream_merchant_id "
                        + "WHERE r.partner_id=:partner_id ORDER BY r.id DESC",
                new MapSqlParameterSource("partner_id", partnerId));
    }

    public List<Map<String, Object>> delegations(long merchantId) {
        long partnerId = partnerId(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT downstream_merchant_id AS downstreamMerchantId, service_code AS serviceCode, environment, status, "
                        + "delegated_by AS delegatedBy, created_at AS createdAt, revoked_at AS revokedAt "
                        + "FROM embedded_service_delegations WHERE partner_id=:partner_id ORDER BY id DESC",
                new MapSqlParameterSource("partner_id", partnerId));
    }

    private Map<String, Object> partner(long merchantId) {
        return jdbcTemplate.queryForMap(
                "SELECT partner_reference AS partnerReference, partner_name AS partnerName, status, created_at AS createdAt, updated_at AS updatedAt "
                        + "FROM embedded_partners WHERE merchant_id=:merchant_id",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    private Map<String, Object> brand(long partnerId) {
        return jdbcTemplate.queryForMap(
                "SELECT brand_name AS brandName, logo_url AS logoUrl, primary_color AS primaryColor, support_email AS supportEmail, "
                        + "custom_domain AS customDomain, terms_url AS termsUrl, privacy_url AS privacyUrl, status, updated_at AS updatedAt "
                        + "FROM embedded_brand_profiles WHERE partner_id=:partner_id",
                new MapSqlParameterSource("partner_id", partnerId));
    }

    private long partnerId(long merchantId) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM embedded_partners WHERE merchant_id=:merchant_id AND status='ACTIVE'",
                new MapSqlParameterSource("merchant_id", merchantId),
                (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Embedded Cito partner profile is not active");
        }
        return rows.get(0);
    }

    private void ensureMerchantExists(long merchantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchants WHERE id=:merchant_id",
                new MapSqlParameterSource("merchant_id", merchantId), Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException("Downstream merchant was not found");
        }
    }

    private void ensureRelationship(long partnerId, long downstreamMerchantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM embedded_partner_merchants WHERE partner_id=:partner_id "
                        + "AND downstream_merchant_id=:downstream_merchant_id AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("partner_id", partnerId)
                        .addValue("downstream_merchant_id", downstreamMerchantId),
                Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException("Downstream merchant relationship is not active");
        }
    }

    private List<String> normalizeServices(long merchantId, List<String> serviceCodes, String environment) {
        if (serviceCodes == null || serviceCodes.isEmpty()) {
            return List.of("CPAY");
        }
        List<String> normalized = serviceCodes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        for (String service : normalized) {
            entitlementService.requireEntitlement(merchantId, service, environment);
        }
        return normalized;
    }

    private String normalizeEnvironment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private void validateHexColor(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!value.trim().matches("#[0-9A-Fa-f]{6}")) {
            throw new PaymentGatewayException("primaryColor must be a six-digit hex color");
        }
    }

    private String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
}