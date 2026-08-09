package net.citotech.cito.vending;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VendingRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public VendingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> locations(long merchantId) {
        return tenantList(
                "SELECT id, location_code, name, address, latitude, longitude, status, created_at, updated_at "
                        + "FROM vending_locations WHERE merchant_id=:tenant_merchant_id ORDER BY name",
                merchantId,
                new MapSqlParameterSource());
    }

    public Map<String, Object> createLocation(
            long merchantId, String code, String name, String address) {
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("code", required(code, "locationCode"));
        p.addValue("name", required(name, "name"));
        p.addValue("address", blankToNull(address));
        String sql =
                "INSERT INTO vending_locations (merchant_id, location_code, name, address) "
                        + "VALUES (:tenant_merchant_id, :code, :name, :address)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
        return tenantOne(
                        "SELECT id, location_code, name, address, status, created_at, updated_at "
                                + "FROM vending_locations WHERE merchant_id=:tenant_merchant_id AND location_code=:code",
                        merchantId,
                        new MapSqlParameterSource("code", code.trim()))
                .orElseThrow();
    }

    public List<Map<String, Object>> pricingPolicies(long merchantId) {
        return tenantList(
                "SELECT * FROM vending_pricing_policies WHERE merchant_id=:tenant_merchant_id ORDER BY name",
                merchantId,
                new MapSqlParameterSource());
    }

    public Map<String, Object> createPricingPolicy(
            long merchantId,
            String policyCode,
            String name,
            String currency,
            BigDecimal depositAmount,
            int freeMinutes,
            BigDecimal unitPrice,
            int billingBlockMinutes,
            int minimumBillingBlocks,
            BigDecimal dailyCapAmount,
            BigDecimal overtimeAmount,
            Integer overtimeDays,
            String refundMode) {
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("policy_code", required(policyCode, "policyCode"));
        p.addValue("name", required(name, "name"));
        p.addValue("currency", required(currency, "currency").toUpperCase());
        p.addValue("deposit_amount", nonNegative(depositAmount, "depositAmount"));
        p.addValue("free_minutes", Math.max(0, freeMinutes));
        p.addValue("unit_price", positive(unitPrice, "unitPrice"));
        p.addValue("billing_block_minutes", Math.max(1, billingBlockMinutes));
        p.addValue("minimum_billing_blocks", Math.max(0, minimumBillingBlocks));
        p.addValue("daily_cap_amount", dailyCapAmount);
        p.addValue("overtime_amount", overtimeAmount);
        p.addValue("overtime_days", overtimeDays);
        p.addValue("refund_mode", refundMode == null || refundMode.isBlank() ? "ORIGINAL_ROUTE" : refundMode.trim().toUpperCase());
        String sql =
                "INSERT INTO vending_pricing_policies "
                        + "(merchant_id, policy_code, name, currency, deposit_amount, free_minutes, unit_price, "
                        + "billing_block_minutes, minimum_billing_blocks, daily_cap_amount, overtime_amount, overtime_days, refund_mode) "
                        + "VALUES (:tenant_merchant_id, :policy_code, :name, :currency, :deposit_amount, :free_minutes, :unit_price, "
                        + ":billing_block_minutes, :minimum_billing_blocks, :daily_cap_amount, :overtime_amount, :overtime_days, :refund_mode)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
        return tenantOne(
                        "SELECT * FROM vending_pricing_policies WHERE merchant_id=:tenant_merchant_id AND policy_code=:policy_code",
                        merchantId,
                        new MapSqlParameterSource("policy_code", policyCode.trim()))
                .orElseThrow();
    }

    public VendingPricingPolicy pricingPolicy(long merchantId, long policyId) {
        Map<String, Object> row =
                tenantOne(
                                "SELECT * FROM vending_pricing_policies WHERE merchant_id=:tenant_merchant_id AND id=:id AND active_flag='YES'",
                                merchantId,
                                new MapSqlParameterSource("id", policyId))
                        .orElseThrow(() -> new PaymentGatewayException("Vending pricing policy was not found or is inactive"));
        return new VendingPricingPolicy(
                number(row.get("id")),
                merchantId,
                string(row.get("policy_code")),
                string(row.get("currency")),
                decimal(row.get("deposit_amount")),
                integer(row.get("free_minutes")),
                decimal(row.get("unit_price")),
                integer(row.get("billing_block_minutes")),
                integer(row.get("minimum_billing_blocks")),
                nullableDecimal(row.get("daily_cap_amount")),
                nullableDecimal(row.get("overtime_amount")),
                nullableInteger(row.get("overtime_days")),
                string(row.get("refund_mode")));
    }

    public List<Map<String, Object>> devices(long merchantId) {
        return tenantList(
                "SELECT d.*, l.name AS location_name, p.name AS pricing_name FROM vending_devices d "
                        + "LEFT JOIN vending_locations l ON l.id=d.location_id AND l.merchant_id=:tenant_merchant_id "
                        + "LEFT JOIN vending_pricing_policies p ON p.id=d.pricing_policy_id AND p.merchant_id=:tenant_merchant_id "
                        + "WHERE d.merchant_id=:tenant_merchant_id ORDER BY d.device_code",
                merchantId,
                new MapSqlParameterSource());
    }

    public Map<String, Object> createDevice(
            long merchantId,
            long locationId,
            long pricingPolicyId,
            String deviceCode,
            String deviceType,
            String connectorCode,
            String externalDeviceId,
            int slotCount) {
        // Tenant-scoped existence checks prevent binding another merchant's location/pricing row.
        pricingPolicy(merchantId, pricingPolicyId);
        tenantOne(
                        "SELECT id FROM vending_locations WHERE merchant_id=:tenant_merchant_id AND id=:id AND status='ACTIVE'",
                        merchantId,
                        new MapSqlParameterSource("id", locationId))
                .orElseThrow(() -> new PaymentGatewayException("Vending location was not found or is inactive"));
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("location_id", locationId);
        p.addValue("pricing_policy_id", pricingPolicyId);
        p.addValue("device_code", required(deviceCode, "deviceCode"));
        p.addValue("device_type", required(deviceType, "deviceType").toUpperCase());
        p.addValue("connector_code", connectorCode == null || connectorCode.isBlank() ? "SIMULATED" : connectorCode.trim().toUpperCase());
        p.addValue("external_device_id", blankToNull(externalDeviceId));
        p.addValue("slot_count", Math.max(0, slotCount));
        String sql =
                "INSERT INTO vending_devices (merchant_id, location_id, pricing_policy_id, device_code, device_type, connector_code, external_device_id, status, slot_count, available_count) "
                        + "VALUES (:tenant_merchant_id, :location_id, :pricing_policy_id, :device_code, :device_type, :connector_code, :external_device_id, 'ONLINE', :slot_count, :slot_count)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
        return deviceByCode(merchantId, deviceCode);
    }

    public Map<String, Object> deviceByCode(long merchantId, String deviceCode) {
        return tenantOne(
                        "SELECT * FROM vending_devices WHERE merchant_id=:tenant_merchant_id AND device_code=:device_code",
                        merchantId,
                        new MapSqlParameterSource("device_code", required(deviceCode, "deviceCode")))
                .orElseThrow(() -> new PaymentGatewayException("Vending device was not found"));
    }

    public List<Map<String, Object>> rentals(long merchantId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", Math.max(1, Math.min(limit, 500)));
        return tenantList(
                "SELECT id, rental_reference, device_id, customer_mask, channel_code, currency, deposit_amount, "
                        + "escrow_amount, usage_amount, refund_amount, surcharge_created, billed_blocks, status, started_at, ended_at, created_at, updated_at "
                        + "FROM vending_rentals WHERE merchant_id=:tenant_merchant_id ORDER BY id DESC LIMIT :limit",
                merchantId,
                p);
    }

    public Optional<Map<String, Object>> rental(long merchantId, String rentalReference) {
        return tenantOne(
                "SELECT * FROM vending_rentals WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference",
                merchantId,
                new MapSqlParameterSource("rental_reference", required(rentalReference, "rentalReference")));
    }

    public Optional<Map<String, Object>> activeRentalForCustomer(long merchantId, String customerHash) {
        MapSqlParameterSource p = new MapSqlParameterSource("customer_hash", customerHash);
        return tenantOne(
                "SELECT id, rental_reference, status FROM vending_rentals "
                        + "WHERE merchant_id=:tenant_merchant_id AND customer_hash=:customer_hash "
                        + "AND status IN ('PAYMENT_PENDING','READY_TO_RELEASE','ACTIVE','RELEASE_FAILED','REFUND_PENDING') "
                        + "ORDER BY id DESC LIMIT 1",
                merchantId,
                p);
    }

    public Map<String, Object> customerBalance(long merchantId, String customerHash, String currency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("customer_hash", customerHash);
        p.addValue("currency", currency);
        Optional<Map<String, Object>> existing = tenantOne(
                "SELECT * FROM vending_customer_balances WHERE merchant_id=:tenant_merchant_id AND customer_hash=:customer_hash AND currency=:currency",
                merchantId,
                p);
        if (existing.isPresent()) return existing.get();
        MapSqlParameterSource insert = TenantScopeGuard.scope(p, merchantId);
        String sql =
                "INSERT INTO vending_customer_balances (merchant_id, customer_hash, currency) "
                        + "VALUES (:tenant_merchant_id, :customer_hash, :currency)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, insert);
        return tenantOne(
                        "SELECT * FROM vending_customer_balances WHERE merchant_id=:tenant_merchant_id AND customer_hash=:customer_hash AND currency=:currency",
                        merchantId,
                        p)
                .orElseThrow();
    }

    public long createRental(
            long merchantId,
            String rentalReference,
            long deviceId,
            long pricingPolicyId,
            String customerHash,
            String customerMask,
            String customerCiphertext,
            String channelCode,
            VendingPricingPolicy policy,
            BigDecimal surchargePlanned,
            BigDecimal escrowAmount,
            String collectReference) {
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("rental_reference", rentalReference);
        p.addValue("device_id", deviceId);
        p.addValue("pricing_policy_id", pricingPolicyId);
        p.addValue("customer_hash", customerHash);
        p.addValue("customer_mask", customerMask);
        p.addValue("customer_ciphertext", customerCiphertext);
        p.addValue("channel_code", blankToNull(channelCode));
        p.addValue("currency", policy.currency());
        p.addValue("deposit_amount", policy.depositAmount());
        p.addValue("surcharge_settled", surchargePlanned);
        p.addValue("escrow_amount", escrowAmount);
        p.addValue("collect_reference", collectReference);
        String sql =
                "INSERT INTO vending_rentals (merchant_id, rental_reference, device_id, pricing_policy_id, customer_hash, customer_mask, customer_ciphertext, channel_code, currency, deposit_amount, surcharge_settled_from_deposit, escrow_amount, status, collect_reference) "
                        + "VALUES (:tenant_merchant_id, :rental_reference, :device_id, :pricing_policy_id, :customer_hash, :customer_mask, :customer_ciphertext, :channel_code, :currency, :deposit_amount, :surcharge_settled, :escrow_amount, 'PAYMENT_PENDING', :collect_reference)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
        return number(rental(merchantId, rentalReference).orElseThrow().get("id"));
    }

    public void setCollectTransaction(long merchantId, String rentalReference, String transactionId) {
        tenantUpdate(
                "UPDATE vending_rentals SET collect_transaction_id=:transaction_id WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference",
                merchantId,
                new MapSqlParameterSource().addValue("transaction_id", transactionId).addValue("rental_reference", rentalReference));
    }

    public int activateAfterSuccessfulCollection(long merchantId, String rentalReference) {
        return tenantUpdate(
                "UPDATE vending_rentals SET status='READY_TO_RELEASE', started_at=CURRENT_TIMESTAMP "
                        + "WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference AND status='PAYMENT_PENDING'",
                merchantId,
                new MapSqlParameterSource("rental_reference", rentalReference));
    }

    public void setRentalStatus(long merchantId, String rentalReference, String status) {
        tenantUpdate(
                "UPDATE vending_rentals SET status=:status WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference",
                merchantId,
                new MapSqlParameterSource().addValue("status", status).addValue("rental_reference", rentalReference));
    }

    public void settlePriorSurcharge(long merchantId, String customerHash, String currency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        tenantUpdate(
                "UPDATE vending_customer_balances SET surcharge_balance=GREATEST(0, surcharge_balance-:amount) "
                        + "WHERE merchant_id=:tenant_merchant_id AND customer_hash=:customer_hash AND currency=:currency",
                merchantId,
                new MapSqlParameterSource().addValue("amount", amount).addValue("customer_hash", customerHash).addValue("currency", currency));
    }

    public void completeRental(
            long merchantId,
            String rentalReference,
            BigDecimal usage,
            BigDecimal refund,
            BigDecimal surcharge,
            long billedBlocks,
            String status,
            String refundReference) {
        tenantUpdate(
                "UPDATE vending_rentals SET usage_amount=:usage, refund_amount=:refund, surcharge_created=:surcharge, "
                        + "billed_blocks=:blocks, ended_at=CURRENT_TIMESTAMP, status=:status, refund_reference=:refund_reference "
                        + "WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference AND status='ACTIVE'",
                merchantId,
                new MapSqlParameterSource()
                        .addValue("usage", usage)
                        .addValue("refund", refund)
                        .addValue("surcharge", surcharge)
                        .addValue("blocks", billedBlocks)
                        .addValue("status", status)
                        .addValue("refund_reference", refundReference)
                        .addValue("rental_reference", rentalReference));
    }

    public void addSurcharge(long merchantId, String customerHash, String currency, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return;
        customerBalance(merchantId, customerHash, currency);
        tenantUpdate(
                "UPDATE vending_customer_balances SET surcharge_balance=surcharge_balance+:amount "
                        + "WHERE merchant_id=:tenant_merchant_id AND customer_hash=:customer_hash AND currency=:currency",
                merchantId,
                new MapSqlParameterSource().addValue("amount", amount).addValue("customer_hash", customerHash).addValue("currency", currency));
    }

    public BigDecimal waiveSurcharge(long merchantId, String customerHash, String currency, BigDecimal requested) {
        Map<String, Object> balance = customerBalance(merchantId, customerHash, currency);
        BigDecimal existing = decimal(balance.get("surcharge_balance"));
        BigDecimal amount = requested == null ? existing : requested.max(BigDecimal.ZERO).min(existing);
        settlePriorSurcharge(merchantId, customerHash, currency, amount);
        return amount;
    }

    public void setRefundTransaction(long merchantId, String rentalReference, String transactionId) {
        tenantUpdate(
                "UPDATE vending_rentals SET refund_transaction_id=:transaction_id WHERE merchant_id=:tenant_merchant_id AND rental_reference=:rental_reference",
                merchantId,
                new MapSqlParameterSource().addValue("transaction_id", transactionId).addValue("rental_reference", rentalReference));
    }

    public void event(long merchantId, String eventType, String entityType, String entityReference, String actor, BigDecimal amount, String currency, String detailJson) {
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("event_type", eventType);
        p.addValue("entity_type", entityType);
        p.addValue("entity_reference", entityReference);
        p.addValue("actor", blankToNull(actor));
        p.addValue("amount", amount);
        p.addValue("currency", blankToNull(currency));
        p.addValue("detail_json", blankToNull(detailJson));
        String sql =
                "INSERT INTO vending_events (merchant_id, event_type, entity_type, entity_reference, actor, amount, currency, detail_json) "
                        + "VALUES (:tenant_merchant_id, :event_type, :entity_type, :entity_reference, :actor, :amount, :currency, :detail_json)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
    }

    public void command(long merchantId, long deviceId, Long rentalId, String reference, String type, String connector, String status, String providerReference, String responseJson) {
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("device_id", deviceId);
        p.addValue("rental_id", rentalId);
        p.addValue("reference", reference);
        p.addValue("type", type);
        p.addValue("connector", connector);
        p.addValue("status", status);
        p.addValue("provider_reference", blankToNull(providerReference));
        p.addValue("response_json", blankToNull(responseJson));
        String sql =
                "INSERT INTO vending_commands (merchant_id, device_id, rental_id, command_reference, command_type, connector_code, status, provider_reference, response_json, completed_at) "
                        + "VALUES (:tenant_merchant_id, :device_id, :rental_id, :reference, :type, :connector, :status, :provider_reference, :response_json, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status), provider_reference=VALUES(provider_reference), response_json=VALUES(response_json), completed_at=CURRENT_TIMESTAMP";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
    }

    private List<Map<String, Object>> tenantList(String sql, long merchantId, MapSqlParameterSource params) {
        TenantScopeGuard.assertTenantBound(sql);
        return jdbc.queryForList(sql, TenantScopeGuard.scope(params, merchantId));
    }

    private Optional<Map<String, Object>> tenantOne(String sql, long merchantId, MapSqlParameterSource params) {
        List<Map<String, Object>> rows = tenantList(sql, merchantId, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private int tenantUpdate(String sql, long merchantId, MapSqlParameterSource params) {
        TenantScopeGuard.assertTenantBound(sql);
        return jdbc.update(sql, TenantScopeGuard.scope(params, merchantId));
    }

    private String required(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new PaymentGatewayException(name + " is required");
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new PaymentGatewayException(name + " must be greater than zero");
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new PaymentGatewayException(name + " must not be negative");
        return value;
    }

    public static long number(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    public static int integer(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    public static Integer nullableInteger(Object value) {
        return value == null ? null : integer(value);
    }

    public static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
    }

    public static BigDecimal nullableDecimal(Object value) {
        return value == null ? null : decimal(value);
    }

    public static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static Instant instant(Object value) {
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof Instant i) return i;
        throw new PaymentGatewayException("Expected timestamp value");
    }
}
