package net.citotech.cito.marketplace;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MarketplaceSplitSimulationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MarketplaceSplitSimulationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> simulate(
            long merchantId, String splitRuleReference, String currencyCode, BigDecimal grossAmount) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
        if (grossAmount == null || grossAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("grossAmount must be greater than zero");
        }
        Map<String, Object> rule = loadRule(merchantId, splitRuleReference);
        String ruleCurrency = String.valueOf(rule.get("currency_code"));
        if (currencyCode == null || !ruleCurrency.equalsIgnoreCase(currencyCode.trim())) {
            throw new PaymentGatewayException("Split rule currency does not match payment currency");
        }
        List<Map<String, Object>> recipients = recipients(((Number) rule.get("id")).longValue(), merchantId);
        if (recipients.isEmpty()) {
            throw new PaymentGatewayException("Split rule has no active recipients");
        }

        BigDecimal feeValue = decimal(rule.get("platform_fee_value"));
        BigDecimal platformFee =
                "PERCENTAGE".equalsIgnoreCase(String.valueOf(rule.get("platform_fee_type")))
                        ? grossAmount.multiply(feeValue).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                        : feeValue.setScale(6, RoundingMode.HALF_UP);
        if (platformFee.compareTo(grossAmount) > 0) {
            throw new PaymentGatewayException("Platform fee exceeds gross payment amount");
        }
        BigDecimal distributable = grossAmount.subtract(platformFee).setScale(6, RoundingMode.HALF_UP);
        boolean percentage = "PERCENTAGE".equalsIgnoreCase(String.valueOf(rule.get("allocation_mode")));
        List<Map<String, Object>> allocations = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (Map<String, Object> recipient : recipients) {
            BigDecimal value = decimal(recipient.get("allocation_value"));
            BigDecimal amount =
                    percentage
                            ? distributable.multiply(value).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                            : value.setScale(6, RoundingMode.HALF_UP);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subaccountReference", recipient.get("subaccount_reference"));
            row.put("displayName", recipient.get("display_name"));
            row.put("allocationValue", value);
            row.put("allocationAmount", amount);
            allocations.add(row);
            allocated = allocated.add(amount);
        }
        BigDecimal difference = distributable.subtract(allocated);
        if (percentage && difference.abs().compareTo(new BigDecimal("0.000010")) <= 0 && !allocations.isEmpty()) {
            Map<String, Object> last = allocations.get(allocations.size() - 1);
            last.put("allocationAmount", decimal(last.get("allocationAmount")).add(difference));
            allocated = distributable;
        }
        if (allocated.compareTo(distributable) != 0) {
            throw new PaymentGatewayException("Split allocations must exactly equal the distributable payment amount");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("simulation", true);
        result.put("splitRuleReference", splitRuleReference);
        result.put("currencyCode", ruleCurrency);
        result.put("grossAmount", grossAmount.setScale(6, RoundingMode.HALF_UP));
        result.put("platformFeeAmount", platformFee);
        result.put("distributableAmount", distributable);
        result.put("allocatedAmount", allocated);
        result.put("feeBearer", rule.get("fee_bearer"));
        result.put("allocations", allocations);
        return result;
    }

    private Map<String, Object> loadRule(long merchantId, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new PaymentGatewayException("splitRuleReference is required");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, currency_code, allocation_mode, platform_fee_type, platform_fee_value, fee_bearer "
                        + "FROM marketplace_split_rules WHERE merchant_id=:merchant_id "
                        + "AND split_rule_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference.trim()));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Split rule was not found or is inactive");
        }
        return rows.get(0);
    }

    private List<Map<String, Object>> recipients(long ruleId, long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT r.subaccount_id, s.subaccount_reference, s.display_name, r.allocation_value, r.priority_rank "
                        + "FROM marketplace_split_recipients r JOIN marketplace_subaccounts s ON s.id=r.subaccount_id "
                        + "WHERE r.split_rule_id=:rule_id AND r.status='ACTIVE' AND s.status='ACTIVE' "
                        + "AND s.merchant_id=:merchant_id ORDER BY r.priority_rank, r.id",
                new MapSqlParameterSource().addValue("rule_id", ruleId).addValue("merchant_id", merchantId));
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Split configuration contains an invalid numeric value");
        }
    }
}
