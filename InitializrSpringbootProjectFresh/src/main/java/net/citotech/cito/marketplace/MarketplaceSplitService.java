package net.citotech.cito.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketplaceSplitService {
    private static final Set<String> MODES = Set.of("PERCENTAGE", "FIXED");
    private static final Set<String> FEE_BEARERS = Set.of("PLATFORM", "RECIPIENTS");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MarketplaceSplitService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> createSubaccount(
            long merchantId,
            String displayName,
            String currencyCode,
            String destinationType,
            String destinationReference,
            String actor) {
        requireMerchant(merchantId);
        String reference =
                "SUB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String currency = required(currencyCode, "currencyCode").toUpperCase(Locale.ROOT);
        if (currency.length() != 3) {
            throw new PaymentGatewayException("currencyCode must use ISO-4217 format");
        }
        jdbcTemplate.update(
                "INSERT INTO marketplace_subaccounts "
                        + "(merchant_id, subaccount_reference, display_name, currency_code, destination_type, destination_reference, status, created_by) "
                        + "VALUES (:merchant_id, :reference, :display_name, :currency_code, :destination_type, :destination_reference, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("display_name", required(displayName, "displayName"))
                        .addValue("currency_code", currency)
                        .addValue(
                                "destination_type",
                                destinationType == null || destinationType.isBlank()
                                        ? "INTERNAL_LEDGER"
                                        : destinationType.trim().toUpperCase(Locale.ROOT))
                        .addValue("destination_reference", blankToNull(destinationReference))
                        .addValue("created_by", blankToNull(actor)));
        return subaccount(merchantId, reference);
    }

    public List<Map<String, Object>> subaccounts(long merchantId) {
        requireMerchant(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT id, subaccount_reference AS subaccountReference, display_name AS displayName, "
                        + "currency_code AS currencyCode, destination_type AS destinationType, "
                        + "destination_reference AS destinationReference, status, created_at AS createdAt, updated_at AS updatedAt "
                        + "FROM marketplace_subaccounts WHERE merchant_id=:merchant_id ORDER BY id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public Map<String, Object> setSubaccountStatus(
            long merchantId, String reference, String status) {
        String normalized = required(status, "status").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "SUSPENDED", "CLOSED").contains(normalized)) {
            throw new PaymentGatewayException("Unsupported subaccount status");
        }
        int updated =
                jdbcTemplate.update(
                        "UPDATE marketplace_subaccounts SET status=:status, updated_at=CURRENT_TIMESTAMP "
                                + "WHERE merchant_id=:merchant_id AND subaccount_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(reference, "subaccountReference"))
                                .addValue("status", normalized));
        if (updated == 0) {
            throw new PaymentGatewayException("Subaccount was not found");
        }
        return subaccount(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> createSplitRule(
            long merchantId,
            String ruleName,
            String currencyCode,
            String allocationMode,
            String platformFeeType,
            BigDecimal platformFeeValue,
            String feeBearer,
            List<Map<String, Object>> recipients,
            String actor) {
        requireMerchant(merchantId);
        String mode = normalizeMode(allocationMode);
        String feeType = normalizeMode(platformFeeType);
        String bearer =
                feeBearer == null || feeBearer.isBlank()
                        ? "PLATFORM"
                        : feeBearer.trim().toUpperCase(Locale.ROOT);
        if (!FEE_BEARERS.contains(bearer)) {
            throw new PaymentGatewayException("feeBearer must be PLATFORM or RECIPIENTS");
        }
        BigDecimal feeValue = nonNegative(platformFeeValue, "platformFeeValue");
        if ("PERCENTAGE".equals(feeType) && feeValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PaymentGatewayException("Percentage platform fee cannot exceed 100");
        }
        List<RecipientInput> validatedRecipients =
                validateRecipients(merchantId, mode, recipients);
        String reference =
                "SPLIT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO marketplace_split_rules "
                        + "(merchant_id, split_rule_reference, rule_name, currency_code, allocation_mode, platform_fee_type, "
                        + "platform_fee_value, fee_bearer, status, created_by) "
                        + "VALUES (:merchant_id, :reference, :rule_name, :currency_code, :allocation_mode, :platform_fee_type, "
                        + ":platform_fee_value, :fee_bearer, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("rule_name", required(ruleName, "ruleName"))
                        .addValue(
                                "currency_code",
                                required(currencyCode, "currencyCode").toUpperCase(Locale.ROOT))
                        .addValue("allocation_mode", mode)
                        .addValue("platform_fee_type", feeType)
                        .addValue("platform_fee_value", feeValue)
                        .addValue("fee_bearer", bearer)
                        .addValue("created_by", blankToNull(actor)));
        Long ruleId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM marketplace_split_rules WHERE split_rule_reference=:reference",
                        new MapSqlParameterSource("reference", reference),
                        Long.class);
        if (ruleId == null) {
            throw new PaymentGatewayException("Unable to create split rule");
        }
        for (RecipientInput recipient : validatedRecipients) {
            jdbcTemplate.update(
                    "INSERT INTO marketplace_split_recipients "
                            + "(split_rule_id, subaccount_id, allocation_value, priority_rank, status) "
                            + "VALUES (:split_rule_id, :subaccount_id, :allocation_value, :priority_rank, 'ACTIVE')",
                    new MapSqlParameterSource()
                            .addValue("split_rule_id", ruleId)
                            .addValue("subaccount_id", recipient.subaccountId())
                            .addValue("allocation_value", recipient.allocationValue())
                            .addValue("priority_rank", recipient.priorityRank()));
        }
        return splitRule(merchantId, reference);
    }

    public List<Map<String, Object>> splitRules(long merchantId) {
        requireMerchant(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT split_rule_reference AS splitRuleReference, rule_name AS ruleName, currency_code AS currencyCode, "
                        + "allocation_mode AS allocationMode, platform_fee_type AS platformFeeType, platform_fee_value AS platformFeeValue, "
                        + "fee_bearer AS feeBearer, status, created_at AS createdAt, updated_at AS updatedAt "
                        + "FROM marketplace_split_rules WHERE merchant_id=:merchant_id ORDER BY id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public Map<String, Object> captureForPayment(
            Merchant merchant, PaymentRequest request, PaymentResult result) {
        if (merchant == null || merchant.getId() == null || request == null || result == null) {
            return Map.of();
        }
        String splitReference =
                request.getMetadata() == null
                        ? null
                        : request.getMetadata().get("splitRuleReference");
        if (splitReference == null || splitReference.isBlank()) {
            return Map.of();
        }
        if (!successful(result.getStatus())) {
            return Map.of();
        }
        return executeSplit(
                merchant.getId(),
                request.getReference(),
                splitReference,
                request.getCurrency(),
                amount(request.getAmount()));
    }

    @Transactional
    public Map<String, Object> executeSplit(
            long merchantId,
            String transactionReference,
            String splitRuleReference,
            String currencyCode,
            BigDecimal grossAmount) {
        BigDecimal gross = positive(grossAmount, "grossAmount");
        SplitRule rule = loadRule(merchantId, splitRuleReference);
        if (!rule.currencyCode().equalsIgnoreCase(required(currencyCode, "currencyCode"))) {
            throw new PaymentGatewayException("Split rule currency does not match payment currency");
        }
        List<SplitRecipient> recipients = recipients(rule.id(), merchantId);
        if (recipients.isEmpty()) {
            throw new PaymentGatewayException("Split rule has no active recipients");
        }
        BigDecimal configuredFee =
                "PERCENTAGE".equals(rule.platformFeeType())
                        ? gross.multiply(rule.platformFeeValue())
                                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                        : rule.platformFeeValue();
        if (configuredFee.compareTo(gross) > 0) {
            throw new PaymentGatewayException("Platform fee exceeds gross payment amount");
        }
        BigDecimal distributable = gross.subtract(configuredFee).setScale(6, RoundingMode.HALF_UP);
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (SplitRecipient recipient : recipients) {
            BigDecimal allocation =
                    "PERCENTAGE".equals(rule.allocationMode())
                            ? distributable
                                    .multiply(recipient.allocationValue())
                                    .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                            : recipient.allocationValue().setScale(6, RoundingMode.HALF_UP);
            allocations.add(new Allocation(recipient, allocation));
            allocated = allocated.add(allocation);
        }
        BigDecimal roundingDifference = distributable.subtract(allocated);
        if ("PERCENTAGE".equals(rule.allocationMode())
                && roundingDifference.abs().compareTo(new BigDecimal("0.000010")) <= 0
                && !allocations.isEmpty()) {
            Allocation last = allocations.remove(allocations.size() - 1);
            allocations.add(
                    new Allocation(last.recipient(), last.amount().add(roundingDifference)));
            allocated = distributable;
        }
        if (allocated.compareTo(distributable) != 0) {
            throw new PaymentGatewayException(
                    "Split allocations must exactly equal the distributable payment amount");
        }

        List<Map<String, Object>> existing =
                jdbcTemplate.queryForList(
                        "SELECT execution_reference AS executionReference, status, gross_amount AS grossAmount, "
                                + "platform_fee_amount AS platformFeeAmount, distributable_amount AS distributableAmount, "
                                + "allocated_amount AS allocatedAmount FROM marketplace_split_executions "
                                + "WHERE merchant_id=:merchant_id AND transaction_reference=:transaction_reference "
                                + "AND split_rule_reference=:split_rule_reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("transaction_reference", transactionReference)
                                .addValue("split_rule_reference", splitRuleReference));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        String executionReference =
                "SPX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String snapshot = snapshot(rule, allocations, gross, configuredFee, distributable);
        jdbcTemplate.update(
                "INSERT INTO marketplace_split_executions "
                        + "(merchant_id, execution_reference, transaction_reference, split_rule_reference, currency_code, gross_amount, "
                        + "platform_fee_amount, distributable_amount, allocated_amount, status, snapshot_json) "
                        + "VALUES (:merchant_id, :execution_reference, :transaction_reference, :split_rule_reference, :currency_code, "
                        + ":gross_amount, :platform_fee_amount, :distributable_amount, :allocated_amount, 'ALLOCATED', :snapshot_json)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("execution_reference", executionReference)
                        .addValue("transaction_reference", required(transactionReference, "transactionReference"))
                        .addValue("split_rule_reference", splitRuleReference)
                        .addValue("currency_code", rule.currencyCode())
                        .addValue("gross_amount", gross)
                        .addValue("platform_fee_amount", configuredFee)
                        .addValue("distributable_amount", distributable)
                        .addValue("allocated_amount", allocated)
                        .addValue("snapshot_json", snapshot));
        Long executionId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM marketplace_split_executions WHERE execution_reference=:execution_reference",
                        new MapSqlParameterSource("execution_reference", executionReference),
                        Long.class);
        if (executionId == null) {
            throw new PaymentGatewayException("Unable to persist split execution");
        }
        for (Allocation allocation : allocations) {
            jdbcTemplate.update(
                    "INSERT INTO marketplace_split_allocations "
                            + "(execution_id, subaccount_id, allocation_amount, status) "
                            + "VALUES (:execution_id, :subaccount_id, :allocation_amount, 'PENDING_SETTLEMENT')",
                    new MapSqlParameterSource()
                            .addValue("execution_id", executionId)
                            .addValue("subaccount_id", allocation.recipient().subaccountId())
                            .addValue("allocation_amount", allocation.amount()));
        }
        return execution(merchantId, executionReference);
    }

    public List<Map<String, Object>> executions(long merchantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT execution_reference AS executionReference, transaction_reference AS transactionReference, "
                        + "split_rule_reference AS splitRuleReference, currency_code AS currencyCode, gross_amount AS grossAmount, "
                        + "platform_fee_amount AS platformFeeAmount, distributable_amount AS distributableAmount, "
                        + "allocated_amount AS allocatedAmount, status, created_at AS createdAt "
                        + "FROM marketplace_split_executions WHERE merchant_id=:merchant_id ORDER BY id DESC LIMIT "
                        + safeLimit,
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    private List<RecipientInput> validateRecipients(
            long merchantId, String mode, List<Map<String, Object>> recipients) {
        if (recipients == null || recipients.isEmpty()) {
            throw new PaymentGatewayException("At least one split recipient is required");
        }
        List<RecipientInput> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> recipient : recipients) {
            String reference = required(text(recipient.get("subaccountReference")), "subaccountReference");
            Long subaccountId = activeSubaccountId(merchantId, reference);
            BigDecimal value = positive(decimal(recipient.get("allocationValue")), "allocationValue");
            int priority = intValue(recipient.get("priorityRank"), 100);
            result.add(new RecipientInput(subaccountId, value, Math.max(1, priority)));
            total = total.add(value);
        }
        if ("PERCENTAGE".equals(mode) && total.compareTo(BigDecimal.valueOf(100)) != 0) {
            throw new PaymentGatewayException("Percentage split recipients must total exactly 100");
        }
        return result;
    }

    private Long activeSubaccountId(long merchantId, String reference) {
        List<Long> rows =
                jdbcTemplate.query(
                        "SELECT id FROM marketplace_subaccounts WHERE merchant_id=:merchant_id "
                                + "AND subaccount_reference=:reference AND status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", reference),
                        (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Split recipient subaccount is not active");
        }
        return rows.get(0);
    }

    private SplitRule loadRule(long merchantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id, currency_code, allocation_mode, platform_fee_type, platform_fee_value, fee_bearer "
                                + "FROM marketplace_split_rules WHERE merchant_id=:merchant_id "
                                + "AND split_rule_reference=:reference AND status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(reference, "splitRuleReference")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Split rule was not found or is inactive");
        }
        Map<String, Object> row = rows.get(0);
        return new SplitRule(
                ((Number) row.get("id")).longValue(),
                reference,
                String.valueOf(row.get("currency_code")),
                String.valueOf(row.get("allocation_mode")),
                String.valueOf(row.get("platform_fee_type")),
                decimal(row.get("platform_fee_value")),
                String.valueOf(row.get("fee_bearer")));
    }

    private List<SplitRecipient> recipients(long ruleId, long merchantId) {
        return jdbcTemplate.query(
                "SELECT r.subaccount_id, s.subaccount_reference, s.display_name, r.allocation_value, r.priority_rank "
                        + "FROM marketplace_split_recipients r JOIN marketplace_subaccounts s ON s.id=r.subaccount_id "
                        + "WHERE r.split_rule_id=:rule_id AND r.status='ACTIVE' AND s.status='ACTIVE' AND s.merchant_id=:merchant_id "
                        + "ORDER BY r.priority_rank, r.id",
                new MapSqlParameterSource()
                        .addValue("rule_id", ruleId)
                        .addValue("merchant_id", merchantId),
                (rs, rowNum) ->
                        new SplitRecipient(
                                rs.getLong("subaccount_id"),
                                rs.getString("subaccount_reference"),
                                rs.getString("display_name"),
                                rs.getBigDecimal("allocation_value"),
                                rs.getInt("priority_rank")));
    }

    private Map<String, Object> splitRule(long merchantId, String reference) {
        Map<String, Object> rule =
                jdbcTemplate.queryForMap(
                        "SELECT id, split_rule_reference AS splitRuleReference, rule_name AS ruleName, currency_code AS currencyCode, "
                                + "allocation_mode AS allocationMode, platform_fee_type AS platformFeeType, platform_fee_value AS platformFeeValue, "
                                + "fee_bearer AS feeBearer, status FROM marketplace_split_rules "
                                + "WHERE merchant_id=:merchant_id AND split_rule_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", reference));
        long ruleId = ((Number) rule.get("id")).longValue();
        Map<String, Object> result = new LinkedHashMap<>(rule);
        result.remove("id");
        result.put(
                "recipients",
                jdbcTemplate.queryForList(
                        "SELECT s.subaccount_reference AS subaccountReference, s.display_name AS displayName, "
                                + "r.allocation_value AS allocationValue, r.priority_rank AS priorityRank, r.status "
                                + "FROM marketplace_split_recipients r JOIN marketplace_subaccounts s ON s.id=r.subaccount_id "
                                + "WHERE r.split_rule_id=:rule_id ORDER BY r.priority_rank, r.id",
                        new MapSqlParameterSource("rule_id", ruleId)));
        return result;
    }

    private Map<String, Object> subaccount(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT subaccount_reference AS subaccountReference, display_name AS displayName, currency_code AS currencyCode, "
                        + "destination_type AS destinationType, destination_reference AS destinationReference, status, created_at AS createdAt "
                        + "FROM marketplace_subaccounts WHERE merchant_id=:merchant_id AND subaccount_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private Map<String, Object> execution(long merchantId, String reference) {
        Map<String, Object> result =
                new LinkedHashMap<>(
                        jdbcTemplate.queryForMap(
                                "SELECT id, execution_reference AS executionReference, transaction_reference AS transactionReference, "
                                        + "split_rule_reference AS splitRuleReference, currency_code AS currencyCode, gross_amount AS grossAmount, "
                                        + "platform_fee_amount AS platformFeeAmount, distributable_amount AS distributableAmount, "
                                        + "allocated_amount AS allocatedAmount, status, snapshot_json AS snapshotJson, created_at AS createdAt "
                                        + "FROM marketplace_split_executions WHERE merchant_id=:merchant_id AND execution_reference=:reference",
                                new MapSqlParameterSource()
                                        .addValue("merchant_id", merchantId)
                                        .addValue("reference", reference)));
        long executionId = ((Number) result.remove("id")).longValue();
        result.put(
                "allocations",
                jdbcTemplate.queryForList(
                        "SELECT s.subaccount_reference AS subaccountReference, s.display_name AS displayName, "
                                + "a.allocation_amount AS allocationAmount, a.status, a.settlement_reference AS settlementReference "
                                + "FROM marketplace_split_allocations a JOIN marketplace_subaccounts s ON s.id=a.subaccount_id "
                                + "WHERE a.execution_id=:execution_id ORDER BY a.id",
                        new MapSqlParameterSource("execution_id", executionId)));
        return result;
    }

    private String snapshot(
            SplitRule rule,
            List<Allocation> allocations,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal distributable) {
        String recipientsJson =
                allocations.stream()
                        .map(
                                allocation ->
                                        "{\"subaccountReference\":\""
                                                + escape(allocation.recipient().reference())
                                                + "\",\"amount\":"
                                                + allocation.amount().toPlainString()
                                                + "}")
                        .collect(Collectors.joining(",", "[", "]"));
        return "{\"splitRuleReference\":\""
                + escape(rule.reference())
                + "\",\"allocationMode\":\""
                + rule.allocationMode()
                + "\",\"feeBearer\":\""
                + rule.feeBearer()
                + "\",\"grossAmount\":"
                + gross.toPlainString()
                + ",\"platformFeeAmount\":"
                + fee.toPlainString()
                + ",\"distributableAmount\":"
                + distributable.toPlainString()
                + ",\"recipients\":"
                + recipientsJson
                + "}";
    }

    private boolean successful(String value) {
        if (value == null) {
            return false;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        return Set.of("SUCCESS", "SUCCESSFUL", "COMPLETED", "COMPLETE", "000").contains(status);
    }

    private BigDecimal amount(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception e) {
            throw new PaymentGatewayException("Payment amount is invalid");
        }
    }

    private void requireMerchant(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
    }

    private String normalizeMode(String value) {
        String mode = required(value, "allocationMode").toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new PaymentGatewayException("Mode must be PERCENTAGE or FIXED");
        }
        return mode;
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new PaymentGatewayException(field + " cannot be negative");
        }
        return value;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || text(value).isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Amount values must be numeric");
        }
    }

    private int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("priorityRank must be an integer");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record RecipientInput(long subaccountId, BigDecimal allocationValue, int priorityRank) {}

    private record SplitRule(
            long id,
            String reference,
            String currencyCode,
            String allocationMode,
            String platformFeeType,
            BigDecimal platformFeeValue,
            String feeBearer) {}

    private record SplitRecipient(
            long subaccountId,
            String reference,
            String displayName,
            BigDecimal allocationValue,
            int priorityRank) {}

    private record Allocation(SplitRecipient recipient, BigDecimal amount) {}
}