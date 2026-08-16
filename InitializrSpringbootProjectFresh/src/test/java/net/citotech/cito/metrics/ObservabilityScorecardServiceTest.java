package net.citotech.cito.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * P0 section 6: verifies the observability scorecard registers every required gauge and that each
 * gauge resolves to a fresh SQL count on scrape (or 0 on a DB failure).
 */
class ObservabilityScorecardServiceTest {

    @Test
    void registersEveryScorecardGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityScorecardService service =
                new ObservabilityScorecardService(registry, mock(NamedParameterJdbcTemplate.class));
        service.registerGauges();

        assertThat(registry.get("cpay.payout.approval.pending").gauge()).isNotNull();
        assertThat(registry.get("cpay.reconciliation.exceptions.open").gauge()).isNotNull();
        assertThat(registry.get("cpay.reconciliation.exceptions.open_high").gauge()).isNotNull();
        assertThat(registry.get("cpay.compliance.cases.open").gauge()).isNotNull();
        assertThat(registry.get("cpay.webhook.deliveries.parked").gauge()).isNotNull();
        for (String reason : List.of("115", "116", "122")) {
            assertThat(
                            registry.get("cpay.api.signature_failures")
                                    .tag("reason", reason)
                                    .gauge())
                    .isNotNull();
        }
    }

    @Test
    void gaugesResolveToDatabaseCountsOnScrape() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        contains("payout_approval_queue"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        argThat(
                                sql ->
                                        sql != null
                                                && sql.contains("reconciliation_exceptions")
                                                && !sql.contains("CRITICAL")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(2);
        when(jdbcTemplate.queryForObject(
                        contains("CRITICAL"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(3);
        when(jdbcTemplate.queryForObject(
                        contains("compliance_cases"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(4);
        when(jdbcTemplate.queryForObject(
                        contains("delivery_status='PARKED'"),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(5);
        ObservabilityScorecardService service =
                new ObservabilityScorecardService(registry, jdbcTemplate);
        service.registerGauges();

        assertThat(registry.get("cpay.payout.approval.pending").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.get("cpay.reconciliation.exceptions.open").gauge().value())
                .isEqualTo(2.0);
        assertThat(registry.get("cpay.reconciliation.exceptions.open_high").gauge().value())
                .isEqualTo(3.0);
        assertThat(registry.get("cpay.compliance.cases.open").gauge().value())
                .isEqualTo(4.0);
        assertThat(registry.get("cpay.webhook.deliveries.parked").gauge().value())
                .isEqualTo(5.0);
    }

    @Test
    void signatureFailureGaugesReflectRecordedFailures() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ObservabilityScorecardService service =
                new ObservabilityScorecardService(registry, mock(NamedParameterJdbcTemplate.class));
        service.registerGauges();

        long before115 =
                SignatureVerificationFailureRegistry.snapshot().getOrDefault("115", 0L);
        SignatureVerificationFailureRegistry.record("115");
        SignatureVerificationFailureRegistry.record("116");
        SignatureVerificationFailureRegistry.record("122");

        assertThat(registry.get("cpay.api.signature_failures").tag("reason", "115").gauge().value())
                .isEqualTo(before115 + 1);
        assertThat(registry.get("cpay.api.signature_failures").tag("reason", "116").gauge().value())
                .isEqualTo(1.0);
        assertThat(registry.get("cpay.api.signature_failures").tag("reason", "122").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void gaugesFallBackToZeroWhenTheDatabaseIsUnavailable() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        any(String.class), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenThrow(new IllegalStateException("db down"));
        ObservabilityScorecardService service =
                new ObservabilityScorecardService(registry, jdbcTemplate);
        service.registerGauges();

        assertThat(registry.get("cpay.payout.approval.pending").gauge().value()).isZero();
        assertThat(registry.get("cpay.reconciliation.exceptions.open").gauge().value()).isZero();
        assertThat(registry.get("cpay.compliance.cases.open").gauge().value()).isZero();
        assertThat(registry.get("cpay.webhook.deliveries.parked").gauge().value()).isZero();
    }
}
