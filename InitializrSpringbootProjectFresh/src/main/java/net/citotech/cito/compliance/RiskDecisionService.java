package net.citotech.cito.compliance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RiskDecisionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RiskDecisionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RiskDecision authorizePayment(Merchant merchant, PaymentRequest request, String direction) {
        if (merchant == null || request == null) {
            throw new PaymentGatewayException("Merchant and payment request are required for risk authorization");
        }
        BigDecimal amount = MoneyAmount.of(request.getAmount()).asBigDecimal();
        String currency = normalized(request.getCurrency(), "UGX");
        String account = accountValue(request, direction);

        RiskDecision decision = evaluate(merchant, request.getReference(), direction, amount, currency, account);
        recordDecision(merchant, request.getReference(), direction, amount, currency, decision);
        if (decision.isBlocked()) {
            throw new PaymentGatewayException("Risk authorization blocked request: " + decision.getSummary());
        }
        return decision;
    }

    private RiskDecision evaluate(Merchant merchant, String reference, String direction, BigDecimal amount, String currency, String account) {
        if (!blank(account) && isBlockedAccount(account)) {
            return RiskDecision.block("BLOCKLIST_MATCH", "Account is blocklisted");
        }
        RiskDecision singleCap = evaluateSingleTransactionCap(merchant, amount, currency);
        if (!"ALLOW".equals(singleCap.getDecision())) {
            return singleCap;
        }
        RiskDecision dailyCap = evaluateDailyMerchantCap(merchant, amount, currency);
        if (!"ALLOW".equals(dailyCap.getDecision())) {
            return dailyCap;
        }
        return RiskDecision.allow("Risk checks passed for " + reference + " (" + direction + ")");
    }

    private boolean isBlockedAccount(String account) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("hash", CanonicalRequestSigner.sha256Hex(account.trim()));
        Integer count = queryCount(
            "SELECT COUNT(*) FROM compliance_blocklist "
                + "WHERE active_flag='YES' AND value_type IN ('ACCOUNT','MSISDN','PAYER','PAYEE') "
                + "AND value_hash=:hash",
            p);
        return count != null && count > 0;
    }

    private RiskDecision evaluateSingleTransactionCap(Merchant merchant, BigDecimal amount, String currency) {
        Rule rule = findRule("SINGLE_TRANSACTION_CAP", merchant, currency);
        if (rule == null || rule.thresholdAmount == null || amount.compareTo(rule.thresholdAmount) <= 0) {
            return RiskDecision.allow("single transaction cap passed");
        }
        if ("BLOCK".equalsIgnoreCase(rule.decision)) {
            return RiskDecision.block("SINGLE_TRANSACTION_CAP", "Amount exceeds single transaction cap");
        }
        return RiskDecision.review("SINGLE_TRANSACTION_CAP", "Amount exceeds review threshold");
    }

    private RiskDecision evaluateDailyMerchantCap(Merchant merchant, BigDecimal amount, String currency) {
        Rule rule = findRule("MERCHANT_DAILY_CAP", merchant, currency);
        if (rule == null || rule.thresholdAmount == null) {
            return RiskDecision.allow("daily cap not configured");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchant.getId());
        p.addValue("currency", currency);
        p.addValue("today", LocalDate.now().toString());
        BigDecimal todayTotal = queryAmount(
            "SELECT COALESCE(SUM(amount), 0) FROM risk_decisions "
                + "WHERE merchant_id=:merchant_id AND currency=:currency "
                + "AND DATE(created_at)=:today AND decision IN ('ALLOW','REVIEW')",
            p);
        if (todayTotal.add(amount).compareTo(rule.thresholdAmount) <= 0) {
            return RiskDecision.allow("daily cap passed");
        }
        if ("BLOCK".equalsIgnoreCase(rule.decision)) {
            return RiskDecision.block("MERCHANT_DAILY_CAP", "Daily merchant cap would be exceeded");
        }
        return RiskDecision.review("MERCHANT_DAILY_CAP", "Daily merchant review threshold would be exceeded");
    }

    private Rule findRule(String ruleType, Merchant merchant, String currency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rule_type", ruleType);
        p.addValue("merchant_scope", "merchant:" + merchant.getId());
        p.addValue("currency", currency);
        List<Rule> rules = jdbcTemplate.query(
            "SELECT decision, threshold_amount FROM risk_rules "
                + "WHERE enabled='YES' AND rule_type=:rule_type "
                + "AND (currency IS NULL OR currency=:currency) "
                + "AND ((scope_type='MERCHANT' AND scope_reference=:merchant_scope) "
                + "OR (scope_type='GLOBAL' AND scope_reference='*')) "
                + "ORDER BY CASE WHEN scope_type='MERCHANT' THEN 0 ELSE 1 END, id ASC LIMIT 1",
            p,
            (rs, rowNum) -> new Rule(rs.getString("decision"), rs.getBigDecimal("threshold_amount")));
        return rules.isEmpty() ? null : rules.get(0);
    }

    private void recordDecision(Merchant merchant, String reference, String direction, BigDecimal amount, String currency, RiskDecision decision) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchant.getId());
        p.addValue("reference", reference);
        p.addValue("direction", direction);
        p.addValue("amount", amount);
        p.addValue("currency", currency);
        p.addValue("decision", decision.getDecision());
        p.addValue("reason_code", decision.getReasonCode());
        p.addValue("summary", decision.getSummary());
        jdbcTemplate.update(
            "INSERT INTO risk_decisions "
                + "(merchant_id, request_reference, direction, amount, currency, decision, reason_code, decision_summary) "
                + "VALUES (:merchant_id, :reference, :direction, :amount, :currency, :decision, :reason_code, :summary)",
            p);
    }

    private String accountValue(PaymentRequest request, String direction) {
        if ("PAYOUT".equalsIgnoreCase(direction) && request.getPayee() != null) {
            return request.getPayee().getValue();
        }
        if (request.getPayer() != null) {
            return request.getPayer().getValue();
        }
        return "";
    }

    private Integer queryCount(String sql, MapSqlParameterSource p) {
        Integer value = jdbcTemplate.queryForObject(sql, p, Integer.class);
        return value == null ? 0 : value;
    }

    private BigDecimal queryAmount(String sql, MapSqlParameterSource p) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, p, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalized(String value, String defaultValue) {
        return blank(value) ? defaultValue : value.trim().toUpperCase();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record Rule(String decision, BigDecimal thresholdAmount) {
    }
}
