package net.citotech.cito.vending;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.FeatureKeys;
import net.citotech.cito.admin.FeatureRegistryService;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.SimpleRateLimitService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public QR/H5 rental journey backed by opaque, revocable device and status tokens. */
@Service
public class VendingHostedRentalService {
    private final NamedParameterJdbcTemplate jdbc;
    private final VendingRentalService rentals;
    private final VendingRepository repository;
    private final FeatureRegistryService features;
    private final SimpleRateLimitService rateLimits;
    private final SecureRandom random = new SecureRandom();

    public VendingHostedRentalService(
            NamedParameterJdbcTemplate jdbc,
            VendingRentalService rentals,
            VendingRepository repository,
            FeatureRegistryService features,
            SimpleRateLimitService rateLimits) {
        this.jdbc = jdbc;
        this.rentals = rentals;
        this.repository = repository;
        this.features = features;
        this.rateLimits = rateLimits;
    }

    /** Generates/rotates a station QR token. The token is intentionally public but high entropy. */
    @Transactional
    public Map<String, Object> rotateDevicePublicToken(long merchantId, String deviceCode, String appBaseUrl) {
        Map<String, Object> device = repository.deviceByCode(merchantId, deviceCode);
        String token = randomToken();
        String sql =
                "UPDATE vending_devices SET public_token=:public_token WHERE merchant_id=:tenant_merchant_id AND id=:device_id";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("device_id", number(device.get("id")));
        p.addValue("public_token", token);
        jdbc.update(sql, p);
        String base = appBaseUrl == null ? "" : appBaseUrl.replaceAll("/+$", "");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceCode", deviceCode);
        result.put("publicToken", token);
        result.put("hostedPath", "/vending/rent/" + token);
        result.put("qrPayload", base.isBlank() ? "/vending/rent/" + token : base + "/vending/rent/" + token);
        return result;
    }

    public Map<String, Object> publicDevice(String publicToken) {
        Map<String, Object> row = requirePublicDevice(publicToken);
        long merchantId = number(row.get("merchant_id"));
        requireEnabled(merchantId);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("deviceCode", row.get("device_code"));
        view.put("deviceType", row.get("device_type"));
        view.put("status", row.get("status"));
        view.put("availableCount", row.get("available_count"));
        view.put("locationName", row.get("location_name"));
        view.put("locationAddress", row.get("location_address"));
        view.put("pricingName", row.get("pricing_name"));
        view.put("currency", row.get("currency"));
        view.put("depositAmount", row.get("deposit_amount"));
        view.put("freeMinutes", row.get("free_minutes"));
        view.put("unitPrice", row.get("unit_price"));
        view.put("billingBlockMinutes", row.get("billing_block_minutes"));
        view.put("minimumBillingBlocks", row.get("minimum_billing_blocks"));
        view.put("dailyCapAmount", row.get("daily_cap_amount"));
        view.put("overtimeAmount", row.get("overtime_amount"));
        view.put("overtimeDays", row.get("overtime_days"));
        return view;
    }

    @Transactional
    public Map<String, Object> start(
            String publicToken, String customerMsisdn, String channel, String clientIp) {
        Map<String, Object> device = requirePublicDevice(publicToken);
        long merchantId = number(device.get("merchant_id"));
        requireEnabled(merchantId);
        if (!rateLimits.allow(
                "vending-hosted-start:" + hash(publicToken).substring(0, 16) + ":" + safeIp(clientIp), 8)) {
            throw new PaymentGatewayException("Too many rental attempts. Please wait and try again");
        }
        if (!"ONLINE".equalsIgnoreCase(text(device.get("status")))) {
            throw new PaymentGatewayException("This vending station is currently unavailable");
        }
        if (number(device.get("available_count")) <= 0) {
            throw new PaymentGatewayException("This vending station has no available items right now");
        }

        Map<String, Object> rental =
                rentals.startRental(
                        merchantId,
                        text(device.get("device_code")),
                        customerMsisdn,
                        channel,
                        "",
                        "hosted:" + safeIp(clientIp));
        String rentalReference = text(rental.get("rental_reference"));
        String statusToken = randomToken();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        String sql =
                "INSERT INTO vending_hosted_sessions (merchant_id, device_id, rental_reference, session_token_hash, expires_at) "
                        + "VALUES (:tenant_merchant_id, :device_id, :rental_reference, :token_hash, :expires_at)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("device_id", number(device.get("id")));
        p.addValue("rental_reference", rentalReference);
        p.addValue("token_hash", hash(statusToken));
        p.addValue("expires_at", Timestamp.from(expiresAt));
        jdbc.update(sql, p);

        Map<String, Object> result = safeRental(rental);
        result.put("statusToken", statusToken);
        result.put("statusPath", "/api/v2/vending/hosted/sessions/" + statusToken);
        result.put("expiresAt", expiresAt.toString());
        return result;
    }

    public Map<String, Object> status(String statusToken) {
        String sql =
                "SELECT merchant_id, device_id, rental_reference, status, expires_at FROM vending_hosted_sessions "
                        + "WHERE session_token_hash=:token_hash LIMIT 1";
        MapSqlParameterSource p = new MapSqlParameterSource("token_hash", hash(statusToken));
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) throw new PaymentGatewayException("Hosted rental session was not found");
        Map<String, Object> session = rows.get(0);
        Instant expires = instant(session.get("expires_at"));
        if (expires.isBefore(Instant.now()) || "EXPIRED".equalsIgnoreCase(text(session.get("status")))) {
            throw new PaymentGatewayException("Hosted rental session has expired");
        }
        long merchantId = number(session.get("merchant_id"));
        String rentalReference = text(session.get("rental_reference"));
        try {
            rentals.sync(merchantId, rentalReference, "hosted-status");
        } catch (PaymentGatewayException ignored) {
            // Polling is best-effort. Current persisted state remains authoritative when an upstream
            // provider/device is still pending or has not produced a transaction row yet.
        }
        touch(statusToken);
        Map<String, Object> rental = repository.rental(merchantId, rentalReference)
                .orElseThrow(() -> new PaymentGatewayException("Hosted rental was not found"));
        return safeRental(rental);
    }

    private Map<String, Object> requirePublicDevice(String publicToken) {
        if (publicToken == null || publicToken.length() < 24) {
            throw new PaymentGatewayException("Vending station token is invalid");
        }
        String sql =
                "SELECT d.id, d.merchant_id, d.device_code, d.device_type, d.status, d.available_count, "
                        + "l.name AS location_name, l.address AS location_address, p.name AS pricing_name, "
                        + "p.currency, p.deposit_amount, p.free_minutes, p.unit_price, p.billing_block_minutes, "
                        + "p.minimum_billing_blocks, p.daily_cap_amount, p.overtime_amount, p.overtime_days "
                        + "FROM vending_devices d "
                        + "JOIN vending_pricing_policies p ON p.id=d.pricing_policy_id AND p.merchant_id=d.merchant_id AND p.active_flag='YES' "
                        + "LEFT JOIN vending_locations l ON l.id=d.location_id AND l.merchant_id=d.merchant_id "
                        + "WHERE d.public_token=:public_token LIMIT 1";
        List<Map<String, Object>> rows =
                jdbc.queryForList(sql, new MapSqlParameterSource("public_token", publicToken.trim()));
        if (rows.isEmpty()) throw new PaymentGatewayException("Vending station was not found");
        return rows.get(0);
    }

    private void requireEnabled(long merchantId) {
        if (!features.isEnabled(FeatureKeys.VENDING_PLATFORM, merchantId)) {
            throw new PaymentGatewayException("Vending is not enabled for this merchant");
        }
    }

    private Map<String, Object> safeRental(Map<String, Object> rental) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rentalReference", rental.get("rental_reference"));
        result.put("status", rental.get("status"));
        result.put("currency", rental.get("currency"));
        result.put("depositAmount", rental.get("deposit_amount"));
        result.put("escrowAmount", rental.get("escrow_amount"));
        result.put("usageAmount", rental.get("usage_amount"));
        result.put("refundAmount", rental.get("refund_amount"));
        result.put("surchargeCreated", rental.get("surcharge_created"));
        result.put("startedAt", rental.get("started_at"));
        result.put("endedAt", rental.get("ended_at"));
        return result;
    }

    private void touch(String token) {
        jdbc.update(
                "UPDATE vending_hosted_sessions SET last_seen_at=CURRENT_TIMESTAMP WHERE session_token_hash=:token_hash",
                new MapSqlParameterSource("token_hash", hash(token)));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash vending hosted token", e);
        }
    }

    private long number(Object value) {
        if (value == null) return 0;
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safeIp(String value) {
        return value == null || value.isBlank() ? "unknown" : value.replaceAll("[^0-9a-fA-F.:]", "");
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp ts) return ts.toInstant();
        return Instant.parse(String.valueOf(value));
    }
}
