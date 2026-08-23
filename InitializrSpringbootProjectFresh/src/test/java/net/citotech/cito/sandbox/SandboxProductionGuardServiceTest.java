package net.citotech.cito.sandbox;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SandboxProductionGuardServiceTest {

    @Test
    void sandboxTrafficDoesNotConsultProductionRolloutOrQuota() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        guard.reserveProductionExecution(merchant(), "SANDBOX", "PAYOUT", "ref-1");

        verify(jdbc, never())
                .queryForObject(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class));
        verify(jdbc, never()).update(any(String.class), any(MapSqlParameterSource.class));
    }

    @Test
    void productionPayoutFailsWhenPayoutStageIsDisabled() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("payouts_enabled")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "stage_code",
                                        "COLLECTIONS",
                                        "production_daily_limit",
                                        10,
                                        "capability_enabled",
                                        0)));
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(
                        () ->
                                guard.reserveProductionExecution(
                                        merchant(), "PRODUCTION", "PAYOUT", "ref-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void productionDailyLimitUsesAuthoritativeUsageLedger() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("collections_enabled")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "stage_code",
                                        "COLLECTIONS",
                                        "production_daily_limit",
                                        10,
                                        "capability_enabled",
                                        1)));
        when(jdbc.queryForObject(
                        argThat(
                                sql ->
                                        sql != null
                                                && sql.contains("merchant_production_usage")
                                                && sql.contains("request_reference")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        when(jdbc.queryForObject(
                        argThat(
                                sql ->
                                        sql != null
                                                && sql.contains("merchant_production_usage")
                                                && sql.contains("usage_date=CURRENT_DATE()")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(10);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(
                        () ->
                                guard.reserveProductionExecution(
                                        merchant(), "PRODUCTION", "COLLECT", "ref-2"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("daily limit reached");
    }

    @Test
    void repeatedCommandReferenceDoesNotConsumeAnotherQuotaSlot() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("collections_enabled")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "stage_code",
                                        "COLLECTIONS",
                                        "production_daily_limit",
                                        10,
                                        "capability_enabled",
                                        1)));
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("request_reference")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        guard.reserveProductionExecution(merchant(), "PRODUCTION", "COLLECT", "ref-existing");

        verify(jdbc, never())
                .update(
                        argThat(sql -> sql != null && sql.contains("merchant_production_usage")),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void activationRequiresPromotionEvidence() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("merchant_go_live_requests")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "merchant_id",
                                        10L,
                                        "request_status",
                                        "APPROVED",
                                        "current_stage",
                                        "APPROVED",
                                        "decision_by",
                                        "ops-a")));
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("sandbox_configuration_promotions")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertDecisionAllowed(7L, "ADVANCE", "ops-b"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("promoted");
    }

    @Test
    void terminalGoLiveRequestCannotBeRejectedOrAdvanced() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "merchant_id",
                                        10L,
                                        "request_status",
                                        "ACTIVATED",
                                        "current_stage",
                                        "ACTIVATED",
                                        "decision_by",
                                        "ops-b")));
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertDecisionAllowed(7L, "REJECT", "ops-c"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void sameAdminCannotApproveConsecutiveReviewStages() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "merchant_id",
                                        10L,
                                        "request_status",
                                        "IN_REVIEW",
                                        "current_stage",
                                        "COMPLIANCE_REVIEW",
                                        "decision_by",
                                        "admin-a")));
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertDecisionAllowed(7L, "ADVANCE", "admin-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different administrator");
    }

    @Test
    void rolloutCannotSkipIntermediateStages() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("request_status='ACTIVATED'")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("stage_code")),
                        any(MapSqlParameterSource.class),
                        eq(String.class)))
                .thenReturn(List.of("COLLECTIONS"));
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertRolloutStageAllowed(10L, "PAYOUTS_LOW_LIMIT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("one stage at a time");
    }

    @Test
    void payoutRolloutRequiresPassedSmokeTest() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("request_status='ACTIVATED'")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("stage_code")),
                        any(MapSqlParameterSource.class),
                        eq(String.class)))
                .thenReturn(List.of("REFUNDS"));
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("sandbox_live_smoke_tests")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertRolloutStageAllowed(10L, "PAYOUTS_LOW_LIMIT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("smoke test");
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setAccount_number("M100");
        return merchant;
    }
}
