package net.citotech.cito.platform;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only cross-feature health summary used by the unified Cito merchant workspace. */
@Service
public class CitoPlatformOverviewService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoFeatureAccessService featureAccessService;

    public CitoPlatformOverviewService(
            NamedParameterJdbcTemplate jdbcTemplate, CitoFeatureAccessService featureAccessService) {
        this.jdbcTemplate = jdbcTemplate;
        this.featureAccessService = featureAccessService;
    }

    public Map<String, Object> overview(long merchantId) {
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", merchantId);
        result.put("features", featureAccessService.featureDiscovery(merchantId).get("services"));
        result.put("routing", routing(p));
        result.put("refunds", refunds(p));
        result.put("marketplace", marketplace(p));
        result.put("recurring", recurring(p));
        result.put("developer", developer(p));
        result.put("virtualAccounts", virtualAccounts(p));
        result.put("embedded", embedded(p));
        result.put("integrations", integrations(p));
        return result;
    }

    private Map<String, Object> routing(MapSqlParameterSource p) {
        return one(
                "SELECT COUNT(*) decisions, SUM(CASE WHEN outcome='SUCCESS' THEN 1 ELSE 0 END) successful, "
                        + "SUM(CASE WHEN outcome='FAILED' THEN 1 ELSE 0 END) failed, COALESCE(ROUND(AVG(latency_ms)),0) averageLatencyMs "
                        + "FROM payment_route_decisions WHERE merchant_id=:merchant_id AND created_at>=CURRENT_TIMESTAMP-INTERVAL 7 DAY",
                p);
    }

    private Map<String, Object> refunds(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>(one(
                "SELECT COUNT(*) total, SUM(CASE WHEN refund_status='COMPLETED' THEN 1 ELSE 0 END) completed, "
                        + "SUM(CASE WHEN refund_status IN ('REQUESTED','PENDING_APPROVAL','PROCESSING','APPROVED') THEN 1 ELSE 0 END) inProgress, "
                        + "SUM(CASE WHEN refund_status IN ('FAILED','REJECTED') THEN 1 ELSE 0 END) failed "
                        + "FROM refunds WHERE merchant_id=:merchant_id AND created_at>=CURRENT_TIMESTAMP-INTERVAL 30 DAY",
                p));
        value.put("openDisputes", scalar(
                "SELECT COUNT(*) FROM payment_disputes WHERE merchant_id=:merchant_id AND status NOT IN ('CLOSED','RESOLVED','REJECTED')",
                p));
        return value;
    }

    private Map<String, Object> marketplace(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeSubaccounts", scalar(
                "SELECT COUNT(*) FROM marketplace_subaccounts WHERE merchant_id=:merchant_id AND status='ACTIVE'", p));
        value.put("splitExecutions30d", scalar(
                "SELECT COUNT(*) FROM marketplace_split_executions WHERE merchant_id=:merchant_id AND created_at>=CURRENT_TIMESTAMP-INTERVAL 30 DAY", p));
        value.put("recoveryFailures", scalar(
                "SELECT COUNT(*) FROM platform_feature_events WHERE merchant_id=:merchant_id AND event_type='MARKETPLACE_SPLIT_CAPTURE' AND status='FAILED'", p));
        value.put("pendingRecoveryEvents", scalar(
                "SELECT COUNT(*) FROM platform_feature_events WHERE merchant_id=:merchant_id AND event_type='MARKETPLACE_SPLIT_CAPTURE' AND status IN ('WAITING_PAYMENT','PENDING','PROCESSING')", p));
        return value;
    }

    private Map<String, Object> recurring(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeSubscriptions", scalar(
                "SELECT COUNT(*) FROM recurring_subscriptions WHERE merchant_id=:merchant_id AND status='ACTIVE'", p));
        value.put("pastDueSubscriptions", scalar(
                "SELECT COUNT(*) FROM recurring_subscriptions WHERE merchant_id=:merchant_id AND status='PAST_DUE'", p));
        value.put("failedCharges30d", scalar(
                "SELECT COUNT(*) FROM recurring_scheduled_charges c JOIN recurring_subscriptions s ON s.id=c.subscription_id "
                        + "WHERE s.merchant_id=:merchant_id AND c.status='FAILED' AND c.created_at>=CURRENT_TIMESTAMP-INTERVAL 30 DAY", p));
        return value;
    }

    private Map<String, Object> developer(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeProjects", scalar(
                "SELECT COUNT(*) FROM developer_projects WHERE merchant_id=:merchant_id AND status='ACTIVE'", p));
        value.put("activeCredentials", scalar(
                "SELECT COUNT(*) FROM developer_credentials c JOIN developer_service_accounts a ON a.id=c.service_account_id "
                        + "JOIN developer_projects p ON p.id=a.project_id WHERE p.merchant_id=:merchant_id AND c.status='ACTIVE'", p));
        value.put("requests24h", scalar(
                "SELECT COUNT(*) FROM developer_api_request_log WHERE merchant_id=:merchant_id AND created_at>=CURRENT_TIMESTAMP-INTERVAL 1 DAY", p));
        value.put("errors24h", scalar(
                "SELECT COUNT(*) FROM developer_api_request_log WHERE merchant_id=:merchant_id AND created_at>=CURRENT_TIMESTAMP-INTERVAL 1 DAY "
                        + "AND (response_status>=400 OR error_code IS NOT NULL)", p));
        return value;
    }

    private Map<String, Object> virtualAccounts(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("active", scalar(
                "SELECT COUNT(*) FROM virtual_accounts WHERE merchant_id=:merchant_id AND status='ACTIVE'", p));
        value.put("incomingTransfers30d", scalar(
                "SELECT COUNT(*) FROM virtual_account_incoming_transfers t JOIN virtual_accounts a ON a.id=t.virtual_account_id "
                        + "WHERE a.merchant_id=:merchant_id AND t.received_at>=CURRENT_TIMESTAMP-INTERVAL 30 DAY", p));
        value.put("incomingAmount30d", decimalScalar(
                "SELECT COALESCE(SUM(t.amount),0) FROM virtual_account_incoming_transfers t JOIN virtual_accounts a ON a.id=t.virtual_account_id "
                        + "WHERE a.merchant_id=:merchant_id AND t.received_at>=CURRENT_TIMESTAMP-INTERVAL 30 DAY", p));
        return value;
    }

    private Map<String, Object> embedded(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("partnerConfigured", scalar(
                "SELECT COUNT(*) FROM embedded_partners WHERE merchant_id=:merchant_id AND status='ACTIVE'", p) > 0);
        value.put("downstreamMerchants", scalar(
                "SELECT COUNT(*) FROM embedded_partner_merchants pm JOIN embedded_partners p ON p.id=pm.partner_id "
                        + "WHERE p.merchant_id=:merchant_id AND pm.status='ACTIVE'", p));
        value.put("activeDelegations", scalar(
                "SELECT COUNT(*) FROM embedded_service_delegations d JOIN embedded_partners p ON p.id=d.partner_id "
                        + "WHERE p.merchant_id=:merchant_id AND d.status='ACTIVE'", p));
        return value;
    }

    private Map<String, Object> integrations(MapSqlParameterSource p) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("activeInstallations", scalar(
                "SELECT COUNT(*) FROM integration_installations WHERE merchant_id=:merchant_id AND status='ACTIVE'", p));
        value.put("queuedJobs", scalar(
                "SELECT COUNT(*) FROM integration_sync_jobs j JOIN integration_installations i ON i.id=j.installation_id "
                        + "WHERE i.merchant_id=:merchant_id AND j.status IN ('QUEUED','RETRY_SCHEDULED','PROCESSING')", p));
        value.put("failedJobs", scalar(
                "SELECT COUNT(*) FROM integration_sync_jobs j JOIN integration_installations i ON i.id=j.installation_id "
                        + "WHERE i.merchant_id=:merchant_id AND j.status='FAILED'", p));
        return value;
    }

    private Map<String, Object> one(String sql, MapSqlParameterSource p) {
        java.util.List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, p);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private long scalar(String sql, MapSqlParameterSource p) {
        Number value = jdbcTemplate.queryForObject(sql, p, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private java.math.BigDecimal decimalScalar(String sql, MapSqlParameterSource p) {
        java.math.BigDecimal value = jdbcTemplate.queryForObject(sql, p, java.math.BigDecimal.class);
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}
