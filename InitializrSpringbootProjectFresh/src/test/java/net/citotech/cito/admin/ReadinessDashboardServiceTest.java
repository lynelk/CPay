package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ReadinessDashboardServiceTest {

    @Test
    void summaryCountsAcrossWholePlatformWithNoMerchantFilter() {
        // summary() is the pre-existing platform-wide view (audit item O6 adds merchantSummary()
        // alongside it, unchanged). Every scalar count is stubbed to a positive, non-zero value so
        // every "no open X" style check is deliberately ACTION_REQUIRED and every "at least one X
        // happened" style check is READY - proving the checklist wiring, not just the counting.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(2);
        ReadinessDashboardService service = new ReadinessDashboardService(jdbcTemplate);

        Map<String, Object> result = service.summary();

        assertThat(result.get("providerSandboxRuns")).isEqualTo(2);
        assertThat(result.get("statementValidationRuns")).isEqualTo(2);
        assertThat(result.get("callbackSecrets")).isEqualTo(2);
        assertThat(result.get("openAlerts")).isEqualTo(2);
        assertThat(result.get("parkedCallbacks")).isEqualTo(2);
        assertThat(result.get("dailyCloses")).isEqualTo(2);
        assertThat(result.get("adminAuditEvents")).isEqualTo(2);
        assertThat(result.get("openComplianceCases")).isEqualTo(2);
        assertThat(result.get("approvedProviderEvidence")).isEqualTo(2);
        assertThat(result.get("pendingComplianceProfiles")).isEqualTo(2);

        Map<String, Map<String, Object>> checklist = checklistById(result);
        assertThat(checklist).hasSize(10);
        assertThat(checklist.get("provider_sandbox").get("status")).isEqualTo("READY");
        assertThat(checklist.get("statement_validation").get("status")).isEqualTo("READY");
        assertThat(checklist.get("callback_secrets").get("status")).isEqualTo("READY");
        assertThat(checklist.get("provider_certification").get("status")).isEqualTo("READY");
        assertThat(checklist.get("admin_audit").get("status")).isEqualTo("READY");
        // These are "no open X" checks - a positive count means action required.
        assertThat(checklist.get("operations_alerts").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("parked_callbacks").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("compliance_cases").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("compliance_profiles").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("daily_close").get("status")).isEqualTo("READY");
    }

    @Test
    void merchantWithNoConfiguredChannelsAndNoComplianceRecordsDefaultsToActionRequired() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        ReadinessDashboardService service = new ReadinessDashboardService(jdbcTemplate);
        Map<String, Object> result = service.merchantSummary(42L);

        assertThat(result.get("merchantId")).isEqualTo(42L);
        assertThat(result.get("configuredChannels")).isEqualTo(0);

        Map<String, Map<String, Object>> checklist = checklistById(result);
        assertThat(checklist.get("channels_configured").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("provider_sandbox").get("status")).isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("statement_validation").get("status"))
                .isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("provider_certification").get("status"))
                .isEqualTo("ACTION_REQUIRED");
        assertThat(checklist.get("callback_secrets").get("status")).isEqualTo("ACTION_REQUIRED");
        // No compliance profile exists at all yet - that must NOT be reported as READY just because
        // the "pending" count happens to be zero.
        assertThat(checklist.get("compliance_profile").get("status")).isEqualTo("ACTION_REQUIRED");
        // No compliance cases exist at all either, but "no open cases" is a sensible default pass -
        // there is nothing outstanding to review.
        assertThat(checklist.get("compliance_cases").get("status")).isEqualTo("READY");

        // With zero configured channels there must be no attempt to run an empty IN (...) query
        // against the provider tables (which have no merchant reference at all).
        verify(jdbcTemplate, never())
                .queryForObject(
                        argThat(
                                containsAny(
                                        "provider_sandbox_runs",
                                        "provider_statement_validation_runs",
                                        "provider_certification_evidence")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class));
    }

    @Test
    void merchantWithFullyCertifiedChannelAndCleanComplianceIsEntirelyReady() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("MTN_MOMO"));
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("provider_sandbox_runs")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("provider_statement_validation_runs")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("provider_certification_evidence")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("merchant_callback_secrets")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("compliance_profiles", "status IN")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.queryForObject(
                        argThat(containsButNot("compliance_profiles", "status IN")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(containsAll("compliance_cases")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);

        ReadinessDashboardService service = new ReadinessDashboardService(jdbcTemplate);
        Map<String, Object> result = service.merchantSummary(7L);

        assertThat(result.get("configuredChannels")).isEqualTo(1);
        Map<String, Map<String, Object>> checklist = checklistById(result);
        assertThat(checklist).hasSize(7);
        checklist
                .values()
                .forEach(
                        item ->
                                assertThat(item.get("status"))
                                        .as(item.get("id") + " should be READY")
                                        .isEqualTo("READY"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> checklistById(Map<String, Object> result) {
        List<Map<String, Object>> checklist = (List<Map<String, Object>>) result.get("checklist");
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> item : checklist) {
            byId.put((String) item.get("id"), item);
        }
        return byId;
    }

    // Mockito invokes previously-registered ArgumentMatchers against a null placeholder while it is
    // still recording a NEW stub's matcher for the same invocation shape - so every matcher here
    // must
    // tolerate a null "sql" argument rather than assuming it is only ever called with a real value.

    private static ArgumentMatcher<String> containsAll(String... needles) {
        return sql -> {
            if (sql == null) {
                return false;
            }
            for (String needle : needles) {
                if (!sql.contains(needle)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static ArgumentMatcher<String> containsButNot(String included, String excluded) {
        return sql -> sql != null && sql.contains(included) && !sql.contains(excluded);
    }

    private static ArgumentMatcher<String> containsAny(String... needles) {
        return sql -> {
            if (sql == null) {
                return false;
            }
            for (String needle : needles) {
                if (sql.contains(needle)) {
                    return true;
                }
            }
            return false;
        };
    }
}
