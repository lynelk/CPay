package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the generic executor that the Yo! Payments adapter (and other execution-service-backed
 * channels) delegates every collect/payout call to. This is one of the newest money-movement
 * paths and previously had no test coverage (see audit K6): the sandbox-scenario fallback used
 * when no live endpoint is configured, and the production guard that refuses to silently fall
 * back to sandbox behaviour when a real endpoint is required.
 */
class ProviderEndpointExecutionServiceTest {

    @Test
    void sandboxCollectionDefaultsToSuccessfulWhenNoEndpointConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service = new ProviderEndpointExecutionService(jdbcTemplate, tokenStoreService);
        PaymentGatewayRequest request = new PaymentGatewayRequest(
            "M1", "256770000001", 1000.0, "REF-1", "test", "cb", Map.of());

        GateWayResponse response = service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getTransactionStatus()).isEqualTo("SUCCESSFUL");
        verify(jdbcTemplate).update(contains("provider_endpoint_runs"), any(MapSqlParameterSource.class));
    }

    @Test
    void sandboxCollectionFailsForMagicDeclinedAccountSuffix() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service = new ProviderEndpointExecutionService(jdbcTemplate, tokenStoreService);
        PaymentGatewayRequest request = new PaymentGatewayRequest(
            "M1", "256770000002", 1000.0, "REF-2", "test", "cb", Map.of());

        GateWayResponse response = service.execute("yo_payments", "Yo! Payments", "COLLECT", request);

        assertThat(response.getTransactionStatus()).isEqualTo("FAILED");
    }

    @Test
    void sandboxPayoutFailsForMagicDeclinedAccountSuffix() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service = new ProviderEndpointExecutionService(jdbcTemplate, tokenStoreService);
        PaymentGatewayRequest request = new PaymentGatewayRequest(
            "M1", "256770000002", 500.0, "REF-3", "test", "cb", Map.of());

        GateWayResponse response = service.execute("yo_payments", "Yo! Payments", "PAYOUT", request);

        assertThat(response.getTransactionStatus()).isEqualTo("FAILED");
    }

    @Test
    void refusesToFallBackToSandboxWhenProductionEndpointIsMissing() {
        // A misconfigured production channel must fail loudly rather than silently resolve
        // real payments through the canned sandbox scenarios.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ProviderTokenStoreService tokenStoreService = mock(ProviderTokenStoreService.class);
        ProviderEndpointExecutionService service = new ProviderEndpointExecutionService(jdbcTemplate, tokenStoreService);
        PaymentGatewayRequest request = new PaymentGatewayRequest(
            "M1", "256770000001", 1000.0, "REF-4", "test", "cb", Map.of("gatewayState", "PRODUCTION"));

        assertThatThrownBy(() -> service.execute("yo_payments", "Yo! Payments", "COLLECT", request))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Provider endpoint URL is required in production mode");
    }
}
