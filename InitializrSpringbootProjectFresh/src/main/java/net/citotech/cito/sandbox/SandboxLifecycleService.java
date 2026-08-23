package net.citotech.cito.sandbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.admin.ReadinessDashboardService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer-facing sandbox state and evidence used by the controlled sandbox-to-production workflow.
 * Only sandbox-owned state is reset or restored here. Production credentials, financial records,
 * compliance evidence and ledger entries are deliberately outside sandbox mutation operations.
 */
@Service
public class SandboxLifecycleService {
    private static final BigDecimal MAX_TOP_UP = new BigDecimal("1000000000000");
    private static final Set<String> RESET_SCOPES =
            Set.of("WALLETS", "SNAPSHOTS", "CERTIFICATION", "PROVIDER_RUNS", "ALL");
    private static final Set<String> TRANSACTION_SUCCESS =
            Set.of("SUCCESS", "SUCCESSFUL", "COMPLETED");
    private static final Set<String> CALLBACK_SUCCESS =
            Set.of("DELIVERED", "SUCCESS", "SUCCESSFUL", "COMPLETED");
    private static final Set<String> PROVIDER_SUCCESS =
            Set.of("SUCCESS", "SUCCESSFUL", "SUBMITTED");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReadinessDashboardService readinessDashboardService;
    private final ObjectMapper objectMapper;

    public SandboxLifecycleService(
            NamedParameterJdbcTemplate jdbcTemplate,
            ReadinessDashboardService readinessDashboardService,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.readinessDashboardService = readinessDashboardService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> summary(long merchantId, String merchantNumber) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("environment", environmentStatus(merchantId));
        result.put("readiness", readinessDashboardService.merchantSummary(merchantId));
        result.put("wallets", sandboxWallets(merchantId));
        result.put("snapshots", snapshots(merchantId));
        result.put("personas", personas(null));
        result.put("certification", latestCertification(merchantId));
        result.put("goLive", latestGoLiveRequest(merchantId));
        result.put("rollout", rollout(merchantId));
        result.put("environmentComparison", environmentComparison(merchantId, merchantNumber));
        return result;
    }

    public List<Map<String, Object>> sandboxWallets(long merchantId) {
        return rows(
                "SELECT id,channel_code,currency,available_balance,updated_by,created_at,updated_at "
                        + "FROM sandbox_wallet_balances WHERE merchant_id=:merchantId "
                        + "ORDER BY currency,channel_code",
                params("merchantId", merchantId));
    }

    @Transactional
    public Map<String, Object> topUp(
            long merchantId, String channelCode, String currency, BigDecimal amount, String actor) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_TOP_UP) > 0) {
            throw new IllegalArgumentException(
                    "Sandbox top-up amount must be greater than zero and within the synthetic-money limit.");
        }
        String safeChannel = normalizeCode(channelCode, "GENERAL", 100);
        String safeCurrency = normalizeCode(currency, "UGX", 12);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("channelCode", safeChannel);
        p.addValue("currency", safeCurrency);
        p.addValue("amount", amount);
        p.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_wallet_balances "
                        + "(merchant_id,channel_code,currency,available_balance,updated_by) "
                        + "VALUES (:merchantId,:channelCode,:currency,:amount,:actor) "
                        + "ON DUPLICATE KEY UPDATE available_balance=available_balance+:amount,updated_by=:actor",
                p);
        return first(
                "SELECT id,channel_code,currency,available_balance,updated_by,updated_at "
                        + "FROM sandbox_wallet_balances WHERE merchant_id=:merchantId "
                        + "AND channel_code=:channelCode AND currency=:currency",
                p);
    }

    @Transactional
    public Map<String, Object> reset(
            long merchantId, String merchantNumber, String requestedScope, String actor) {
        String scope = normalizeCode(requestedScope, "ALL", 40);
        if (!RESET_SCOPES.contains(scope)) {
            throw new IllegalArgumentException("Unsupported sandbox reset scope: " + scope);
        }
        MapSqlParameterSource merchant = params("merchantId", merchantId);
        if ("ALL".equals(scope) || "CERTIFICATION".equals(scope)) {
            int activeGoLive =
                    scalar(
                            "SELECT COUNT(*) FROM merchant_go_live_requests WHERE merchant_id=:merchantId "
                                    + "AND request_status IN ('REQUESTED','IN_REVIEW','APPROVED')",
                            merchant);
            if (activeGoLive > 0) {
                throw new IllegalStateException(
                        "Certification cannot be reset while a production-access request is active. Reject or complete the go-live request first.");
            }
        }

        int wallets = 0;
        int snapshots = 0;
        int certificationChecks = 0;
        int certificationRuns = 0;
        int providerRuns = 0;
        if ("ALL".equals(scope) || "WALLETS".equals(scope)) {
            wallets =
                    jdbcTemplate.update(
                            "DELETE FROM sandbox_wallet_balances WHERE merchant_id=:merchantId",
                            merchant);
        }
        if ("ALL".equals(scope) || "SNAPSHOTS".equals(scope)) {
            snapshots =
                    jdbcTemplate.update(
                            "DELETE FROM sandbox_snapshots WHERE merchant_id=:merchantId", merchant);
        }
        if ("ALL".equals(scope) || "CERTIFICATION".equals(scope)) {
            List<Long> runIds =
                    jdbcTemplate.queryForList(
                            "SELECT id FROM sandbox_certification_runs WHERE merchant_id=:merchantId",
                            merchant,
                            Long.class);
            if (!runIds.isEmpty()) {
                certificationChecks =
                        jdbcTemplate.update(
                                "DELETE FROM sandbox_certification_checks WHERE run_id IN (:runIds)",
                                new MapSqlParameterSource("runIds", runIds));
            }
            certificationRuns =
                    jdbcTemplate.update(
                            "DELETE FROM sandbox_certification_runs WHERE merchant_id=:merchantId",
                            merchant);
        }
        if (("ALL".equals(scope) || "PROVIDER_RUNS".equals(scope))
                && merchantNumber != null
                && !merchantNumber.isBlank()) {
            providerRuns =
                    jdbcTemplate.update(
                            "DELETE FROM provider_endpoint_runs WHERE merchant_number=:merchantNumber "
                                    + "AND environment='SANDBOX'",
                            params("merchantNumber", merchantNumber));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", scope);
        result.put("walletsDeleted", wallets);
        result.put("snapshotsDeleted", snapshots);
        result.put("certificationChecksDeleted", certificationChecks);
        result.put("certificationRunsDeleted", certificationRuns);
        result.put("providerSandboxRunsDeleted", providerRuns);
        result.put("productionDataTouched", false);
        result.put("actor", actor);
        return result;
    }

    public List<Map<String, Object>> personas(String personaType) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = " WHERE active_flag=1";
        if (personaType != null && !personaType.isBlank()) {
            where += " AND persona_type=:personaType";
            p.addValue("personaType", normalizeCode(personaType, "KYC", 24));
        }
        return rows(
                "SELECT persona_code,persona_type,display_name,expected_status,scenario,payload_json "
                        + "FROM sandbox_test_personas"
                        + where
                        + " ORDER BY persona_type,persona_code",
                p);
    }

    @Transactional
    public Map<String, Object> createSnapshot(long merchantId, String snapshotName, String actor) {
        String name =
                snapshotName == null || snapshotName.isBlank()
                        ? "Sandbox snapshot " + Instant.now()
                        : snapshotName.trim();
        if (name.length() > 160) {
            throw new IllegalArgumentException("Snapshot name must be 160 characters or fewer.");
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("version", 1);
        state.put("wallets", sandboxWallets(merchantId));
        state.put("environment", environmentStatus(merchantId));
        state.put("capturedAt", Instant.now().toString());
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("name", name);
        p.addValue("json", json(state));
        p.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_snapshots (merchant_id,snapshot_name,snapshot_json,created_by) "
                        + "VALUES (:merchantId,:name,:json,:actor)",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return first(
                "SELECT id,snapshot_name,created_by,created_at FROM sandbox_snapshots WHERE id=:id",
                params("id", id));
    }

    public List<Map<String, Object>> snapshots(long merchantId) {
        return rows(
                "SELECT id,snapshot_name,created_by,created_at FROM sandbox_snapshots "
                        + "WHERE merchant_id=:merchantId ORDER BY id DESC LIMIT 50",
                params("merchantId", merchantId));
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> restoreSnapshot(long merchantId, long snapshotId, String actor) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("snapshotId", snapshotId);
        Map<String, Object> snapshot =
                first(
                        "SELECT snapshot_json FROM sandbox_snapshots WHERE id=:snapshotId "
                                + "AND merchant_id=:merchantId",
                        p);
        if (snapshot.isEmpty()) {
            throw new IllegalArgumentException("Sandbox snapshot was not found for this merchant.");
        }
        Map<String, Object> state;
        try {
            state =
                    objectMapper.readValue(
                            String.valueOf(snapshot.get("snapshot_json")), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored sandbox snapshot is not readable.", e);
        }
        jdbcTemplate.update("DELETE FROM sandbox_wallet_balances WHERE merchant_id=:merchantId", p);
        int restored = 0;
        if (state.get("wallets") instanceof List<?> walletList) {
            for (Object item : walletList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> wallet = (Map<String, Object>) raw;
                BigDecimal amount =
                        wallet.get("available_balance") == null
                                ? BigDecimal.ZERO
                                : new BigDecimal(String.valueOf(wallet.get("available_balance")));
                MapSqlParameterSource wp = new MapSqlParameterSource();
                wp.addValue("merchantId", merchantId);
                wp.addValue(
                        "channelCode",
                        normalizeCode(
                                String.valueOf(wallet.get("channel_code")), "GENERAL", 100));
                wp.addValue(
                        "currency",
                        normalizeCode(String.valueOf(wallet.get("currency")), "UGX", 12));
                wp.addValue("amount", amount);
                wp.addValue("actor", actor);
                jdbcTemplate.update(
                        "INSERT INTO sandbox_wallet_balances "
                                + "(merchant_id,channel_code,currency,available_balance,updated_by) "
                                + "VALUES (:merchantId,:channelCode,:currency,:amount,:actor)",
                        wp);
                restored++;
            }
        }
        return Map.of(
                "snapshotId",
                snapshotId,
                "walletsRestored",
                restored,
                "productionDataTouched",
                false,
                "actor",
                actor == null ? "" : actor);
    }

    @Transactional
    public Map<String, Object> runCertification(long merchantId, String actor) {
        MapSqlParameterSource start = new MapSqlParameterSource();
        start.addValue("merchantId", merchantId);
        start.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_certification_runs (merchant_id,run_status,requested_by) "
                        + "VALUES (:merchantId,'RUNNING',:actor)",
                start);
        Long runId =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);

        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> readiness = readinessDashboardService.merchantSummary(merchantId);
        if (readiness.get("checklist") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                String key = String.valueOf(raw.get("id"));
                String label = String.valueOf(raw.get("label"));
                boolean passed = "READY".equals(String.valueOf(raw.get("status")));
                checks.add(
                        certificationCheck(
                                key, label, passed, String.valueOf(raw.get("action"))));
            }
        }

        List<String> configuredChannels =
                jdbcTemplate.queryForList(
                        "SELECT DISTINCT channel_code FROM merchant_channel_credentials "
                                + "WHERE merchant_id=:merchantId",
                        params("merchantId", merchantId),
                        String.class);
        List<Map<String, Object>> requirements =
                rows(
                        "SELECT provider_code,channel_code,scenario_name "
                                + "FROM provider_certification_requirements "
                                + "WHERE required_flag='YES' ORDER BY provider_code,channel_code,scenario_name",
                        new MapSqlParameterSource());
        for (Map<String, Object> requirement : requirements) {
            String provider = String.valueOf(requirement.get("provider_code"));
            String requiredChannel = String.valueOf(requirement.get("channel_code"));
            String scenario = String.valueOf(requirement.get("scenario_name"));
            List<String> relevantChannels =
                    "*".equals(requiredChannel)
                            ? configuredChannels
                            : configuredChannels.stream()
                                    .filter(channel -> channel.equalsIgnoreCase(requiredChannel))
                                    .toList();
            if (relevantChannels.isEmpty() && !"*".equals(requiredChannel)) {
                continue;
            }
            int approvedChannels = 0;
            for (String channel : relevantChannels) {
                MapSqlParameterSource rp = new MapSqlParameterSource();
                rp.addValue("channel", channel);
                rp.addValue("scenario", scenario);
                rp.addValue("provider", provider);
                int evidence =
                        scalar(
                                "SELECT COUNT(*) FROM provider_certification_evidence "
                                        + "WHERE channel_code=:channel AND scenario_name=:scenario "
                                        + "AND evidence_status='APPROVED' "
                                        + "AND (:provider='*' OR provider_code=:provider)",
                                rp);
                if (evidence > 0) {
                    approvedChannels++;
                }
            }
            boolean passed =
                    !relevantChannels.isEmpty() && approvedChannels == relevantChannels.size();
            String key =
                    "scenario_"
                            + sanitizeKey(provider)
                            + "_"
                            + sanitizeKey(requiredChannel)
                            + "_"
                            + sanitizeKey(scenario);
            checks.add(
                    certificationCheck(
                            key,
                            "Required provider scenario: " + scenario,
                            passed,
                            passed
                                    ? "Approved for all applicable configured channels"
                                    : approvedChannels
                                            + "/"
                                            + relevantChannels.size()
                                            + " applicable channels approved"));
        }

        checks.add(
                certificationCheck(
                        "sandbox_environment",
                        "Sandbox environment is explicitly configured",
                        "SANDBOX"
                                .equals(
                                        String.valueOf(
                                                environmentStatus(merchantId)
                                                        .get("activeEnvironment"))),
                        "Merchant should complete certification while operating in SANDBOX."));
        checks.add(
                certificationCheck(
                        "synthetic_money_boundary",
                        "Synthetic wallet boundary is available",
                        tableExists("sandbox_wallet_balances"),
                        "Sandbox wallet storage must exist and remain separate from production balances."));
        checks.add(
                certificationCheck(
                        "credential_separation",
                        "Sandbox and production credential environments are separated",
                        credentialEnvironmentsSeparated(merchantId),
                        "Provision distinct SANDBOX and PRODUCTION channel credential records before go-live."));

        int passedCount = 0;
        for (Map<String, Object> check : checks) {
            boolean passed = Boolean.TRUE.equals(check.get("passed"));
            if (passed) {
                passedCount++;
            }
            MapSqlParameterSource cp = new MapSqlParameterSource();
            cp.addValue("runId", runId);
            cp.addValue("key", check.get("key"));
            cp.addValue("label", check.get("label"));
            cp.addValue("passed", passed ? 1 : 0);
            cp.addValue("evidence", check.get("evidence"));
            jdbcTemplate.update(
                    "INSERT INTO sandbox_certification_checks "
                            + "(run_id,check_key,check_label,passed,evidence) "
                            + "VALUES (:runId,:key,:label,:passed,:evidence)",
                    cp);
        }
        String status = !checks.isEmpty() && passedCount == checks.size() ? "PASSED" : "FAILED";
        MapSqlParameterSource finish = new MapSqlParameterSource();
        finish.addValue("runId", runId);
        finish.addValue("status", status);
        finish.addValue("passed", passedCount);
        finish.addValue("total", checks.size());
        jdbcTemplate.update(
                "UPDATE sandbox_certification_runs SET run_status=:status,passed_checks=:passed,"
                        + "total_checks=:total,completed_at=CURRENT_TIMESTAMP WHERE id=:runId",
                finish);
        return certification(runId == null ? 0L : runId);
    }

    public Map<String, Object> latestCertification(long merchantId) {
        Long runId =
                nullableLong(
                        "SELECT id FROM sandbox_certification_runs WHERE merchant_id=:merchantId "
                                + "ORDER BY id DESC LIMIT 1",
                        params("merchantId", merchantId));
        return runId == null ? Map.of() : certification(runId);
    }

    public Map<String, Object> certification(long runId) {
        Map<String, Object> run =
                first(
                        "SELECT id,merchant_id,run_status,passed_checks,total_checks,requested_by,"
                                + "started_at,completed_at FROM sandbox_certification_runs WHERE id=:runId",
                        params("runId", runId));
        if (run.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put(
                "checks",
                rows(
                        "SELECT check_key,check_label,passed,evidence,created_at "
                                + "FROM sandbox_certification_checks WHERE run_id=:runId ORDER BY id",
                        params("runId", runId)));
        return result;
    }

    public Map<String, Object> environmentComparison(long merchantId, String merchantNumber) {
        MapSqlParameterSource p = params("merchantId", merchantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", merchantId);
        result.put("activeEnvironment", environmentStatus(merchantId).get("activeEnvironment"));
        result.put(
                "sandboxChannels",
                scalar(
                        "SELECT COUNT(*) FROM merchant_channel_credentials WHERE merchant_id=:merchantId "
                                + "AND UPPER(environment)='SANDBOX'",
                        p));
        result.put(
                "productionChannels",
                scalar(
                        "SELECT COUNT(*) FROM merchant_channel_credentials WHERE merchant_id=:merchantId "
                                + "AND UPPER(environment)='PRODUCTION'",
                        p));
        result.put(
                "webhookEndpoints",
                scalar(
                        "SELECT COUNT(*) FROM merchant_webhook_endpoints WHERE merchant_id=:merchantId",
                        p));
        result.put(
                "activeCallbackSecrets",
                scalar(
                        "SELECT COUNT(*) FROM merchant_callback_secrets WHERE merchant_id=:merchantId "
                                + "AND active_flag='YES'",
                        p));
        result.put("syntheticWallets", sandboxWallets(merchantId));
        result.put(
                "productionTransactionCountToday",
                scalar(
                        "SELECT COUNT(*) FROM merchant_production_usage WHERE merchant_id=:merchantId "
                                + "AND usage_date=CURRENT_DATE()",
                        p));
        result.put(
                "excludedFromPromotion",
                List.of(
                        "API secrets and private keys",
                        "synthetic balances",
                        "sandbox transactions and provider runs",
                        "sandbox KYC/KYB personas and evidence",
                        "test settlement accounts"));
        return result;
    }

    public Map<String, Object> latestGoLiveRequest(long merchantId) {
        Long id =
                nullableLong(
                        "SELECT id FROM merchant_go_live_requests WHERE merchant_id=:merchantId "
                                + "ORDER BY id DESC LIMIT 1",
                        params("merchantId", merchantId));
        return id == null ? Map.of() : goLiveRequest(id);
    }

    public List<Map<String, Object>> goLiveRequests(String status) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = "";
        if (status != null && !status.isBlank()) {
            where = " WHERE g.request_status=:status";
            p.addValue("status", normalizeCode(status, "REQUESTED", 40));
        }
        return rows(
                "SELECT g.id,g.merchant_id,m.name AS merchant_name,m.account_number,"
                        + "g.certification_run_id,g.request_status,g.current_stage,g.requested_by,"
                        + "g.decision_by,g.decision_notes,g.requested_at,g.updated_at,g.approved_at,"
                        + "g.activated_at FROM merchant_go_live_requests g "
                        + "JOIN merchants m ON m.id=g.merchant_id"
                        + where
                        + " ORDER BY g.id DESC LIMIT 200",
                p);
    }

    @Transactional
    public Map<String, Object> promoteConfiguration(
            long merchantId, Long goLiveRequestId, String actor) {
        Map<String, Object> readiness = readinessDashboardService.merchantSummary(merchantId);
        if (!checklistReady(readiness)) {
            throw new IllegalStateException(
                    "Merchant readiness checks must pass before configuration promotion.");
        }
        if (goLiveRequestId == null) {
            throw new IllegalArgumentException(
                    "A go-live request is required for configuration promotion.");
        }
        MapSqlParameterSource requestParams = new MapSqlParameterSource();
        requestParams.addValue("requestId", goLiveRequestId);
        requestParams.addValue("merchantId", merchantId);
        int matchingApprovedRequest =
                scalar(
                        "SELECT COUNT(*) FROM merchant_go_live_requests WHERE id=:requestId "
                                + "AND merchant_id=:merchantId AND request_status='APPROVED'",
                        requestParams);
        if (matchingApprovedRequest == 0) {
            throw new IllegalStateException(
                    "Configuration promotion requires the merchant's approved go-live request.");
        }
        int productionChannels =
                scalar(
                        "SELECT COUNT(*) FROM merchant_channel_credentials WHERE merchant_id=:merchantId "
                                + "AND UPPER(environment)='PRODUCTION'",
                        params("merchantId", merchantId));
        if (productionChannels == 0) {
            throw new IllegalStateException(
                    "At least one separately provisioned PRODUCTION channel credential is required before promotion.");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("merchantId", merchantId);
        manifest.put(
                "sharedTenantConfiguration",
                List.of(
                        "merchant users and roles",
                        "webhook endpoint definitions",
                        "reporting preferences",
                        "reconciliation rules and finance workflow configuration",
                        "notification templates and feature settings"));
        manifest.put("productionProvisioningValidated", true);
        manifest.put("productionChannelCount", productionChannels);
        manifest.put(
                "excluded",
                List.of(
                        "secrets/private keys",
                        "sandbox balances",
                        "sandbox transactions/provider evidence",
                        "synthetic identities",
                        "test settlement accounts"));
        manifest.put("promotedAt", Instant.now().toString());

        MapSqlParameterSource insert = new MapSqlParameterSource();
        insert.addValue("merchantId", merchantId);
        insert.addValue("requestId", goLiveRequestId);
        insert.addValue("manifest", json(manifest));
        insert.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_configuration_promotions "
                        + "(merchant_id,go_live_request_id,promotion_status,manifest_json,promoted_by) "
                        + "VALUES (:merchantId,:requestId,'PROMOTED',:manifest,:actor)",
                insert);
        return manifest;
    }

    @Transactional
    public Map<String, Object> verifyLiveSmokeTest(
            long merchantId, String suppliedMerchantNumber, String transactionReference, String actor) {
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new IllegalArgumentException("A production transaction reference is required.");
        }
        String authoritativeMerchantNumber =
                nullableString(
                        "SELECT account_number FROM merchants WHERE id=:merchantId LIMIT 1",
                        params("merchantId", merchantId));
        if (authoritativeMerchantNumber == null || authoritativeMerchantNumber.isBlank()) {
            throw new IllegalArgumentException("Merchant was not found.");
        }
        if (suppliedMerchantNumber != null
                && !suppliedMerchantNumber.isBlank()
                && !authoritativeMerchantNumber.equals(suppliedMerchantNumber.trim())) {
            throw new IllegalArgumentException(
                    "Merchant number does not match the selected merchant.");
        }

        MapSqlParameterSource txParams = new MapSqlParameterSource();
        txParams.addValue("merchantId", merchantId);
        txParams.addValue("reference", transactionReference.trim());
        Map<String, Object> transaction =
                first(
                        "SELECT id,status,tx_unique_id,tx_gateway_ref,tx_merchant_ref,callback_status,"
                                + "tx_type,original_amount,currency,created_on FROM merchant_transactions_log "
                                + "WHERE merchant_id=:merchantId AND "
                                + "(tx_unique_id=:reference OR tx_gateway_ref=:reference OR tx_merchant_ref=:reference) "
                                + "ORDER BY id DESC LIMIT 1",
                        txParams);
        boolean transactionVerified =
                !transaction.isEmpty()
                        && TRANSACTION_SUCCESS.contains(upper(transaction.get("status")));
        boolean callbackVerified =
                transactionVerified
                        && CALLBACK_SUCCESS.contains(upper(transaction.get("callback_status")));

        List<String> transactionReferences = new ArrayList<>();
        addReference(transactionReferences, transaction.get("tx_unique_id"));
        addReference(transactionReferences, transaction.get("tx_gateway_ref"));
        addReference(transactionReferences, transaction.get("tx_merchant_ref"));
        int providerRuns = 0;
        if (!transactionReferences.isEmpty()) {
            MapSqlParameterSource provider = new MapSqlParameterSource();
            provider.addValue("merchantNumber", authoritativeMerchantNumber);
            provider.addValue("references", transactionReferences);
            provider.addValue("success", PROVIDER_SUCCESS);
            providerRuns =
                    scalar(
                            "SELECT COUNT(*) FROM provider_endpoint_runs "
                                    + "WHERE merchant_number=:merchantNumber AND environment='PRODUCTION' "
                                    + "AND reference_value IN (:references) AND run_status IN (:success) "
                                    + "AND created_at>=DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                            provider);
        }
        boolean providerVerified = providerRuns > 0;
        String testStatus =
                transactionVerified && providerVerified && callbackVerified ? "PASSED" : "FAILED";

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("merchantNumber", authoritativeMerchantNumber);
        evidence.put("transaction", transaction);
        evidence.put("matchedProductionProviderRuns", providerRuns);
        evidence.put("checkedAt", Instant.now().toString());
        MapSqlParameterSource insert = new MapSqlParameterSource();
        insert.addValue("merchantId", merchantId);
        insert.addValue("reference", transactionReference.trim());
        insert.addValue("status", testStatus);
        insert.addValue("transactionVerified", transactionVerified ? 1 : 0);
        insert.addValue("providerVerified", providerVerified ? 1 : 0);
        insert.addValue("callbackVerified", callbackVerified ? 1 : 0);
        insert.addValue("evidence", json(evidence));
        insert.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_live_smoke_tests "
                        + "(merchant_id,transaction_reference,test_status,transaction_verified,"
                        + "provider_run_verified,callback_verified,evidence_json,verified_by) "
                        + "VALUES (:merchantId,:reference,:status,:transactionVerified,:providerVerified,"
                        + ":callbackVerified,:evidence,:actor)",
                insert);

        Map<String, Object> result = new LinkedHashMap<>(evidence);
        result.put("status", testStatus);
        result.put("transactionVerified", transactionVerified);
        result.put("providerVerified", providerVerified);
        result.put("callbackVerified", callbackVerified);
        return result;
    }

    public Map<String, Object> rollout(long merchantId) {
        Map<String, Object> record =
                first(
                        "SELECT merchant_id,stage_code,production_daily_limit,collections_enabled,"
                                + "refunds_enabled,payouts_enabled,updated_by,updated_at "
                                + "FROM merchant_rollout_stages WHERE merchant_id=:merchantId",
                        params("merchantId", merchantId));
        if (!record.isEmpty()) {
            return record;
        }
        return Map.of(
                "merchant_id",
                merchantId,
                "stage_code",
                "SANDBOX",
                "production_daily_limit",
                0,
                "collections_enabled",
                false,
                "refunds_enabled",
                false,
                "payouts_enabled",
                false);
    }

    @Transactional
    public Map<String, Object> verifyIsolation(String actor) {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(
                isolationCheck(
                        "sandbox_wallet_table",
                        tableExists("sandbox_wallet_balances"),
                        "Synthetic balances have their own table."));
        checks.add(
                isolationCheck(
                        "sandbox_snapshot_table",
                        tableExists("sandbox_snapshots"),
                        "Snapshots contain sandbox-owned state only."));
        checks.add(
                isolationCheck(
                        "environment_tagging",
                        columnExists("provider_endpoint_runs", "environment"),
                        "Provider executions are environment tagged."));
        checks.add(
                isolationCheck(
                        "credential_environment",
                        columnExists("merchant_channel_credentials", "environment"),
                        "Channel credentials carry an explicit environment."));
        checks.add(
                isolationCheck(
                        "production_capability_gate",
                        tableExists("merchant_rollout_stages"),
                        "Production capabilities are explicitly staged per merchant."));
        checks.add(
                isolationCheck(
                        "production_usage_quota",
                        tableExists("merchant_production_usage"),
                        "All guarded production money movement shares one quota ledger."));
        boolean passed = checks.stream().allMatch(item -> Boolean.TRUE.equals(item.get("passed")));
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("status", passed ? "PASSED" : "FAILED");
        p.addValue("checks", json(checks));
        p.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO sandbox_isolation_verifications "
                        + "(verification_status,checks_json,verified_by) VALUES (:status,:checks,:actor)",
                p);
        return Map.of(
                "status",
                passed ? "PASSED" : "FAILED",
                "checks",
                checks,
                "verifiedAt",
                Instant.now().toString());
    }

    private Map<String, Object> environmentStatus(long merchantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        String active =
                nullableString(
                        "SELECT active_environment FROM merchant_environment_preferences "
                                + "WHERE merchant_id=:merchantId AND channel_code='*' "
                                + "ORDER BY id DESC LIMIT 1",
                        params("merchantId", merchantId));
        result.put(
                "activeEnvironment",
                active == null || active.isBlank()
                        ? "SANDBOX"
                        : active.toUpperCase(Locale.ROOT));
        result.put("sandboxSeparated", true);
        return result;
    }

    private boolean credentialEnvironmentsSeparated(long merchantId) {
        MapSqlParameterSource p = params("merchantId", merchantId);
        int sandbox =
                scalar(
                        "SELECT COUNT(*) FROM merchant_channel_credentials WHERE merchant_id=:merchantId "
                                + "AND UPPER(environment)='SANDBOX'",
                        p);
        int production =
                scalar(
                        "SELECT COUNT(*) FROM merchant_channel_credentials WHERE merchant_id=:merchantId "
                                + "AND UPPER(environment)='PRODUCTION'",
                        p);
        return sandbox > 0 && production > 0;
    }

    private Map<String, Object> goLiveRequest(long id) {
        return first(
                "SELECT id,merchant_id,certification_run_id,request_status,current_stage,requested_by,"
                        + "decision_by,decision_notes,requested_at,updated_at,approved_at,activated_at "
                        + "FROM merchant_go_live_requests WHERE id=:id",
                params("id", id));
    }

    private boolean checklistReady(Map<String, Object> readiness) {
        Object value = readiness.get("checklist");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .allMatch(item -> "READY".equals(String.valueOf(item.get("status"))));
    }

    private Map<String, Object> certificationCheck(
            String key, String label, boolean passed, String evidence) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("label", label);
        check.put("passed", passed);
        check.put("evidence", evidence == null ? "" : evidence);
        return check;
    }

    private Map<String, Object> isolationCheck(String key, boolean passed, String evidence) {
        return certificationCheck(key, key.replace('_', ' '), passed, evidence);
    }

    private boolean tableExists(String table) {
        return scalar(
                        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
                                + "AND table_name=:name",
                        params("name", table))
                > 0;
    }

    private boolean columnExists(String table, String column) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("table", table);
        p.addValue("column", column);
        return scalar(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
                                + "AND table_name=:table AND column_name=:column",
                        p)
                > 0;
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource parameters) {
        try {
            return jdbcTemplate.queryForList(sql, parameters);
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private Map<String, Object> first(String sql, MapSqlParameterSource parameters) {
        List<Map<String, Object>> list = rows(sql, parameters);
        return list.isEmpty() ? Map.of() : list.get(0);
    }

    private int scalar(String sql, MapSqlParameterSource parameters) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, parameters, Integer.class);
            return value == null ? 0 : value;
        } catch (DataAccessException e) {
            return 0;
        }
    }

    private Long nullableLong(String sql, MapSqlParameterSource parameters) {
        try {
            List<Long> values = jdbcTemplate.queryForList(sql, parameters, Long.class);
            return values.isEmpty() ? null : values.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private String nullableString(String sql, MapSqlParameterSource parameters) {
        try {
            List<String> values = jdbcTemplate.queryForList(sql, parameters, String.class);
            return values.isEmpty() ? null : values.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private MapSqlParameterSource params(String key, Object value) {
        return new MapSqlParameterSource(key, value);
    }

    private String normalizeCode(String value, String fallback, int maxLength) {
        String normalized =
                value == null || value.isBlank()
                        ? fallback
                        : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength || !normalized.matches("[A-Z0-9_.*-]+")) {
            throw new IllegalArgumentException("Invalid code value: " + value);
        }
        return normalized;
    }

    private String sanitizeKey(String value) {
        return String.valueOf(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String upper(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private void addReference(List<String> references, Object value) {
        if (value == null) {
            return;
        }
        String reference = String.valueOf(value).trim();
        if (!reference.isEmpty() && !references.contains(reference)) {
            references.add(reference);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Sandbox state could not be serialized.", e);
        }
    }
}
