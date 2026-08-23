package net.citotech.cito.recurring;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.AdapterNativePaymentService;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecurringPaymentService {
    private static final Set<String> INTERVAL_UNITS = Set.of("DAY", "WEEK", "MONTH");
    private static final Set<String> SUBSCRIPTION_STATUSES =
            Set.of("ACTIVE", "PAUSED", "PAST_DUE", "CANCELLED", "COMPLETED");
    private static final Set<String> MANDATE_MODES = Set.of("REQUEST_TO_PAY", "PROVIDER_MANDATE");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdapterNativePaymentService nativePaymentService;

    public RecurringPaymentService(
            NamedParameterJdbcTemplate jdbcTemplate,
            AdapterNativePaymentService nativePaymentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.nativePaymentService = nativePaymentService;
    }

    @Transactional
    public Map<String, Object> createPlan(
            long merchantId,
            String planName,
            BigDecimal amount,
            String currencyCode,
            String intervalUnit,
            int intervalCount,
            int retryCount,
            int retryIntervalHours,
            int gracePeriodDays,
            String actor) {
        requireMerchant(merchantId);
        BigDecimal price = positive(amount, "amount");
        String unit = normalize(intervalUnit, INTERVAL_UNITS, "intervalUnit");
        String reference = reference("PLAN");
        jdbcTemplate.update(
                "INSERT INTO recurring_plans "
                        + "(merchant_id, plan_reference, plan_name, amount, currency_code, interval_unit, interval_count, "
                        + "retry_count, retry_interval_hours, grace_period_days, status, created_by) "
                        + "VALUES (:merchant_id, :reference, :plan_name, :amount, :currency_code, :interval_unit, :interval_count, "
                        + ":retry_count, :retry_interval_hours, :grace_period_days, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("plan_name", required(planName, "planName"))
                        .addValue("amount", price)
                        .addValue("currency_code", currency(currencyCode))
                        .addValue("interval_unit", unit)
                        .addValue("interval_count", Math.max(1, intervalCount))
                        .addValue("retry_count", Math.max(0, retryCount))
                        .addValue("retry_interval_hours", Math.max(1, retryIntervalHours))
                        .addValue("grace_period_days", Math.max(0, gracePeriodDays))
                        .addValue("created_by", blankToNull(actor)));
        return plan(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> createMandate(
            long merchantId,
            String customerReference,
            String payerType,
            String payerValue,
            String channelCode,
            String countryCode,
            String currencyCode,
            String environment,
            String executionMode,
            String providerMandateReference,
            String consentReference,
            Instant expiresAt) {
        requireMerchant(merchantId);
        String mode = normalize(executionMode, MANDATE_MODES, "executionMode");
        if ("PROVIDER_MANDATE".equals(mode)
                && (providerMandateReference == null || providerMandateReference.isBlank())) {
            throw new PaymentGatewayException(
                    "providerMandateReference is required for PROVIDER_MANDATE execution");
        }
        String reference = reference("MANDATE");
        jdbcTemplate.update(
                "INSERT INTO payment_mandates "
                        + "(merchant_id, mandate_reference, customer_reference, payer_type, payer_value, channel_code, country_code, "
                        + "currency_code, environment, execution_mode, provider_mandate_reference, consent_reference, status, expires_at) "
                        + "VALUES (:merchant_id, :reference, :customer_reference, :payer_type, :payer_value, :channel_code, :country_code, "
                        + ":currency_code, :environment, :execution_mode, :provider_mandate_reference, :consent_reference, 'ACTIVE', :expires_at)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("customer_reference", blankToNull(customerReference))
                        .addValue(
                                "payer_type",
                                payerType == null || payerType.isBlank()
                                        ? "MSISDN"
                                        : payerType.trim().toUpperCase(Locale.ROOT))
                        .addValue("payer_value", required(payerValue, "payerValue"))
                        .addValue("channel_code", blankToNull(channelCode))
                        .addValue("country_code", country(countryCode))
                        .addValue("currency_code", currency(currencyCode))
                        .addValue("environment", environment(environment))
                        .addValue("execution_mode", mode)
                        .addValue("provider_mandate_reference", blankToNull(providerMandateReference))
                        .addValue("consent_reference", required(consentReference, "consentReference"))
                        .addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt)));
        return mandate(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> createSubscription(
            long merchantId,
            String planReference,
            String mandateReference,
            String customerReference,
            Instant startAt,
            Instant endAt,
            String actor) {
        requireMerchant(merchantId);
        Plan plan = loadPlan(merchantId, planReference);
        Mandate mandate = loadMandate(merchantId, mandateReference);
        if (!plan.currencyCode().equals(mandate.currencyCode())) {
            throw new PaymentGatewayException("Plan and mandate currencies must match");
        }
        Instant start = startAt == null ? Instant.now() : startAt;
        if (endAt != null && !endAt.isAfter(start)) {
            throw new PaymentGatewayException("endAt must be after startAt");
        }
        String reference = reference("SUBSCRIPTION");
        jdbcTemplate.update(
                "INSERT INTO recurring_subscriptions "
                        + "(merchant_id, subscription_reference, plan_id, mandate_id, customer_reference, status, start_at, next_charge_at, end_at, created_by) "
                        + "VALUES (:merchant_id, :reference, :plan_id, :mandate_id, :customer_reference, 'ACTIVE', :start_at, :next_charge_at, :end_at, :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("plan_id", plan.id())
                        .addValue("mandate_id", mandate.id())
                        .addValue("customer_reference", blankToNull(customerReference))
                        .addValue("start_at", Timestamp.from(start))
                        .addValue("next_charge_at", Timestamp.from(start))
                        .addValue("end_at", endAt == null ? null : Timestamp.from(endAt))
                        .addValue("created_by", blankToNull(actor)));
        return subscription(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> setSubscriptionStatus(
            long merchantId, String subscriptionReference, String status) {
        String normalized = normalize(status, SUBSCRIPTION_STATUSES, "status");
        if ("ACTIVE".equals(normalized)) {
            int updated =
                    jdbcTemplate.update(
                            "UPDATE recurring_subscriptions SET status='ACTIVE', paused_at=NULL, "
                                    + "next_charge_at=CASE WHEN next_charge_at<CURRENT_TIMESTAMP THEN CURRENT_TIMESTAMP ELSE next_charge_at END "
                                    + "WHERE merchant_id=:merchant_id AND subscription_reference=:reference AND status IN ('PAUSED','PAST_DUE','ACTIVE')",
                            new MapSqlParameterSource()
                                    .addValue("merchant_id", merchantId)
                                    .addValue("reference", subscriptionReference));
            if (updated == 0) {
                throw new PaymentGatewayException("Subscription cannot be activated");
            }
        } else if ("PAUSED".equals(normalized)) {
            updateSubscriptionStatus(merchantId, subscriptionReference, "PAUSED", "paused_at");
        } else if ("CANCELLED".equals(normalized)) {
            updateSubscriptionStatus(merchantId, subscriptionReference, "CANCELLED", "cancelled_at");
        } else {
            updateSubscriptionStatus(merchantId, subscriptionReference, normalized, null);
        }
        return subscription(merchantId, subscriptionReference);
    }

    public List<Map<String, Object>> plans(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT plan_reference AS planReference, plan_name AS planName, amount, currency_code AS currencyCode, "
                        + "interval_unit AS intervalUnit, interval_count AS intervalCount, retry_count AS retryCount, "
                        + "retry_interval_hours AS retryIntervalHours, grace_period_days AS gracePeriodDays, status, created_at AS createdAt "
                        + "FROM recurring_plans WHERE merchant_id=:merchant_id ORDER BY id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    public List<Map<String, Object>> mandates(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT mandate_reference AS mandateReference, customer_reference AS customerReference, payer_type AS payerType, "
                        + "payer_value AS payerValue, channel_code AS channelCode, country_code AS countryCode, currency_code AS currencyCode, "
                        + "environment, execution_mode AS executionMode, provider_mandate_reference AS providerMandateReference, "
                        + "consent_reference AS consentReference, status, authorized_at AS authorizedAt, expires_at AS expiresAt "
                        + "FROM payment_mandates WHERE merchant_id=:merchant_id ORDER BY id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    public List<Map<String, Object>> subscriptions(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT s.subscription_reference AS subscriptionReference, p.plan_reference AS planReference, "
                        + "m.mandate_reference AS mandateReference, s.customer_reference AS customerReference, s.status, "
                        + "s.start_at AS startAt, s.next_charge_at AS nextChargeAt, s.last_charge_at AS lastChargeAt, "
                        + "s.end_at AS endAt, s.paused_at AS pausedAt, s.cancelled_at AS cancelledAt, s.created_at AS createdAt "
                        + "FROM recurring_subscriptions s JOIN recurring_plans p ON p.id=s.plan_id "
                        + "JOIN payment_mandates m ON m.id=s.mandate_id WHERE s.merchant_id=:merchant_id ORDER BY s.id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    public List<Map<String, Object>> charges(long merchantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT c.charge_reference AS chargeReference, s.subscription_reference AS subscriptionReference, "
                        + "c.due_at AS dueAt, c.amount, c.currency_code AS currencyCode, c.status, c.attempt_count AS attemptCount, "
                        + "c.next_attempt_at AS nextAttemptAt, c.payment_reference AS paymentReference, c.payment_status AS paymentStatus, "
                        + "c.failure_message AS failureMessage, c.created_at AS createdAt, c.completed_at AS completedAt "
                        + "FROM recurring_scheduled_charges c JOIN recurring_subscriptions s ON s.id=c.subscription_id "
                        + "WHERE s.merchant_id=:merchant_id ORDER BY c.id DESC LIMIT "
                        + safeLimit,
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Scheduled(fixedDelayString = "${cpay.recurring.schedule-delay-ms:60000}")
    @SchedulerLock(
            name = "recurringPaymentSchedule",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT5S")
    public void scheduleDueCharges() {
        List<Map<String, Object>> due =
                jdbcTemplate.queryForList(
                        "SELECT s.id AS subscription_id, s.next_charge_at, p.amount, p.currency_code "
                                + "FROM recurring_subscriptions s JOIN recurring_plans p ON p.id=s.plan_id "
                                + "JOIN payment_mandates m ON m.id=s.mandate_id "
                                + "WHERE s.status='ACTIVE' AND p.status='ACTIVE' AND m.status='ACTIVE' "
                                + "AND (m.expires_at IS NULL OR m.expires_at>CURRENT_TIMESTAMP) "
                                + "AND s.next_charge_at<=CURRENT_TIMESTAMP "
                                + "AND (s.end_at IS NULL OR s.end_at>CURRENT_TIMESTAMP) LIMIT 200",
                        new MapSqlParameterSource());
        for (Map<String, Object> row : due) {
            long subscriptionId = ((Number) row.get("subscription_id")).longValue();
            Instant dueAt = ((java.sql.Timestamp) row.get("next_charge_at")).toInstant();
            String chargeReference =
                    "RCH-" + subscriptionId + "-" + Long.toUnsignedString(dueAt.getEpochSecond(), 36);
            jdbcTemplate.update(
                    "INSERT IGNORE INTO recurring_scheduled_charges "
                            + "(subscription_id, charge_reference, due_at, amount, currency_code, status, next_attempt_at) "
                            + "VALUES (:subscription_id, :charge_reference, :due_at, :amount, :currency_code, 'PENDING', :due_at)",
                    new MapSqlParameterSource()
                            .addValue("subscription_id", subscriptionId)
                            .addValue("charge_reference", chargeReference)
                            .addValue("due_at", Timestamp.from(dueAt))
                            .addValue("amount", row.get("amount"))
                            .addValue("currency_code", row.get("currency_code")));
        }
    }

    @Scheduled(fixedDelayString = "${cpay.recurring.execute-delay-ms:45000}")
    @SchedulerLock(
            name = "recurringPaymentExecute",
            lockAtMostFor = "PT3M",
            lockAtLeastFor = "PT5S")
    public void executeDueCharges() {
        List<Long> ids =
                jdbcTemplate.query(
                        "SELECT id FROM recurring_scheduled_charges WHERE status IN ('PENDING','RETRY_SCHEDULED') "
                                + "AND COALESCE(next_attempt_at,due_at)<=CURRENT_TIMESTAMP ORDER BY id LIMIT 100",
                        new MapSqlParameterSource(),
                        (rs, rowNum) -> rs.getLong("id"));
        for (Long id : ids) {
            processCharge(id);
        }
    }

    void processCharge(long chargeId) {
        int claimed =
                jdbcTemplate.update(
                        "UPDATE recurring_scheduled_charges SET status='PROCESSING' "
                                + "WHERE id=:id AND status IN ('PENDING','RETRY_SCHEDULED')",
                        new MapSqlParameterSource("id", chargeId));
        if (claimed == 0) {
            return;
        }
        ChargeContext ctx = loadChargeContext(chargeId);
        int attemptNumber = ctx.attemptCount() + 1;
        String paymentReference = ctx.chargeReference() + "-A" + attemptNumber;
        try {
            Merchant merchant = Common.getMerchantById(String.valueOf(ctx.merchantId()), jdbcTemplate);
            if (merchant == null) {
                throw new PaymentGatewayException("Subscription merchant was not found");
            }
            PaymentRequest request = new PaymentRequest();
            request.setMerchantNumber(merchant.getAccount_number());
            request.setAmount(ctx.amount().toPlainString());
            request.setCurrency(ctx.currencyCode());
            request.setCountry(ctx.countryCode());
            request.setChannel(ctx.channelCode());
            request.setReference(paymentReference);
            request.setDescription("Recurring charge " + ctx.subscriptionReference());
            PaymentPartyRequest payer = new PaymentPartyRequest();
            payer.setType(ctx.payerType());
            payer.setValue(ctx.payerValue());
            request.setPayer(payer);
            request.setMetadata(
                    Map.of(
                            "environment",
                            ctx.environment(),
                            "subscriptionReference",
                            ctx.subscriptionReference(),
                            "mandateReference",
                            ctx.mandateReference()));
            PaymentResult result =
                    nativePaymentService.collect(request, merchant, ctx.environment());
            if (successful(result.getStatus())) {
                recordDunning(
                        chargeId,
                        attemptNumber,
                        "SUCCESS",
                        paymentReference,
                        result.getStatus(),
                        null);
                Instant nextChargeAt =
                        nextCharge(ctx.dueAt(), ctx.intervalUnit(), ctx.intervalCount());
                jdbcTemplate.update(
                        "UPDATE recurring_scheduled_charges SET status='SUCCESS', attempt_count=:attempt_count, "
                                + "payment_reference=:payment_reference, payment_status=:payment_status, failure_message=NULL, completed_at=CURRENT_TIMESTAMP "
                                + "WHERE id=:id",
                        new MapSqlParameterSource()
                                .addValue("id", chargeId)
                                .addValue("attempt_count", attemptNumber)
                                .addValue("payment_reference", paymentReference)
                                .addValue("payment_status", result.getStatus()));
                jdbcTemplate.update(
                        "UPDATE recurring_subscriptions SET last_charge_at=CURRENT_TIMESTAMP, next_charge_at=:next_charge_at, status='ACTIVE' "
                                + "WHERE id=:subscription_id",
                        new MapSqlParameterSource()
                                .addValue("subscription_id", ctx.subscriptionId())
                                .addValue("next_charge_at", Timestamp.from(nextChargeAt)));
                return;
            }
            handleChargeFailure(
                    ctx, chargeId, attemptNumber, paymentReference, result.getStatus(), result.getMessage());
        } catch (RuntimeException e) {
            handleChargeFailure(ctx, chargeId, attemptNumber, paymentReference, null, e.getMessage());
        }
    }

    private void handleChargeFailure(
            ChargeContext ctx,
            long chargeId,
            int attemptNumber,
            String paymentReference,
            String providerStatus,
            String failureMessage) {
        recordDunning(
                chargeId,
                attemptNumber,
                "FAILED",
                paymentReference,
                providerStatus,
                failureMessage);
        if (attemptNumber <= ctx.retryCount()) {
            Instant retryAt = Instant.now().plusSeconds(ctx.retryIntervalHours() * 3600L);
            jdbcTemplate.update(
                    "UPDATE recurring_scheduled_charges SET status='RETRY_SCHEDULED', attempt_count=:attempt_count, "
                            + "next_attempt_at=:next_attempt_at, payment_reference=:payment_reference, payment_status=:payment_status, "
                            + "failure_message=:failure_message WHERE id=:id",
                    new MapSqlParameterSource()
                            .addValue("id", chargeId)
                            .addValue("attempt_count", attemptNumber)
                            .addValue("next_attempt_at", Timestamp.from(retryAt))
                            .addValue("payment_reference", paymentReference)
                            .addValue("payment_status", blankToNull(providerStatus))
                            .addValue("failure_message", safeMessage(failureMessage)));
            return;
        }
        jdbcTemplate.update(
                "UPDATE recurring_scheduled_charges SET status='FAILED', attempt_count=:attempt_count, "
                        + "payment_reference=:payment_reference, payment_status=:payment_status, failure_message=:failure_message, "
                        + "completed_at=CURRENT_TIMESTAMP WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("id", chargeId)
                        .addValue("attempt_count", attemptNumber)
                        .addValue("payment_reference", paymentReference)
                        .addValue("payment_status", blankToNull(providerStatus))
                        .addValue("failure_message", safeMessage(failureMessage)));
        jdbcTemplate.update(
                "UPDATE recurring_subscriptions SET status='PAST_DUE' WHERE id=:subscription_id AND status='ACTIVE'",
                new MapSqlParameterSource("subscription_id", ctx.subscriptionId()));
    }

    private ChargeContext loadChargeContext(long chargeId) {
        List<ChargeContext> rows =
                jdbcTemplate.query(
                        "SELECT c.id, c.charge_reference, c.due_at, c.amount, c.currency_code, c.attempt_count, "
                                + "s.id AS subscription_id, s.subscription_reference, s.merchant_id, "
                                + "p.interval_unit, p.interval_count, p.retry_count, p.retry_interval_hours, "
                                + "m.mandate_reference, m.payer_type, m.payer_value, m.channel_code, m.country_code, m.environment "
                                + "FROM recurring_scheduled_charges c JOIN recurring_subscriptions s ON s.id=c.subscription_id "
                                + "JOIN recurring_plans p ON p.id=s.plan_id JOIN payment_mandates m ON m.id=s.mandate_id "
                                + "WHERE c.id=:id AND s.status IN ('ACTIVE','PAST_DUE') AND m.status='ACTIVE'",
                        new MapSqlParameterSource("id", chargeId),
                        (rs, rowNum) ->
                                new ChargeContext(
                                        rs.getLong("id"),
                                        rs.getLong("subscription_id"),
                                        rs.getString("charge_reference"),
                                        rs.getTimestamp("due_at").toInstant(),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency_code"),
                                        rs.getInt("attempt_count"),
                                        rs.getString("subscription_reference"),
                                        rs.getLong("merchant_id"),
                                        rs.getString("interval_unit"),
                                        rs.getInt("interval_count"),
                                        rs.getInt("retry_count"),
                                        rs.getInt("retry_interval_hours"),
                                        rs.getString("mandate_reference"),
                                        rs.getString("payer_type"),
                                        rs.getString("payer_value"),
                                        rs.getString("channel_code"),
                                        rs.getString("country_code"),
                                        rs.getString("environment")));
        if (rows.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE recurring_scheduled_charges SET status='FAILED', failure_message='Subscription or mandate is not executable', completed_at=CURRENT_TIMESTAMP WHERE id=:id",
                    new MapSqlParameterSource("id", chargeId));
            throw new PaymentGatewayException("Subscription or mandate is not executable");
        }
        return rows.get(0);
    }

    private void recordDunning(
            long chargeId,
            int attemptNumber,
            String outcome,
            String paymentReference,
            String providerStatus,
            String failureMessage) {
        jdbcTemplate.update(
                "INSERT INTO recurring_dunning_attempts "
                        + "(charge_id, attempt_number, outcome, payment_reference, provider_status, failure_message) "
                        + "VALUES (:charge_id, :attempt_number, :outcome, :payment_reference, :provider_status, :failure_message)",
                new MapSqlParameterSource()
                        .addValue("charge_id", chargeId)
                        .addValue("attempt_number", attemptNumber)
                        .addValue("outcome", outcome)
                        .addValue("payment_reference", paymentReference)
                        .addValue("provider_status", blankToNull(providerStatus))
                        .addValue("failure_message", safeMessage(failureMessage)));
    }

    private void updateSubscriptionStatus(
            long merchantId, String subscriptionReference, String status, String timestampColumn) {
        String timestampUpdate =
                timestampColumn == null ? "" : ", " + timestampColumn + "=CURRENT_TIMESTAMP";
        int updated =
                jdbcTemplate.update(
                        "UPDATE recurring_subscriptions SET status=:status"
                                + timestampUpdate
                                + " WHERE merchant_id=:merchant_id AND subscription_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(subscriptionReference, "subscriptionReference"))
                                .addValue("status", status));
        if (updated == 0) {
            throw new PaymentGatewayException("Subscription was not found");
        }
    }

    private Map<String, Object> plan(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT plan_reference AS planReference, plan_name AS planName, amount, currency_code AS currencyCode, "
                        + "interval_unit AS intervalUnit, interval_count AS intervalCount, retry_count AS retryCount, "
                        + "retry_interval_hours AS retryIntervalHours, grace_period_days AS gracePeriodDays, status, created_at AS createdAt "
                        + "FROM recurring_plans WHERE merchant_id=:merchant_id AND plan_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private Map<String, Object> mandate(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT mandate_reference AS mandateReference, customer_reference AS customerReference, payer_type AS payerType, "
                        + "payer_value AS payerValue, channel_code AS channelCode, country_code AS countryCode, currency_code AS currencyCode, "
                        + "environment, execution_mode AS executionMode, provider_mandate_reference AS providerMandateReference, "
                        + "consent_reference AS consentReference, status, authorized_at AS authorizedAt, expires_at AS expiresAt "
                        + "FROM payment_mandates WHERE merchant_id=:merchant_id AND mandate_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private Map<String, Object> subscription(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT s.subscription_reference AS subscriptionReference, p.plan_reference AS planReference, "
                        + "m.mandate_reference AS mandateReference, s.customer_reference AS customerReference, s.status, "
                        + "s.start_at AS startAt, s.next_charge_at AS nextChargeAt, s.last_charge_at AS lastChargeAt, "
                        + "s.end_at AS endAt, s.paused_at AS pausedAt, s.cancelled_at AS cancelledAt, s.created_at AS createdAt "
                        + "FROM recurring_subscriptions s JOIN recurring_plans p ON p.id=s.plan_id "
                        + "JOIN payment_mandates m ON m.id=s.mandate_id WHERE s.merchant_id=:merchant_id AND s.subscription_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private Plan loadPlan(long merchantId, String reference) {
        List<Plan> rows =
                jdbcTemplate.query(
                        "SELECT id, amount, currency_code, interval_unit, interval_count, retry_count, retry_interval_hours "
                                + "FROM recurring_plans WHERE merchant_id=:merchant_id AND plan_reference=:reference AND status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(reference, "planReference")),
                        (rs, rowNum) ->
                                new Plan(
                                        rs.getLong("id"),
                                        rs.getBigDecimal("amount"),
                                        rs.getString("currency_code"),
                                        rs.getString("interval_unit"),
                                        rs.getInt("interval_count"),
                                        rs.getInt("retry_count"),
                                        rs.getInt("retry_interval_hours")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Recurring plan was not found or is inactive");
        }
        return rows.get(0);
    }

    private Mandate loadMandate(long merchantId, String reference) {
        List<Mandate> rows =
                jdbcTemplate.query(
                        "SELECT id, currency_code FROM payment_mandates WHERE merchant_id=:merchant_id "
                                + "AND mandate_reference=:reference AND status='ACTIVE' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(reference, "mandateReference")),
                        (rs, rowNum) ->
                                new Mandate(rs.getLong("id"), rs.getString("currency_code")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Payment mandate was not found, inactive, or expired");
        }
        return rows.get(0);
    }

    private Instant nextCharge(Instant from, String unit, int count) {
        ZonedDateTime value = ZonedDateTime.ofInstant(from, ZoneOffset.UTC);
        return switch (unit) {
                    case "DAY" -> value.plusDays(count);
                    case "WEEK" -> value.plusWeeks(count);
                    case "MONTH" -> value.plusMonths(count);
                    default -> throw new PaymentGatewayException("Unsupported recurring interval");
                }
                .toInstant();
    }

    private boolean successful(String value) {
        if (value == null) {
            return false;
        }
        return Set.of("SUCCESS", "SUCCESSFUL", "COMPLETED", "COMPLETE", "000")
                .contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private String normalize(String value, Set<String> allowed, String field) {
        String normalized = required(value, field).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new PaymentGatewayException("Unsupported " + field);
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

    private String country(String value) {
        String normalized = required(value, "countryCode").toUpperCase(Locale.ROOT);
        if (normalized.length() < 2 || normalized.length() > 3) {
            throw new PaymentGatewayException("countryCode must use ISO country format");
        }
        return normalized;
    }

    private String environment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
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

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String safeMessage(String value) {
        if (value == null || value.isBlank()) {
            return "Recurring charge failed";
        }
        String trimmed = value.trim();
        return trimmed.length() > 950 ? trimmed.substring(0, 950) : trimmed;
    }

    private record Plan(
            long id,
            BigDecimal amount,
            String currencyCode,
            String intervalUnit,
            int intervalCount,
            int retryCount,
            int retryIntervalHours) {}

    private record Mandate(long id, String currencyCode) {}

    private record ChargeContext(
            long chargeId,
            long subscriptionId,
            String chargeReference,
            Instant dueAt,
            BigDecimal amount,
            String currencyCode,
            int attemptCount,
            String subscriptionReference,
            long merchantId,
            String intervalUnit,
            int intervalCount,
            int retryCount,
            int retryIntervalHours,
            String mandateReference,
            String payerType,
            String payerValue,
            String channelCode,
            String countryCode,
            String environment) {}
}