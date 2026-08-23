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
    void sandboxTrafficDoesNotConsultProductionRollout() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        guard.enforcePayment(merchant(), "SANDBOX", "PAYOUT");

        verify(jdbc, never())
                .queryForObject(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class));
    }

    @Test
    void productionPayoutFailsWhenPayoutStageIsDisabled() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("payouts_enabled")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.enforcePayment(merchant(), "PRODUCTION", "PAYOUT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void productionDailyLimitFailsClosedAtStageLimit() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("collections_enabled")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("production_daily_limit")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(10);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("provider_endpoint_runs")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(10);
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.enforcePayment(merchant(), "PRODUCTION", "COLLECT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("daily limit reached");
    }

    @Test
    void activationRequiresPromotionEvidence() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("merchant_go_live_requests")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "merchant_id", 10L,
                        "request_status", "APPROVED",
                        "current_stage", "APPROVED",
                        "decision_by", "ops-a")));
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
    void sameAdminCannotApproveConsecutiveReviewStages() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(any(String.class), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "merchant_id", 10L,
                        "request_status", "IN_REVIEW",
                        "current_stage", "COMPLIANCE_REVIEW",
                        "decision_by", "admin-a")));
        SandboxProductionGuardService guard = new SandboxProductionGuardService(jdbc);

        assertThatThrownBy(() -> guard.assertDecisionAllowed(7L, "ADVANCE", "admin-a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different administrator");
    }

    @Test
    void payoutRolloutRequiresPassedSmokeTest() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("request_status='ACTIVATED'")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
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
