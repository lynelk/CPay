package net.citotech.cito.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emits aggregate, read-only evidence for ISO finance objective OBJ-FIN-01.
 *
 * <p>The reporter is disabled by default and never logs credentials, customer records, merchant
 * records, or transaction references. It is intended to be enabled for an audited production
 * deployment with {@code CPAY_FINANCE_CLOSE_EVIDENCE_ENABLED=true}, captured from deployment logs,
 * and then disabled again.
 */
@Component
@ConditionalOnProperty(
        name = "cpay.finance-close-evidence.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class FinanceCloseEvidenceReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(FinanceCloseEvidenceReporter.class);
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FinanceCloseEvidenceReporter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public void run(ApplicationArguments args) {
        MapSqlParameterSource empty = new MapSqlParameterSource();
        try {
            List<Map<String, Object>> closes =
                    jdbcTemplate.queryForList(
                            "SELECT business_date,status,provider_statements_received,"
                                    + "reconciliation_import_completed,unmatched_items_reviewed,"
                                    + "high_severity_controls_resolved,maker_checker_approvals_complete,"
                                    + "finance_owner_signed_off FROM finance_daily_close_records "
                                    + "ORDER BY business_date DESC,id DESC LIMIT 1",
                            empty);

            Map<String, Object> exceptions =
                    jdbcTemplate.queryForMap(
                            "SELECT COUNT(*) AS unresolved_count,"
                                    + "COALESCE(SUM(ABS(COALESCE(variance_amount,0))),0) AS unresolved_variance "
                                    + "FROM reconciliation_exceptions WHERE severity IN ('HIGH','CRITICAL') "
                                    + "AND status NOT IN ('RESOLVED','APPROVED','CLOSED')",
                            empty);

            Map<String, Object> settlements =
                    jdbcTemplate.queryForMap(
                            "SELECT COUNT(*) AS unresolved_count,"
                                    + "COALESCE(SUM(ABS(COALESCE(variance_amount,0))),0) AS unresolved_variance "
                                    + "FROM finance_settlement_batches WHERE ABS(COALESCE(variance_amount,0))>0.0001 "
                                    + "AND status NOT IN ('RECONCILED','CLOSED')",
                            empty);

            List<Map<String, Object>> ledgers =
                    jdbcTemplate.queryForList(
                            "SELECT le.currency,"
                                    + "COALESCE(SUM(CASE WHEN le.entry_direction='DR' THEN le.amount ELSE 0 END),0) AS debits,"
                                    + "COALESCE(SUM(CASE WHEN le.entry_direction='CR' THEN le.amount ELSE 0 END),0) AS credits "
                                    + "FROM ledger_entries le GROUP BY le.currency ORDER BY le.currency",
                            empty);

            boolean closePass = !closes.isEmpty() && closePass(closes.get(0));
            long exceptionCount = number(exceptions.get("unresolved_count")).longValue();
            BigDecimal exceptionVariance = decimal(exceptions.get("unresolved_variance"));
            long settlementCount = number(settlements.get("unresolved_count")).longValue();
            BigDecimal settlementVariance = decimal(settlements.get("unresolved_variance"));
            boolean ledgerPass = true;
            for (Map<String, Object> ledger : ledgers) {
                BigDecimal debits = decimal(ledger.get("debits"));
                BigDecimal credits = decimal(ledger.get("credits"));
                BigDecimal difference = debits.subtract(credits);
                if (difference.compareTo(ZERO) != 0) {
                    ledgerPass = false;
                }
                log.info(
                        "FINANCE_CLOSE_LEDGER objective=OBJ-FIN-01 currency={} debits={} credits={} difference={}",
                        ledger.get("currency"),
                        debits,
                        credits,
                        difference);
            }

            boolean pass =
                    closePass
                            && exceptionCount == 0
                            && exceptionVariance.compareTo(ZERO) == 0
                            && settlementCount == 0
                            && settlementVariance.compareTo(ZERO) == 0
                            && ledgerPass;

            Map<String, Object> close = closes.isEmpty() ? Map.of() : closes.get(0);
            log.info(
                    "FINANCE_CLOSE_EVIDENCE objective=OBJ-FIN-01 pass={} business_date={} close_status={} "
                            + "provider_statements_received={} reconciliation_import_completed={} "
                            + "unmatched_items_reviewed={} high_severity_controls_resolved={} "
                            + "maker_checker_approvals_complete={} finance_owner_signed_off={} "
                            + "unresolved_high_critical_exceptions={} unresolved_exception_variance={} "
                            + "unresolved_settlement_variances={} unresolved_settlement_variance={} ledger_balanced={}",
                    pass,
                    close.get("business_date"),
                    close.get("status"),
                    close.get("provider_statements_received"),
                    close.get("reconciliation_import_completed"),
                    close.get("unmatched_items_reviewed"),
                    close.get("high_severity_controls_resolved"),
                    close.get("maker_checker_approvals_complete"),
                    close.get("finance_owner_signed_off"),
                    exceptionCount,
                    exceptionVariance,
                    settlementCount,
                    settlementVariance,
                    ledgerPass);
        } catch (RuntimeException exception) {
            log.error(
                    "FINANCE_CLOSE_EVIDENCE objective=OBJ-FIN-01 pass=false evidence_query_failed={} message={}",
                    exception.getClass().getSimpleName(),
                    safeMessage(exception));
        }
    }

    private boolean closePass(Map<String, Object> close) {
        return "CLOSED".equals(String.valueOf(close.get("status")))
                && truthy(close.get("provider_statements_received"))
                && truthy(close.get("reconciliation_import_completed"))
                && truthy(close.get("unmatched_items_reviewed"))
                && truthy(close.get("high_severity_controls_resolved"))
                && truthy(close.get("maker_checker_approvals_complete"))
                && truthy(close.get("finance_owner_signed_off"));
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue() != 0;
        }
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private Number number(Object value) {
        return value instanceof Number numberValue ? numberValue : 0L;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (value instanceof Number numberValue) {
            return new BigDecimal(numberValue.toString());
        }
        return value == null ? ZERO : new BigDecimal(String.valueOf(value));
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return "unavailable";
        }
        return message.replaceAll("[\\r\\n]+", " ")
                .replaceAll("(?i)(password|secret|token)=[^ ,;]+", "$1=[redacted]");
    }
}
