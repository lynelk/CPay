package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingBaasChargingService {
    private static final int MONEY_SCALE = 4;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BillingLedgerAccountTemplateService ledgerService;

    public BillingBaasChargingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            BillingLedgerAccountTemplateService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public ChargeView authorize(
            BillingBaasContext context,
            String billingAccountReference,
            String serviceCode,
            String entitlementCode,
            BigDecimal usageQuantity,
            BigDecimal netAmount,
            String currency,
            String idempotencyKey,
            Instant expiresAt) {
        requireContext(context);
        String accountReference = required(billingAccountReference, "billingAccountReference");
        String service = required(serviceCode, "serviceCode").toUpperCase();
        String idem = required(idempotencyKey, "idempotencyKey");
        BigDecimal net = money(netAmount, "netAmount");
        BigDecimal quantity = positive(usageQuantity, "usageQuantity");
        String ccy = required(currency, "currency").toUpperCase();
        Instant expiry = expiresAt == null ? Instant.now().plusSeconds(900) : expiresAt;
        if (!expiry.isAfter(Instant.now())) {
            throw new PaymentGatewayException("Charging authorization expiry must be in the future");
        }

        BillingAccount account = resolveAccount(context.billingTenantId(), accountReference, ccy);
        ChargeView replay = findByIdempotency(context.billingTenantId(), idem);
        if (replay != null) {
            if (replay.billingAccountId() != account.id()
                    || replay.authorizedNetAmount().compareTo(net) != 0
                    || !replay.serviceCode().equals(service)
                    || replay.usageQuantity().compareTo(quantity) != 0) {
                throw new PaymentGatewayException(
                        "Charging idempotency key already exists with different attributes");
            }
            return replay;
        }

        TaxRule taxRule = resolveTaxRule(context.billingTenantId(), ccy);
        BigDecimal tax = net.multiply(taxRule.rate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal gross = net.add(tax).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (entitlementCode != null && !entitlementCode.isBlank()) {
            requireEntitlement(
                    context.billingTenantId(),
                    account.customerId(),
                    service,
                    entitlementCode.trim(),
                    quantity);
        }

        long chargingAccountId = ensureChargingAccount(context.billingTenantId(), account);
        ChargingAccount chargingAccount = lockChargingAccount(context.billingTenantId(), chargingAccountId);
        BigDecimal available =
                chargingAccount
                        .prepaidBalance()
                        .add(chargingAccount.creditLimit())
                        .subtract(chargingAccount.creditUsed())
                        .subtract(chargingAccount.reservedAmount());
        if (available.compareTo(gross) < 0) {
            throw new PaymentGatewayException("Insufficient BaaS charging balance or credit headroom");
        }

        String reservationReference = "BCR-" + UUID.randomUUID();
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("customer", account.customerId())
                        .addValue("charging_account", chargingAccountId)
                        .addValue("reservation", reservationReference)
                        .addValue("service", service)
                        .addValue("entitlement", blankToNull(entitlementCode))
                        .addValue("quantity", quantity)
                        .addValue("currency", ccy)
                        .addValue("gross", gross)
                        .addValue("net", net)
                        .addValue("tax", tax)
                        .addValue("tax_rule", taxRule.id())
                        .addValue("tax_code", taxRule.taxCode())
                        .addValue("tax_rate", taxRule.rate())
                        .addValue("idempotency", idem)
                        .addValue("expires_at", Timestamp.from(expiry));
        jdbcTemplate.update(
                "INSERT INTO billing_charge_reservations "
                        + "(billing_tenant_id,billing_customer_id,charging_account_id,reservation_reference,"
                        + "service_code,entitlement_code,usage_quantity,currency,authorized_amount,"
                        + "authorized_net_amount,authorized_tax_amount,tax_rule_version_id,tax_code,tax_rate,"
                        + "idempotency_key,expires_at) VALUES (:tenant,:customer,:charging_account,:reservation,"
                        + ":service,:entitlement,:quantity,:currency,:gross,:net,:tax,:tax_rule,:tax_code,"
                        + ":tax_rate,:idempotency,:expires_at)",
                p);
        jdbcTemplate.update(
                "UPDATE billing_charging_accounts SET reserved_amount=reserved_amount+:gross,"
                        + "lock_version=lock_version+1 WHERE id=:charging_account AND billing_tenant_id=:tenant",
                p);
        insertAdjustment(
                context.billingTenantId(),
                chargingAccountId,
                reservationReference,
                "AUTHORIZE",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                gross,
                null,
                "authorize:" + idem,
                "BaaS:" + context.serviceAccountId());
        return findByReference(context.billingTenantId(), reservationReference);
    }

    @Transactional
    public CommitView commit(BillingBaasContext context, String reservationReference) {
        requireContext(context);
        ChargeView reservation = lockReservation(context.billingTenantId(), reservationReference);
        if ("COMMITTED".equals(reservation.status())) {
            return committedView(context.billingTenantId(), reservation.reservationReference());
        }
        if (!"AUTHORIZED".equals(reservation.status())) {
            throw new PaymentGatewayException(
                    "Only an AUTHORIZED charging reservation can be committed");
        }
        if (!reservation.expiresAt().isAfter(Instant.now())) {
            expireAuthorizedReservation(context, reservation);
            throw new PaymentGatewayException("Charging reservation has expired");
        }

        ChargingAccount account =
                lockChargingAccount(context.billingTenantId(), reservation.chargingAccountId());
        BigDecimal gross = reservation.authorizedAmount();
        BigDecimal prepaid = account.prepaidBalance().min(gross);
        BigDecimal credit = gross.subtract(prepaid);
        if (account.creditUsed().add(credit).compareTo(account.creditLimit()) > 0) {
            throw new PaymentGatewayException("Charging credit limit would be exceeded");
        }

        Split prepaidSplit = splitFunding(reservation, prepaid);
        Split creditSplit =
                new Split(
                        reservation.authorizedNetAmount().subtract(prepaidSplit.net()),
                        reservation.authorizedTaxAmount().subtract(prepaidSplit.tax()));
        Long prepaidLedger = null;
        Long creditLedger = null;
        if (prepaid.signum() > 0) {
            String usageReference = reservation.reservationReference() + ":prepaid";
            prepaidLedger =
                    ledgerService.postPrepaidConsumptionWithTax(
                            context.billingTenantId(),
                            reservation.currency(),
                            prepaidSplit.net(),
                            prepaidSplit.tax(),
                            usageReference,
                            "BaaS prepaid commit " + reservation.reservationReference());
            insertLedgerLink(
                    context.billingTenantId(),
                    reservation.reservationReference(),
                    "PREPAID",
                    prepaidLedger,
                    "billing-consumption:" + usageReference);
        }
        if (credit.signum() > 0) {
            String chargeReference = reservation.reservationReference() + ":credit";
            creditLedger =
                    ledgerService.postCustomerCharge(
                            context.billingTenantId(),
                            reservation.currency(),
                            creditSplit.net(),
                            creditSplit.tax(),
                            chargeReference,
                            "BaaS credit commit " + reservation.reservationReference());
            insertLedgerLink(
                    context.billingTenantId(),
                    reservation.reservationReference(),
                    "CREDIT",
                    creditLedger,
                    "billing-charge:" + chargeReference);
        }

        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("account", reservation.chargingAccountId())
                        .addValue("reservation", reservation.reservationReference())
                        .addValue("gross", gross)
                        .addValue("net", reservation.authorizedNetAmount())
                        .addValue("tax", reservation.authorizedTaxAmount())
                        .addValue("prepaid", prepaid)
                        .addValue("credit", credit);
        jdbcTemplate.update(
                "UPDATE billing_charging_accounts SET prepaid_balance=prepaid_balance-:prepaid,"
                        + "credit_used=credit_used+:credit,reserved_amount=reserved_amount-:gross,"
                        + "lock_version=lock_version+1 WHERE id=:account AND billing_tenant_id=:tenant",
                p);
        jdbcTemplate.update(
                "UPDATE billing_charge_reservations SET committed_amount=:gross,committed_net_amount=:net,"
                        + "committed_tax_amount=:tax,prepaid_committed_amount=:prepaid,"
                        + "credit_committed_amount=:credit,status='COMMITTED' "
                        + "WHERE reservation_reference=:reservation AND billing_tenant_id=:tenant "
                        + "AND status='AUTHORIZED'",
                p);
        insertAdjustment(
                context.billingTenantId(),
                reservation.chargingAccountId(),
                reservation.reservationReference(),
                "COMMIT",
                prepaid.negate(),
                credit,
                gross.negate(),
                prepaidLedger != null ? prepaidLedger : creditLedger,
                "commit:" + reservation.reservationReference(),
                "BaaS:" + context.serviceAccountId());
        consumeEntitlement(context.billingTenantId(), reservation);
        return committedView(context.billingTenantId(), reservation.reservationReference());
    }

    @Transactional
    public ChargeView release(BillingBaasContext context, String reservationReference) {
        requireContext(context);
        ChargeView reservation = lockReservation(context.billingTenantId(), reservationReference);
        if ("RELEASED".equals(reservation.status())) {
            return reservation;
        }
        if (!"AUTHORIZED".equals(reservation.status())) {
            throw new PaymentGatewayException(
                    "Only an AUTHORIZED charging reservation can be released");
        }
        releaseReservation(context, reservation, "RELEASE", "RELEASED");
        return findByReference(context.billingTenantId(), reservation.reservationReference());
    }

    @Transactional
    public ChargeView reverse(BillingBaasContext context, String reservationReference) {
        requireContext(context);
        ChargeView reservation = lockReservation(context.billingTenantId(), reservationReference);
        if ("REVERSED".equals(reservation.status())) {
            return reservation;
        }
        if (!"COMMITTED".equals(reservation.status())) {
            throw new PaymentGatewayException(
                    "Only a COMMITTED charging reservation can be reversed");
        }
        requireApprovedProtectedAction(context.billingTenantId(), reservation.reservationReference());
        ChargingAccount account =
                lockChargingAccount(context.billingTenantId(), reservation.chargingAccountId());
        BigDecimal prepaid = reservation.prepaidCommittedAmount();
        BigDecimal credit = reservation.creditCommittedAmount();
        if (account.creditUsed().compareTo(credit) < 0) {
            throw new PaymentGatewayException("Charging reversal would make used credit negative");
        }

        reverseLedgerLinks(context, reservation.reservationReference());
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("account", reservation.chargingAccountId())
                        .addValue("reservation", reservation.reservationReference())
                        .addValue("prepaid", prepaid)
                        .addValue("credit", credit);
        jdbcTemplate.update(
                "UPDATE billing_charging_accounts SET prepaid_balance=prepaid_balance+:prepaid,"
                        + "credit_used=credit_used-:credit,lock_version=lock_version+1 "
                        + "WHERE id=:account AND billing_tenant_id=:tenant",
                p);
        jdbcTemplate.update(
                "UPDATE billing_charge_reservations SET status='REVERSED' "
                        + "WHERE reservation_reference=:reservation AND billing_tenant_id=:tenant "
                        + "AND status='COMMITTED'",
                p);
        reverseEntitlementUsage(context.billingTenantId(), reservation);
        insertAdjustment(
                context.billingTenantId(),
                reservation.chargingAccountId(),
                reservation.reservationReference(),
                "REVERSE",
                prepaid,
                credit.negate(),
                BigDecimal.ZERO,
                null,
                "reverse:" + reservation.reservationReference(),
                "BaaS:" + context.serviceAccountId());
        return findByReference(context.billingTenantId(), reservation.reservationReference());
    }

    public ChargeView get(BillingBaasContext context, String reservationReference) {
        requireContext(context);
        return findByReference(context.billingTenantId(), reservationReference);
    }

    private void releaseReservation(
            BillingBaasContext context,
            ChargeView reservation,
            String adjustmentType,
            String reservationStatus) {
        lockChargingAccount(context.billingTenantId(), reservation.chargingAccountId());
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("account", reservation.chargingAccountId())
                        .addValue("reservation", reservation.reservationReference())
                        .addValue("gross", reservation.authorizedAmount())
                        .addValue("status", reservationStatus);
        jdbcTemplate.update(
                "UPDATE billing_charging_accounts SET reserved_amount=reserved_amount-:gross,"
                        + "lock_version=lock_version+1 WHERE id=:account AND billing_tenant_id=:tenant",
                p);
        jdbcTemplate.update(
                "UPDATE billing_charge_reservations SET released_amount=:gross,status=:status "
                        + "WHERE reservation_reference=:reservation AND billing_tenant_id=:tenant "
                        + "AND status='AUTHORIZED'",
                p);
        insertAdjustment(
                context.billingTenantId(),
                reservation.chargingAccountId(),
                reservation.reservationReference(),
                adjustmentType,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                reservation.authorizedAmount().negate(),
                null,
                adjustmentType.toLowerCase() + ":" + reservation.reservationReference(),
                "BaaS:" + context.serviceAccountId());
    }

    private void expireAuthorizedReservation(
            BillingBaasContext context, ChargeView reservation) {
        releaseReservation(context, reservation, "EXPIRE", "EXPIRED");
    }

    private BillingAccount resolveAccount(long tenantId, String externalReference, String currency) {
        List<BillingAccount> rows =
                jdbcTemplate.query(
                        "SELECT id,billing_customer_id,currency,credit_limit FROM billing_accounts "
                                + "WHERE billing_tenant_id=:tenant AND external_reference=:reference "
                                + "AND account_status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", externalReference),
                        (rs, rowNum) ->
                                new BillingAccount(
                                        rs.getLong("id"),
                                        rs.getLong("billing_customer_id"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("credit_limit")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Billing account was not found for this tenant");
        }
        BillingAccount account = rows.get(0);
        if (!account.currency().equalsIgnoreCase(currency)) {
            throw new PaymentGatewayException("Charging currency does not match billing account currency");
        }
        return account;
    }

    private long ensureChargingAccount(long tenantId, BillingAccount account) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("customer", account.customerId())
                        .addValue("billing_account", account.id())
                        .addValue("currency", account.currency().toUpperCase())
                        .addValue("credit_limit", account.creditLimit());
        jdbcTemplate.update(
                "INSERT IGNORE INTO billing_charging_accounts "
                        + "(billing_tenant_id,billing_customer_id,billing_account_id,currency,credit_limit) "
                        + "VALUES (:tenant,:customer,:billing_account,:currency,:credit_limit)",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM billing_charging_accounts WHERE billing_tenant_id=:tenant "
                                + "AND billing_account_id=:billing_account AND currency=:currency",
                        p,
                        Long.class);
        if (id == null) {
            throw new PaymentGatewayException("Unable to initialize BaaS charging account");
        }
        return id;
    }

    private ChargingAccount lockChargingAccount(long tenantId, long chargingAccountId) {
        List<ChargingAccount> rows =
                jdbcTemplate.query(
                        "SELECT id,prepaid_balance,credit_limit,credit_used,reserved_amount,status "
                                + "FROM billing_charging_accounts WHERE id=:id AND billing_tenant_id=:tenant FOR UPDATE",
                        new MapSqlParameterSource()
                                .addValue("id", chargingAccountId)
                                .addValue("tenant", tenantId),
                        (rs, rowNum) ->
                                new ChargingAccount(
                                        rs.getLong("id"),
                                        rs.getBigDecimal("prepaid_balance"),
                                        rs.getBigDecimal("credit_limit"),
                                        rs.getBigDecimal("credit_used"),
                                        rs.getBigDecimal("reserved_amount"),
                                        rs.getString("status")));
        if (rows.isEmpty() || !"ACTIVE".equals(rows.get(0).status())) {
            throw new PaymentGatewayException("BaaS charging account is unavailable or suspended");
        }
        return rows.get(0);
    }

    private TaxRule resolveTaxRule(long tenantId, String currency) {
        List<TaxRule> rows =
                jdbcTemplate.query(
                        "SELECT id,tax_code,rate FROM billing_tax_rule_versions "
                                + "WHERE status='APPROVED' AND tax_code='STANDARD' AND currency=:currency "
                                + "AND effective_from<=CURRENT_TIMESTAMP "
                                + "AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP) "
                                + "AND (billing_tenant_id=:tenant OR billing_tenant_id IS NULL) "
                                + "ORDER BY CASE WHEN billing_tenant_id=:tenant THEN 0 ELSE 1 END,effective_from DESC LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("currency", currency),
                        (rs, rowNum) ->
                                new TaxRule(
                                        rs.getLong("id"),
                                        rs.getString("tax_code"),
                                        rs.getBigDecimal("rate")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "No approved billing tax rule for online charging in " + currency);
        }
        return rows.get(0);
    }

    private void requireEntitlement(
            long tenantId,
            long customerId,
            String serviceCode,
            String entitlementCode,
            BigDecimal quantity) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT g.limit_quantity FROM billing_entitlement_grants g "
                                + "JOIN billing_subscriptions s ON s.id=g.billing_subscription_id "
                                + "WHERE g.billing_tenant_id=:tenant AND g.billing_customer_id=:customer "
                                + "AND g.entitlement_code=:entitlement AND g.status='ACTIVE' "
                                + "AND g.valid_from<=CURRENT_TIMESTAMP AND (g.valid_to IS NULL OR g.valid_to>CURRENT_TIMESTAMP) "
                                + "AND s.status='ACTIVE' AND s.service_code=:service LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("customer", customerId)
                                .addValue("entitlement", entitlementCode)
                                .addValue("service", serviceCode));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Required BaaS entitlement is not active");
        }
        Object limitValue = rows.get(0).get("limit_quantity");
        if (limitValue == null) {
            return;
        }
        BigDecimal limit = (BigDecimal) limitValue;
        BigDecimal consumed = entitlementConsumed(tenantId, customerId, entitlementCode);
        if (consumed.add(quantity).compareTo(limit) > 0) {
            throw new PaymentGatewayException("BaaS entitlement usage limit would be exceeded");
        }
    }

    private BigDecimal entitlementConsumed(long tenantId, long customerId, String entitlementCode) {
        BigDecimal value =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(consumed_quantity,0) FROM billing_entitlement_usage "
                                + "WHERE billing_tenant_id=:tenant AND billing_customer_id=:customer "
                                + "AND entitlement_code=:entitlement AND period_key=:period",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("customer", customerId)
                                .addValue("entitlement", entitlementCode)
                                .addValue("period", periodKey()),
                        BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private void consumeEntitlement(long tenantId, ChargeView reservation) {
        if (reservation.entitlementCode() == null || reservation.entitlementCode().isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO billing_entitlement_usage "
                        + "(billing_tenant_id,billing_customer_id,entitlement_code,period_key,consumed_quantity) "
                        + "VALUES (:tenant,:customer,:entitlement,:period,:quantity) "
                        + "ON DUPLICATE KEY UPDATE consumed_quantity=consumed_quantity+:quantity",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("customer", reservation.billingCustomerId())
                        .addValue("entitlement", reservation.entitlementCode())
                        .addValue("period", periodKey())
                        .addValue("quantity", reservation.usageQuantity()));
    }

    private void reverseEntitlementUsage(long tenantId, ChargeView reservation) {
        if (reservation.entitlementCode() == null || reservation.entitlementCode().isBlank()) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE billing_entitlement_usage SET consumed_quantity=GREATEST(consumed_quantity-:quantity,0) "
                        + "WHERE billing_tenant_id=:tenant AND billing_customer_id=:customer "
                        + "AND entitlement_code=:entitlement AND period_key=:period",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("customer", reservation.billingCustomerId())
                        .addValue("entitlement", reservation.entitlementCode())
                        .addValue("period", periodKey())
                        .addValue("quantity", reservation.usageQuantity()));
    }

    private ChargeView lockReservation(long tenantId, String reservationReference) {
        return reservation(tenantId, reservationReference, true);
    }

    private ChargeView findByReference(long tenantId, String reservationReference) {
        return reservation(tenantId, reservationReference, false);
    }

    private ChargeView reservation(long tenantId, String reservationReference, boolean lock) {
        String sql =
                "SELECT r.reservation_reference,r.billing_customer_id,r.charging_account_id,"
                        + "ca.billing_account_id,r.service_code,r.entitlement_code,r.usage_quantity,r.currency,"
                        + "r.authorized_amount,r.authorized_net_amount,r.authorized_tax_amount,r.tax_rule_version_id,"
                        + "r.tax_code,r.tax_rate,r.committed_amount,r.committed_net_amount,r.committed_tax_amount,"
                        + "r.prepaid_committed_amount,r.credit_committed_amount,r.released_amount,r.status,"
                        + "r.idempotency_key,r.expires_at FROM billing_charge_reservations r "
                        + "JOIN billing_charging_accounts ca ON ca.id=r.charging_account_id "
                        + "WHERE r.billing_tenant_id=:tenant AND r.reservation_reference=:reservation"
                        + (lock ? " FOR UPDATE" : "");
        List<ChargeView> rows =
                jdbcTemplate.query(
                        sql,
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reservation", required(reservationReference, "reservationReference")),
                        (rs, rowNum) -> mapCharge(rs));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Charging reservation was not found for this tenant");
        }
        return rows.get(0);
    }

    private ChargeView findByIdempotency(long tenantId, String idempotencyKey) {
        List<String> refs =
                jdbcTemplate.query(
                        "SELECT reservation_reference FROM billing_charge_reservations "
                                + "WHERE billing_tenant_id=:tenant AND idempotency_key=:idempotency",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("idempotency", idempotencyKey),
                        (rs, rowNum) -> rs.getString(1));
        return refs.isEmpty() ? null : findByReference(tenantId, refs.get(0));
    }

    private ChargeView mapCharge(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object taxRuleId = rs.getObject("tax_rule_version_id");
        return new ChargeView(
                rs.getString("reservation_reference"),
                rs.getLong("billing_customer_id"),
                rs.getLong("charging_account_id"),
                rs.getLong("billing_account_id"),
                rs.getString("service_code"),
                rs.getString("entitlement_code"),
                rs.getBigDecimal("usage_quantity"),
                rs.getString("currency"),
                rs.getBigDecimal("authorized_amount"),
                rs.getBigDecimal("authorized_net_amount"),
                rs.getBigDecimal("authorized_tax_amount"),
                taxRuleId == null ? null : rs.getLong("tax_rule_version_id"),
                rs.getString("tax_code"),
                rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("committed_amount"),
                rs.getBigDecimal("committed_net_amount"),
                rs.getBigDecimal("committed_tax_amount"),
                rs.getBigDecimal("prepaid_committed_amount"),
                rs.getBigDecimal("credit_committed_amount"),
                rs.getBigDecimal("released_amount"),
                rs.getString("status"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("expires_at").toInstant());
    }

    private CommitView committedView(long tenantId, String reservationReference) {
        ChargeView charge = findByReference(tenantId, reservationReference);
        return new CommitView(
                charge.reservationReference(),
                charge.status(),
                charge.committedAmount(),
                charge.committedNetAmount(),
                charge.committedTaxAmount(),
                charge.prepaidCommittedAmount(),
                charge.creditCommittedAmount(),
                charge.currency());
    }

    private Split splitFunding(ChargeView reservation, BigDecimal fundingGross) {
        if (fundingGross.signum() == 0) {
            return new Split(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal.ZERO.setScale(MONEY_SCALE));
        }
        if (fundingGross.compareTo(reservation.authorizedAmount()) == 0) {
            return new Split(reservation.authorizedNetAmount(), reservation.authorizedTaxAmount());
        }
        BigDecimal tax =
                reservation
                        .authorizedTaxAmount()
                        .multiply(fundingGross)
                        .divide(reservation.authorizedAmount(), MONEY_SCALE, RoundingMode.HALF_UP);
        return new Split(fundingGross.subtract(tax), tax);
    }

    private void insertLedgerLink(
            long tenantId,
            String reservationReference,
            String fundingType,
            long ledgerTransactionId,
            String ledgerTransactionReference) {
        jdbcTemplate.update(
                "INSERT INTO billing_charge_ledger_links "
                        + "(billing_tenant_id,reservation_reference,funding_type,ledger_transaction_id,ledger_transaction_reference) "
                        + "VALUES (:tenant,:reservation,:funding,:ledger_id,:ledger_reference)",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("reservation", reservationReference)
                        .addValue("funding", fundingType)
                        .addValue("ledger_id", ledgerTransactionId)
                        .addValue("ledger_reference", ledgerTransactionReference));
    }

    private void reverseLedgerLinks(BillingBaasContext context, String reservationReference) {
        List<Map<String, Object>> links =
                jdbcTemplate.queryForList(
                        "SELECT id,funding_type,ledger_transaction_reference FROM billing_charge_ledger_links "
                                + "WHERE billing_tenant_id=:tenant AND reservation_reference=:reservation "
                                + "AND link_status='POSTED' ORDER BY id",
                        new MapSqlParameterSource()
                                .addValue("tenant", context.billingTenantId())
                                .addValue("reservation", reservationReference));
        for (Map<String, Object> link : links) {
            String funding = String.valueOf(link.get("funding_type"));
            String original = String.valueOf(link.get("ledger_transaction_reference"));
            String reversal = "billing-reversal:" + reservationReference + ":" + funding.toLowerCase();
            ledgerService.reverseBillingTransaction(
                    context.billingTenantId(),
                    original,
                    reversal,
                    reservationReference + ":" + funding.toLowerCase(),
                    "BaaS charge reversal " + reservationReference);
            jdbcTemplate.update(
                    "UPDATE billing_charge_ledger_links SET link_status='REVERSED',reversed_at=CURRENT_TIMESTAMP "
                            + "WHERE id=:id AND link_status='POSTED'",
                    new MapSqlParameterSource("id", link.get("id")));
        }
    }

    private void requireApprovedProtectedAction(long tenantId, String reservationReference) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_protected_action_requests "
                                + "WHERE billing_tenant_id=:tenant AND action_type='CHARGE_REVERSE' "
                                + "AND resource_type='CHARGE_RESERVATION' AND resource_reference=:reference "
                                + "AND status='APPROVED'",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", reservationReference),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException(
                    "Charging reversal requires an approved protected-action request");
        }
    }

    private void insertAdjustment(
            long tenantId,
            long accountId,
            String reservationReference,
            String type,
            BigDecimal prepaidDelta,
            BigDecimal creditDelta,
            BigDecimal reservedDelta,
            Long ledgerTransactionId,
            String idempotencyKey,
            String actor) {
        jdbcTemplate.update(
                "INSERT INTO billing_charging_adjustments "
                        + "(billing_tenant_id,charging_account_id,reservation_reference,adjustment_type,"
                        + "prepaid_delta,credit_used_delta,reserved_delta,ledger_transaction_id,idempotency_key,created_by) "
                        + "VALUES (:tenant,:account,:reservation,:type,:prepaid,:credit,:reserved,:ledger,:idempotency,:actor)",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("account", accountId)
                        .addValue("reservation", reservationReference)
                        .addValue("type", type)
                        .addValue("prepaid", prepaidDelta)
                        .addValue("credit", creditDelta)
                        .addValue("reserved", reservedDelta)
                        .addValue("ledger", ledgerTransactionId)
                        .addValue("idempotency", idempotencyKey)
                        .addValue("actor", actor));
    }

    private String periodKey() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }

    private BigDecimal money(BigDecimal value, String field) {
        return positive(value, field).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }

    private record BillingAccount(long id, long customerId, String currency, BigDecimal creditLimit) {}

    private record ChargingAccount(
            long id,
            BigDecimal prepaidBalance,
            BigDecimal creditLimit,
            BigDecimal creditUsed,
            BigDecimal reservedAmount,
            String status) {}

    private record TaxRule(long id, String taxCode, BigDecimal rate) {}

    private record Split(BigDecimal net, BigDecimal tax) {}

    public record ChargeView(
            String reservationReference,
            long billingCustomerId,
            long chargingAccountId,
            long billingAccountId,
            String serviceCode,
            String entitlementCode,
            BigDecimal usageQuantity,
            String currency,
            BigDecimal authorizedAmount,
            BigDecimal authorizedNetAmount,
            BigDecimal authorizedTaxAmount,
            Long taxRuleVersionId,
            String taxCode,
            BigDecimal taxRate,
            BigDecimal committedAmount,
            BigDecimal committedNetAmount,
            BigDecimal committedTaxAmount,
            BigDecimal prepaidCommittedAmount,
            BigDecimal creditCommittedAmount,
            BigDecimal releasedAmount,
            String status,
            String idempotencyKey,
            Instant expiresAt) {}

    public record CommitView(
            String reservationReference,
            String status,
            BigDecimal committedAmount,
            BigDecimal committedNetAmount,
            BigDecimal committedTaxAmount,
            BigDecimal prepaidCommittedAmount,
            BigDecimal creditCommittedAmount,
            String currency) {}
}
