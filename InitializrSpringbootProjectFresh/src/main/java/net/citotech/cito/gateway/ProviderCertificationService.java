package net.citotech.cito.gateway;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Provider certification evidence workflow (P0 §1).
 *
 * <p>The original implementation only captured and approved individual evidence rows. This
 * extension adds a run-level lifecycle ({@code provider_certification_runs}) with per-run scenarios
 * and exceptions so that production channel activation can be gated on an APPROVED run whose
 * required scenarios all passed and whose blocking exceptions are resolved.
 */
@Service
public class ProviderCertificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderCertificationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Original evidence capture (unchanged, backward compatible) ----

    public void recordSandboxEvidence(
            String providerCode,
            String channelCode,
            String scenarioName,
            long runId,
            String runStatus,
            String evidenceSummary) {
        MapSqlParameterSource p = baseParams(providerCode, channelCode, scenarioName);
        p.addValue("evidence_type", "SANDBOX_RUN");
        p.addValue("run_id", runId);
        p.addValue("statement_run_id", null);
        p.addValue("evidence_status", "PASSED".equalsIgnoreCase(runStatus) ? "CAPTURED" : "FAILED");
        p.addValue("summary", evidenceSummary);
        p.addValue("storage_ref", null);
        jdbcTemplate.update(
                "INSERT INTO provider_certification_evidence "
                        + "(provider_code, channel_code, scenario_name, evidence_type, run_id, statement_run_id, evidence_status, evidence_summary, storage_ref) "
                        + "VALUES (:provider_code, :channel_code, :scenario_name, :evidence_type, :run_id, :statement_run_id, :evidence_status, :summary, :storage_ref)",
                p);
    }

    public int recordStatementEvidence(
            String providerCode,
            String channelCode,
            String scenarioName,
            long statementRunId,
            String evidenceStatus,
            String evidenceSummary,
            String storageRef) {
        MapSqlParameterSource p = baseParams(providerCode, channelCode, scenarioName);
        p.addValue("evidence_type", "STATEMENT_VALIDATION");
        p.addValue("run_id", null);
        p.addValue("statement_run_id", statementRunId);
        p.addValue("evidence_status", normalized(evidenceStatus, "CAPTURED"));
        p.addValue("summary", evidenceSummary);
        p.addValue("storage_ref", storageRef);
        return jdbcTemplate.update(
                "INSERT INTO provider_certification_evidence "
                        + "(provider_code, channel_code, scenario_name, evidence_type, run_id, statement_run_id, evidence_status, evidence_summary, storage_ref) "
                        + "VALUES (:provider_code, :channel_code, :scenario_name, :evidence_type, :run_id, :statement_run_id, :evidence_status, :summary, :storage_ref)",
                p);
    }

    public int approveEvidence(long id, String approvedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("approved_by", blank(approvedBy) ? "system" : approvedBy.trim());
        return jdbcTemplate.update(
                "UPDATE provider_certification_evidence SET evidence_status='APPROVED', "
                        + "approved_by=:approved_by, approved_at=CURRENT_TIMESTAMP "
                        + "WHERE id=:id AND evidence_status='CAPTURED'",
                p);
    }

    public List<Map<String, Object>> listEvidence(String providerCode, String channelCode) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", blank(providerCode) ? null : providerCode.trim());
        p.addValue("channel_code", blank(channelCode) ? null : channelCode.trim());
        return jdbcTemplate.queryForList(
                "SELECT id, provider_code, channel_code, scenario_name, evidence_type, run_id, "
                        + "statement_run_id, evidence_status, evidence_summary, storage_ref, approved_by, "
                        + "approved_at, created_at, updated_at "
                        + "FROM provider_certification_evidence "
                        + "WHERE (:provider_code IS NULL OR provider_code=:provider_code) "
                        + "AND (:channel_code IS NULL OR channel_code=:channel_code) "
                        + "ORDER BY created_at DESC LIMIT 500",
                p);
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
                "evidenceByStatus",
                jdbcTemplate.queryForList(
                        "SELECT evidence_status, COUNT(*) AS count FROM provider_certification_evidence GROUP BY evidence_status",
                        new MapSqlParameterSource()));
        result.put(
                "coverage",
                jdbcTemplate.queryForList(
                        "SELECT r.provider_code, r.channel_code, r.scenario_name, "
                                + "MAX(e.created_at) AS latest_evidence_at, "
                                + "COALESCE(MAX(CASE WHEN e.evidence_status='APPROVED' THEN 1 ELSE 0 END), 0) AS approved "
                                + "FROM provider_certification_requirements r "
                                + "LEFT JOIN provider_certification_evidence e "
                                + "ON (e.scenario_name=r.scenario_name AND (r.provider_code='*' OR r.provider_code=e.provider_code) "
                                + "AND (r.channel_code='*' OR r.channel_code=e.channel_code)) "
                                + "WHERE r.required_flag='YES' "
                                + "GROUP BY r.provider_code, r.channel_code, r.scenario_name "
                                + "ORDER BY r.provider_code, r.channel_code, r.scenario_name",
                        new MapSqlParameterSource()));
        result.put(
                "runsByStatus",
                jdbcTemplate.queryForList(
                        "SELECT run_status, COUNT(*) AS count FROM provider_certification_runs GROUP BY run_status",
                        new MapSqlParameterSource()));
        return result;
    }

    // ---- Run lifecycle (P0 §1) ----

    /** Creates a DRAFT run for the provider/channel and seeds its required scenario rows. */
    public long createRun(
            String providerCode,
            String channelCode,
            String environment,
            String scopeType,
            String country,
            String currency,
            String createdBy) {
        String env = uppercase(blank(environment) ? "SANDBOX" : environment);
        String scope = uppercase(blank(scopeType) ? "GLOBAL" : scopeType);
        if (!"PRODUCTION".equals(env) && !"SANDBOX".equals(env)) {
            throw new IllegalArgumentException("environment must be SANDBOX or PRODUCTION");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("provider_code", normalized(providerCode, "UNKNOWN"))
                        .addValue("channel_code", normalized(channelCode, "UNKNOWN"))
                        .addValue("environment", env)
                        .addValue("scope_type", scope)
                        .addValue("country", blank(country) ? null : country.trim().toUpperCase())
                        .addValue(
                                "currency", blank(currency) ? null : currency.trim().toUpperCase())
                        .addValue("created_by", blank(createdBy) ? "system" : createdBy.trim());
        jdbcTemplate.update(
                "INSERT INTO provider_certification_runs "
                        + "(provider_code, channel_code, environment, scope_type, country, currency, created_by) "
                        + "VALUES (:provider_code, :channel_code, :environment, :scope_type, :country, :currency, :created_by)",
                p);
        Long runId = lastInsertId();
        if (runId == null || runId == 0L) {
            throw new IllegalStateException("Failed to create provider certification run");
        }
        seedRequiredScenarios(runId);
        return runId;
    }

    /** Moves a DRAFT run to RUNNING. */
    public Map<String, Object> startRun(long runId, String startedBy) {
        requireStatus(runId, "DRAFT", "start");
        update(
                "UPDATE provider_certification_runs SET run_status='RUNNING', started_at=COALESCE(started_at, CURRENT_TIMESTAMP) WHERE id=:runId",
                new MapSqlParameterSource("runId", runId));
        return statusOf(runId, startedBy);
    }

    /** Links an approved evidence row to a run scenario and recomputes run flags. */
    public Map<String, Object> linkEvidence(
            long runId, long evidenceId, String scenarioName, String updatedBy) {
        ensureRunExists(runId);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("evidenceId", evidenceId)
                        .addValue(
                                "scenarioName", normalized(scenarioName, "UNKNOWN").toLowerCase());
        int linked =
                jdbcTemplate.update(
                        "UPDATE provider_certification_evidence SET run_id=:runId WHERE id=:evidenceId",
                        p);
        if (linked == 0) {
            throw new IllegalArgumentException("evidence not found: " + evidenceId);
        }
        jdbcTemplate.update(
                "UPDATE provider_certification_run_scenarios SET evidence_status='APPROVED', "
                        + "updated_at=CURRENT_TIMESTAMP WHERE run_id=:runId AND scenario_name=:scenarioName",
                p);
        recalculate(runId);
        return statusOf(runId, updatedBy);
    }

    /** Records a scenario result (PASSED/FAILED) and recomputes the run flags. */
    public Map<String, Object> recordScenarioResult(
            long runId,
            String scenarioName,
            String scenarioResult,
            String observedStatus,
            String notes,
            String updatedBy) {
        ensureRunExists(runId);
        String result = uppercase(blank(scenarioResult) ? "PENDING" : scenarioResult);
        if (!"PASSED".equals(result) && !"FAILED".equals(result) && !"PENDING".equals(result)) {
            throw new IllegalArgumentException("scenarioResult must be PASSED, FAILED or PENDING");
        }
        int updated =
                jdbcTemplate.update(
                        "UPDATE provider_certification_run_scenarios SET scenario_result=:result, "
                                + "observed_status=COALESCE(:observedStatus, observed_status), "
                                + "notes=COALESCE(:notes, notes), updated_at=CURRENT_TIMESTAMP "
                                + "WHERE run_id=:runId AND scenario_name=:scenarioName",
                        new MapSqlParameterSource()
                                .addValue("runId", runId)
                                .addValue(
                                        "scenarioName",
                                        normalized(scenarioName, "UNKNOWN").toLowerCase())
                                .addValue("result", result)
                                .addValue(
                                        "observedStatus",
                                        blank(observedStatus) ? null : observedStatus.trim())
                                .addValue("notes", blank(notes) ? null : notes.trim()));
        if (updated == 0) {
            throw new IllegalArgumentException("scenario not found: " + scenarioName);
        }
        recalculate(runId);
        return statusOf(runId, updatedBy);
    }

    /** Records an exception (blocking by default) and recomputes the run flags. */
    public Map<String, Object> addException(
            long runId,
            String exceptionCode,
            String exceptionType,
            String severity,
            String description,
            String createdBy) {
        ensureRunExists(runId);
        String type = uppercase(blank(exceptionType) ? "BLOCKING" : exceptionType);
        if (!"BLOCKING".equals(type) && !"NON_BLOCKING".equals(type)) {
            throw new IllegalArgumentException("exceptionType must be BLOCKING or NON_BLOCKING");
        }
        jdbcTemplate.update(
                "INSERT INTO provider_certification_run_exceptions "
                        + "(run_id, exception_code, exception_type, severity, description, created_by) "
                        + "VALUES (:runId, :code, :type, :severity, :description, :createdBy)",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("code", normalized(exceptionCode, "EXCEPTION"))
                        .addValue("type", type)
                        .addValue("severity", uppercase(blank(severity) ? "MEDIUM" : severity))
                        .addValue("description", blank(description) ? null : description.trim())
                        .addValue("createdBy", blank(createdBy) ? "system" : createdBy.trim()));
        recalculate(runId);
        return statusOf(runId, createdBy);
    }

    /** Resolves an exception and recomputes the run flags. */
    public Map<String, Object> resolveException(
            long runId, long exceptionId, String resolution, String resolvedBy) {
        ensureRunExists(runId);
        jdbcTemplate.update(
                "UPDATE provider_certification_run_exceptions SET resolved_flag=1, resolution=:resolution, "
                        + "resolved_by=:resolvedBy, resolved_at=CURRENT_TIMESTAMP "
                        + "WHERE id=:exceptionId AND run_id=:runId",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("exceptionId", exceptionId)
                        .addValue("resolution", blank(resolution) ? null : resolution.trim())
                        .addValue("resolvedBy", blank(resolvedBy) ? "system" : resolvedBy.trim()));
        recalculate(runId);
        return statusOf(runId, resolvedBy);
    }

    /** Submits for review once evidence is complete (no required scenario left PENDING). */
    public Map<String, Object> submitForReview(long runId, String submittedBy) {
        ensureRunExists(runId);
        Object openScenarios =
                scalar(
                        "SELECT COUNT(*) FROM provider_certification_run_scenarios WHERE run_id=:runId AND scenario_result='PENDING'",
                        new MapSqlParameterSource("runId", runId));
        if (openScenarios instanceof Number number && number.longValue() > 0) {
            throw new IllegalStateException(
                    "All required scenarios must have a result before review: "
                            + number.longValue()
                            + " still PENDING");
        }
        update(
                "UPDATE provider_certification_runs SET run_status='REVIEW_PENDING', reviewed_by=:reviewedBy, "
                        + "reviewed_at=CURRENT_TIMESTAMP WHERE id=:runId AND run_status IN ('EVIDENCE_PENDING','RUNNING')",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue(
                                "reviewedBy", blank(submittedBy) ? "system" : submittedBy.trim()));
        return statusOf(runId, submittedBy);
    }

    /** Approves a REVIEW_PENDING run, requiring all scenarios passed and no unresolved blockers. */
    public Map<String, Object> approveRun(long runId, String approvedBy, String expiresInDays) {
        ensureRunExists(runId);
        Object status =
                scalar(
                        "SELECT run_status FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (status == null || !"REVIEW_PENDING".equals(status.toString())) {
            throw new IllegalStateException("run must be REVIEW_PENDING before approval");
        }
        Object allPassed =
                scalar(
                        "SELECT all_required_scenarios_passed FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        Object blockers =
                scalar(
                        "SELECT unresolved_blocking_exceptions FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (allPassed instanceof Number number && number.longValue() == 0) {
            throw new IllegalStateException("cannot approve: not all required scenarios passed");
        }
        if (blockers instanceof Number number && number.longValue() != 0) {
            throw new IllegalStateException(
                    "cannot approve: unresolved blocking exceptions remain");
        }
        Instant expires = Instant.now().plus(parseDays(expiresInDays), ChronoUnit.DAYS);
        update(
                "UPDATE provider_certification_runs SET run_status='APPROVED', approved_by=:approvedBy, "
                        + "decided_at=CURRENT_TIMESTAMP, expires_at=:expiresAt WHERE id=:runId",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("approvedBy", blank(approvedBy) ? "system" : approvedBy.trim())
                        .addValue("expiresAt", Timestamp.from(expires)));
        return statusOf(runId, approvedBy);
    }

    /** Rejects a REVIEW_PENDING run with a reason. */
    public Map<String, Object> rejectRun(long runId, String reason, String rejectedBy) {
        ensureRunExists(runId);
        Object status =
                scalar(
                        "SELECT run_status FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (status == null || !"REVIEW_PENDING".equals(status.toString())) {
            throw new IllegalStateException("run must be REVIEW_PENDING before rejection");
        }
        update(
                "UPDATE provider_certification_runs SET run_status='REJECTED', reject_reason=:reason, "
                        + "decided_at=CURRENT_TIMESTAMP WHERE id=:runId",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("reason", blank(reason) ? "Rejected" : reason.trim()));
        return statusOf(runId, rejectedBy);
    }

    /** Marks EXPIRED runs (surfaced on list; the scheduler-driven expiry is a later concern). */
    public int expireIfDue() {
        return jdbcTemplate.update(
                "UPDATE provider_certification_runs SET run_status='EXPIRED' "
                        + "WHERE run_status='APPROVED' AND expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP",
                new MapSqlParameterSource());
    }

    /** The P0 gate: is this provider/channel safe to activate in production right now? */
    public Map<String, Object> productionReadiness(String providerCode, String channelCode) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT run_status, all_required_scenarios_passed, unresolved_blocking_exceptions, "
                                + "expires_at, approved_by, decided_at "
                                + "FROM provider_certification_runs "
                                + "WHERE provider_code=:providerCode AND channel_code=:channelCode "
                                + "AND environment='PRODUCTION' AND scope_type='GLOBAL' "
                                + "ORDER BY decided_at DESC LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("providerCode", normalized(providerCode, "UNKNOWN"))
                                .addValue("channelCode", normalized(channelCode, "UNKNOWN")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerCode", providerCode);
        result.put("channelCode", channelCode);
        if (rows.isEmpty()) {
            result.put("ready", false);
            result.put("status", "NO_RUN");
            result.put("reason", "No PRODUCTION certification run exists for this channel");
            return result;
        }
        Map<String, Object> row = rows.get(0);
        String status = String.valueOf(row.get("run_status"));
        boolean allPassed = truthy(row.get("all_required_scenarios_passed"));
        boolean blockers = truthy(row.get("unresolved_blocking_exceptions"));
        Timestamp expiresAt = (Timestamp) row.get("expires_at");
        boolean expired = expiresAt != null && expiresAt.toInstant().isBefore(Instant.now());

        boolean ready = "APPROVED".equals(status) && allPassed && !blockers && !expired;
        String reason = "";
        if (!"APPROVED".equals(status)) {
            reason = "Certification run status is " + status + " (APPROVED required)";
        } else if (expired) {
            reason = "Certification run expired at " + expiresAt;
        } else if (!allPassed) {
            reason = "Not all required scenarios passed";
        } else if (blockers) {
            reason = "Unresolved blocking exceptions remain";
        }
        result.put("ready", ready);
        result.put("status", status);
        result.put("allRequiredScenariosPassed", allPassed);
        result.put("unresolvedBlockingExceptions", blockers);
        result.put("expiresAt", expiresAt == null ? null : expiresAt.toInstant().toString());
        result.put("reason", reason);
        return result;
    }

    public List<Map<String, Object>> listRuns(
            String providerCode, String channelCode, String status, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", blank(providerCode) ? null : providerCode.trim());
        p.addValue("channel_code", blank(channelCode) ? null : channelCode.trim());
        p.addValue("run_status", blank(status) ? null : status.trim().toUpperCase());
        p.addValue("limit", Math.max(1, Math.min(limit, 250)));
        return jdbcTemplate.queryForList(
                "SELECT id, provider_code, channel_code, country, currency, environment, scope_type, "
                        + "run_status, all_required_scenarios_passed, unresolved_blocking_exceptions, created_by, "
                        + "reviewed_by, approved_by, reject_reason, started_at, evidence_completed_at, "
                        + "reviewed_at, decided_at, expires_at, created_at, updated_at "
                        + "FROM provider_certification_runs "
                        + "WHERE (:provider_code IS NULL OR provider_code=:provider_code) "
                        + "AND (:channel_code IS NULL OR channel_code=:channel_code) "
                        + "AND (:run_status IS NULL OR run_status=:run_status) "
                        + "ORDER BY created_at DESC LIMIT :limit",
                p);
    }

    public Map<String, Object> getRun(long runId) {
        ensureRunExists(runId);
        Map<String, Object> result = statusOf(runId, null);
        result.put(
                "scenarios",
                jdbcTemplate.queryForList(
                        "SELECT scenario_name, scenario_result, observed_status, evidence_status, notes "
                                + "FROM provider_certification_run_scenarios WHERE run_id=:runId ORDER BY scenario_name",
                        new MapSqlParameterSource("runId", runId)));
        result.put(
                "exceptions",
                jdbcTemplate.queryForList(
                        "SELECT id, exception_code, exception_type, severity, description, resolution, "
                                + "resolved_flag, created_by, resolved_by, created_at, resolved_at "
                                + "FROM provider_certification_run_exceptions WHERE run_id=:runId ORDER BY id",
                        new MapSqlParameterSource("runId", runId)));
        return result;
    }

    // ---- Internal helpers ----

    private void seedRequiredScenarios(long runId) {
        List<String> required =
                jdbcTemplate.queryForList(
                        "SELECT scenario_name FROM provider_certification_requirements "
                                + "WHERE required_flag='YES' AND (provider_code='*' OR provider_code='UNKNOWN') "
                                + "AND (channel_code='*' OR channel_code='UNKNOWN')",
                        new MapSqlParameterSource(),
                        String.class);
        for (String scenario : required) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO provider_certification_run_scenarios "
                            + "(run_id, scenario_name) VALUES (:runId, :scenarioName)",
                    new MapSqlParameterSource()
                            .addValue("runId", runId)
                            .addValue("scenarioName", scenario.toLowerCase()));
        }
        if (required.isEmpty()) {
            jdbcTemplate.update(
                    "INSERT IGNORE INTO provider_certification_run_scenarios "
                            + "(run_id, scenario_name) VALUES (:runId, 'manual_review')",
                    new MapSqlParameterSource("runId", runId));
        }
    }

    /**
     * Recomputes {@code all_required_scenarios_passed} (every scenario PASSED) and {@code
     * unresolved_blocking_exceptions} (any BLOCKING exception unresolved). When both resolve true,
     * the run transitions EVIDENCE_PENDING -> REVIEW_PENDING.
     */
    private void recalculate(long runId) {
        update(
                "UPDATE provider_certification_runs SET all_required_scenarios_passed=:allPassed, "
                        + "unresolved_blocking_exceptions=:blockers, "
                        + "evidence_completed_at=COALESCE(evidence_completed_at, CURRENT_TIMESTAMP) "
                        + "WHERE id=:runId",
                new MapSqlParameterSource()
                        .addValue("runId", runId)
                        .addValue("allPassed", hasPendingScenario(runId) ? 0 : 1)
                        .addValue("blockers", hasUnresolvedBlockingException(runId) ? 1 : 0));
    }

    private boolean hasPendingScenario(long runId) {
        Object value =
                scalar(
                        "SELECT COUNT(*) FROM provider_certification_run_scenarios "
                                + "WHERE run_id=:runId AND scenario_result <> 'PASSED'",
                        new MapSqlParameterSource("runId", runId));
        return value instanceof Number number && number.longValue() > 0;
    }

    private boolean hasUnresolvedBlockingException(long runId) {
        Object value =
                scalar(
                        "SELECT COUNT(*) FROM provider_certification_run_exceptions "
                                + "WHERE run_id=:runId AND exception_type='BLOCKING' AND resolved_flag=0",
                        new MapSqlParameterSource("runId", runId));
        return value instanceof Number number && number.longValue() > 0;
    }

    private void requireStatus(long runId, String expected, String action) {
        ensureRunExists(runId);
        Object status =
                scalar(
                        "SELECT run_status FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (status == null || !expected.equals(status.toString())) {
            throw new IllegalStateException(
                    "cannot " + action + ": run must be " + expected + " but is " + status);
        }
    }

    private void ensureRunExists(long runId) {
        Object status =
                scalar(
                        "SELECT run_status FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (status == null) {
            throw new IllegalArgumentException("certification run not found: " + runId);
        }
    }

    private Map<String, Object> statusOf(long runId, String actor) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id, provider_code, channel_code, environment, scope_type, run_status, "
                                + "all_required_scenarios_passed, unresolved_blocking_exceptions, created_by, "
                                + "reviewed_by, approved_by, reject_reason, started_at, evidence_completed_at, "
                                + "reviewed_at, decided_at, expires_at, updated_at "
                                + "FROM provider_certification_runs WHERE id=:runId",
                        new MapSqlParameterSource("runId", runId));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("certification run not found: " + runId);
        }
        Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
        row.put("runId", row.get("id"));
        row.remove("id");
        row.put("allRequiredScenariosPassed", truthy(row.get("all_required_scenarios_passed")));
        row.put("unresolvedBlockingExceptions", truthy(row.get("unresolved_blocking_exceptions")));
        if (actor != null) {
            row.put("actor", actor);
        }
        return row;
    }

    private Long lastInsertId() {
        return jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
    }

    private Object scalar(String sql, MapSqlParameterSource params) {
        try {
            return jdbcTemplate.queryForObject(sql, params, Object.class);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void update(String sql, MapSqlParameterSource params) {
        jdbcTemplate.update(sql, params);
    }

    private static long parseDays(String value) {
        if (value == null || value.isBlank()) {
            return 365L;
        }
        try {
            long days = Long.parseLong(value.trim());
            return Math.max(1, Math.min(days, 3650L));
        } catch (NumberFormatException e) {
            return 365L;
        }
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.longValue() != 0;
        }
        String s = value.toString().trim();
        return "1".equals(s) || "TRUE".equalsIgnoreCase(s) || "YES".equalsIgnoreCase(s);
    }

    private static String stringValue(MapSqlParameterSource params, String key) {
        return params.getValue(key) == null ? null : params.getValue(key).toString();
    }

    private MapSqlParameterSource baseParams(
            String providerCode, String channelCode, String scenarioName) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", normalized(providerCode, "UNKNOWN"));
        p.addValue("channel_code", normalized(channelCode, "UNKNOWN"));
        p.addValue("scenario_name", normalized(scenarioName, "manual_review").toLowerCase());
        return p;
    }

    private String normalized(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private static String uppercase(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
