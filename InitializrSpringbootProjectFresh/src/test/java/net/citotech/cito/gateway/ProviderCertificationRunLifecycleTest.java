package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** P0 §1 run-lifecycle coverage: approval gates, reject, exceptions, expiry, and readiness. */
class ProviderCertificationRunLifecycleTest {

    private final NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    private final ProviderCertificationService service =
            new ProviderCertificationService(jdbcTemplate);

    @Test
    void createRunSeedsScenariosAndReturnsRunId() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn(42L);
        when(jdbcTemplate.queryForList(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn(List.of("collect_success", "payout_success"));

        long runId =
                service.createRun(
                        "MTN", "MTN_MOMO", "PRODUCTION", "GLOBAL", "UG", "UGX", "ops@example.com");

        assertThat(runId).isEqualTo(42L);
    }

    @Test
    void rejectsRunBeforeApprovalTransitionsToRejected() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("REVIEW_PENDING", "REVIEW_PENDING");
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(runRow(7L, "REJECTED", null, null, 1L, 0L)));

        Map<String, Object> result =
                service.rejectRun(7L, "Insufficient evidence", "ops@example.com");

        assertThat(result.get("run_status")).isEqualTo("REJECTED");
        assertThat(result.get("runId")).isEqualTo(7L);
    }

    @Test
    void rejectRequiresReviewPendingStatus() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("DRAFT", "DRAFT");

        assertThatThrownBy(() -> service.rejectRun(7L, "nope", "ops@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REVIEW_PENDING");
    }

    @Test
    void approveFailsWhenNotAllScenariosPassed() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("REVIEW_PENDING", "REVIEW_PENDING", 0L, 0L);

        assertThatThrownBy(() -> service.approveRun(7L, "ops@example.com", "30"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not all required scenarios passed");
    }

    @Test
    void approveFailsWhenBlockingExceptionUnresolved() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("REVIEW_PENDING", "REVIEW_PENDING", 1L, 1L);

        assertThatThrownBy(() -> service.approveRun(7L, "ops@example.com", "30"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blocking exceptions");
    }

    @Test
    void approveSucceedsWhenAllGatesHold() {
        // status, allPassed, blockers
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("REVIEW_PENDING", "REVIEW_PENDING", 1L, 0L);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                runRow(
                                        7L,
                                        "APPROVED",
                                        "ops@example.com",
                                        Timestamp.from(Instant.now().plusSeconds(86400)),
                                        1L,
                                        0L)));

        Map<String, Object> result = service.approveRun(7L, "ops@example.com", "30");

        assertThat(result.get("run_status")).isEqualTo("APPROVED");
        assertThat(result.get("approved_by")).isEqualTo("ops@example.com");
    }

    @Test
    void productionReadinessIsFalseWhenNoRunExists() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        Map<String, Object> result = service.productionReadiness("MTN", "MTN_MOMO");

        assertThat(result.get("ready")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("NO_RUN");
    }

    @Test
    void productionReadinessIsFalseWhenRunNotApproved() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(readinessRow("DRAFT", 0L, 1L, null)));

        Map<String, Object> result = service.productionReadiness("MTN", "MTN_MOMO");

        assertThat(result.get("ready")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("DRAFT");
        assertThat((String) result.get("reason")).contains("APPROVED required");
    }

    @Test
    void productionReadinessIsFalseWhenExpired() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                readinessRow(
                                        "APPROVED",
                                        1L,
                                        0L,
                                        Timestamp.from(Instant.now().minusSeconds(86400)))));

        Map<String, Object> result = service.productionReadiness("MTN", "MTN_MOMO");

        assertThat(result.get("ready")).isEqualTo(false);
        assertThat((String) result.get("reason")).contains("expired");
    }

    @Test
    void productionReadinessIsTrueWhenApprovedPassedAndUnblocked() {
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                readinessRow(
                                        "APPROVED",
                                        1L,
                                        0L,
                                        Timestamp.from(Instant.now().plusSeconds(86400)))));

        Map<String, Object> result = service.productionReadiness("MTN", "MTN_MOMO");

        assertThat(result.get("ready")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("APPROVED");
    }

    @Test
    void linkEvidenceUpdatesEvidenceRunIdAndScenarioEvidenceStatus() {
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), any(Class.class)))
                .thenReturn("RUNNING");
        when(jdbcTemplate.update(
                        contains("provider_certification_evidence"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(runRow(3L, "RUNNING", null, null, 0L, 0L)));

        Map<String, Object> result =
                service.linkEvidence(3L, 99L, "collect_success", "ops@example.com");

        assertThat(result.get("runId")).isEqualTo(3L);
        assertThat(result.get("run_status")).isEqualTo("RUNNING");
    }

    /** Full row shape expected by {@code statusOf} (18 columns). */
    private static Map<String, Object> runRow(
            long id,
            String status,
            String approvedBy,
            Timestamp expiresAt,
            long allPassed,
            long blockers) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("provider_code", "MTN");
        row.put("channel_code", "MTN_MOMO");
        row.put("environment", "PRODUCTION");
        row.put("scope_type", "GLOBAL");
        row.put("run_status", status);
        row.put("all_required_scenarios_passed", allPassed);
        row.put("unresolved_blocking_exceptions", blockers);
        row.put("created_by", "system");
        row.put("reviewed_by", null);
        row.put("approved_by", approvedBy);
        row.put("reject_reason", null);
        row.put("started_at", null);
        row.put("evidence_completed_at", null);
        row.put("reviewed_at", null);
        row.put("decided_at", Timestamp.from(Instant.now()));
        row.put("expires_at", expiresAt);
        row.put("updated_at", Timestamp.from(Instant.now()));
        return row;
    }

    /** Row shape expected by {@code productionReadiness} (6 columns). */
    private static Map<String, Object> readinessRow(
            String status, long allPassed, long blockers, Timestamp expiresAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("run_status", status);
        row.put("all_required_scenarios_passed", allPassed);
        row.put("unresolved_blocking_exceptions", blockers);
        row.put("expires_at", expiresAt);
        row.put("approved_by", "ops@example.com");
        row.put("decided_at", Timestamp.from(Instant.now()));
        return row;
    }
}
