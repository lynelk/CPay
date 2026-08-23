package net.citotech.cito.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.ReadinessDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SandboxLifecycleServiceTest {

    @Test
    void sandboxMoneyMovementIsNeverBlockedByProductionRollout() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SandboxLifecycleService service = service(jdbc);

        service.requireProductionCapability(42L, "SANDBOX", "PAYOUT");

        verify(jdbc, never())
                .queryForObject(
                        any(String.class),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class));
    }

    @Test
    void productionPayoutFailsClosedUntilRolloutEnablesIt() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("payouts_enabled")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(0);
        SandboxLifecycleService service = service(jdbc);

        assertThatThrownBy(() -> service.requireProductionCapability(42L, "PRODUCTION", "PAYOUT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void productionCollectionPassesOnceCollectionsStageIsEnabled() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                        argThat(sql -> sql != null && sql.contains("collections_enabled")),
                        any(MapSqlParameterSource.class),
                        eq(Integer.class)))
                .thenReturn(1);
        SandboxLifecycleService service = service(jdbc);

        service.requireProductionCapability(42L, "PRODUCTION", "COLLECT");
    }

    @Test
    void topUpWritesOnlySyntheticWalletStorage() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(
                        argThat(sql -> sql != null && sql.contains("sandbox_wallet_balances")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        when(jdbc.queryForList(
                        argThat(sql -> sql != null && sql.contains("sandbox_wallet_balances")),
                        any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "id", 1L,
                        "channel_code", "GENERAL",
                        "currency", "UGX",
                        "available_balance", new BigDecimal("1000"))));
        SandboxLifecycleService service = service(jdbc);

        Map<String, Object> result =
                service.topUp(42L, "GENERAL", "UGX", new BigDecimal("1000"), "merchant@example.test");

        assertThat(result.get("available_balance")).isEqualTo(new BigDecimal("1000"));
        verify(jdbc, never())
                .update(
                        argThat(sql -> sql != null && sql.contains("merchant_channel_balances")),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void providerResetIsExplicitlySandboxScoped() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        SandboxLifecycleService service = service(jdbc);

        Map<String, Object> result =
                service.reset(42L, "1000042", "PROVIDER_RUNS", "merchant@example.test");

        assertThat(result.get("productionDataTouched")).isEqualTo(false);
        verify(jdbc)
                .update(
                        argThat(
                                sql ->
                                        sql != null
                                                && sql.contains("provider_endpoint_runs")
                                                && sql.contains("environment='SANDBOX'")),
                        any(MapSqlParameterSource.class));
        verify(jdbc, never())
                .update(
                        argThat(
                                sql ->
                                        sql != null
                                                && (sql.contains("merchant_transactions_log")
                                                        || sql.contains("ledger"))),
                        any(MapSqlParameterSource.class));
    }

    @Test
    void oversizedSyntheticTopUpIsRejectedBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SandboxLifecycleService service = service(jdbc);

        assertThatThrownBy(
                        () ->
                                service.topUp(
                                        42L,
                                        "GENERAL",
                                        "UGX",
                                        new BigDecimal("1000000000001"),
                                        "merchant@example.test"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(jdbc, never()).update(any(String.class), any(MapSqlParameterSource.class));
    }

    private SandboxLifecycleService service(NamedParameterJdbcTemplate jdbc) {
        return new SandboxLifecycleService(
                jdbc,
                mock(ReadinessDashboardService.class),
                new ObjectMapper());
    }
}
