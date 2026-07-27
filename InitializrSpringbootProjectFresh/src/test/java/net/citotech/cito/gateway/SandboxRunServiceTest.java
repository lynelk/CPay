package net.citotech.cito.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SandboxRunServiceTest {

    @Test
    void failedAdapterResponseIsRecordedAsFailedNotPassed() {
        PaymentChannelAdapter adapter = mock(PaymentChannelAdapter.class);
        when(adapter.channelCode()).thenReturn("MTN_MOMO");
        when(adapter.displayName()).thenReturn("MTN");
        GateWayResponse failure = new GateWayResponse();
        failure.setStatus("FAILED");
        when(adapter.collect(any(PaymentGatewayRequest.class))).thenReturn(failure);
        PaymentChannelRegistry registry = new PaymentChannelRegistry(List.of(adapter));

        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
            .thenReturn(1L);
        ProviderCertificationService certificationService = mock(ProviderCertificationService.class);

        SandboxRunService service = new SandboxRunService(registry, jdbcTemplate, certificationService);
        service.run("MTN_MOMO", "collect_success", "256770000000", "1000");

        verify(certificationService).recordSandboxEvidence(
            eq("MTN"), eq("MTN_MOMO"), eq("collect_success"), eq(1L), eq("FAILED"), anyString());
    }

    @Test
    void successfulAdapterResponseIsRecordedAsPassed() {
        PaymentChannelAdapter adapter = mock(PaymentChannelAdapter.class);
        when(adapter.channelCode()).thenReturn("MTN_MOMO");
        when(adapter.displayName()).thenReturn("MTN");
        GateWayResponse success = new GateWayResponse();
        success.setStatus("SUCCESS");
        when(adapter.collect(any(PaymentGatewayRequest.class))).thenReturn(success);
        PaymentChannelRegistry registry = new PaymentChannelRegistry(List.of(adapter));

        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
            .thenReturn(1L);
        ProviderCertificationService certificationService = mock(ProviderCertificationService.class);

        SandboxRunService service = new SandboxRunService(registry, jdbcTemplate, certificationService);
        service.run("MTN_MOMO", "collect_success", "256770000000", "1000");

        verify(certificationService).recordSandboxEvidence(
            eq("MTN"), eq("MTN_MOMO"), eq("collect_success"), eq(1L), eq("PASSED"), anyString());
    }
}
