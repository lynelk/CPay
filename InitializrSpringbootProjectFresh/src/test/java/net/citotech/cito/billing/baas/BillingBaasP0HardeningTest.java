package net.citotech.cito.billing.baas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

class BillingBaasP0HardeningTest {

    @Test
    void protectedActionConsumptionIsOneTimeTenantScopedAndRequesterBound() {
        NamedParameterJdbcTemplate jdbc =
                org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        BillingBaasProtectedActionService service = new BillingBaasProtectedActionService(jdbc);
        BillingBaasContext context = new BillingBaasContext(77L, 9L, 10L, 11L, "production");
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        service.consumeApproved(context, "charge_reverse", "charge_reservation", "RES-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(sql.capture(), params.capture());
        assertThat(sql.getValue())
                .contains("billing_tenant_id=:tenant")
                .contains("status='APPROVED'")
                .contains("requested_by=:actor")
                .contains("approved_by<>:actor")
                .contains("status='CONSUMED'");
        assertThat(params.getValue().getValue("tenant")).isEqualTo(77L);
        assertThat(params.getValue().getValue("actor")).isEqualTo("SERVICE_ACCOUNT:11");
        assertThat(params.getValue().getValue("reference")).isEqualTo("RES-1");
    }

    @Test
    void protectedActionConsumptionFailsClosedWhenNoUnusedApprovalExists() {
        NamedParameterJdbcTemplate jdbc =
                org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        BillingBaasProtectedActionService service = new BillingBaasProtectedActionService(jdbc);
        BillingBaasContext context = new BillingBaasContext(77L, 9L, 10L, 11L, "production");
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(
                        () ->
                                service.consumeApproved(
                                        context, "CHARGE_REVERSE", "CHARGE_RESERVATION", "RES-1"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("unused approval");
    }

    @Test
    void expiredReservationStateIsCommittedBeforeTheClientReceivesExpiry() throws Exception {
        Method commit =
                BillingBaasChargingService.class.getMethod(
                        "commit", BillingBaasContext.class, String.class);
        Transactional transactional = commit.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(Arrays.asList(transactional.noRollbackFor()))
                .contains(BillingBaasChargingService.ChargingReservationExpiredException.class);
    }

    @Test
    void migrationPersistsActivationAndProtectedActionConsumptionEvidence() throws IOException {
        try (var stream =
                getClass()
                        .getResourceAsStream("/db/migration/V102__billing_baas_p0_hardening.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql)
                    .contains("activated_by")
                    .contains("activated_at")
                    .contains("consumed_by")
                    .contains("consumed_at")
                    .contains("'CONSUMED'");
        }
    }
}
