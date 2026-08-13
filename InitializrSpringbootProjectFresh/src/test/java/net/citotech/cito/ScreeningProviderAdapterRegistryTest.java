package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class ScreeningProviderAdapterRegistryTest {

    @Test
    void localProviderRecordsScreeningRequestAndReturnsRequestId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScreeningProviderAdapterRegistry.ProviderConfig>>any(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(List.of());
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(91L);
        ScreeningProviderAdapterRegistry registry = new ScreeningProviderAdapterRegistry(
                jdbcTemplate,
                List.of(new LocalScreeningProviderAdapter()));

        ScreeningProviderAdapterRegistry.ScreeningResult result = registry.screen(
                new ScreeningProviderAdapterRegistry.ScreeningRequest(
                        "local", "MERCHANT", "M-100", "SANCTIONS", "{}", "tester"));

        assertThat(result.requestId()).isEqualTo(91L);
        assertThat(result.status()).isEqualTo("PENDING_PROVIDER");
        assertThat(result.riskLevel()).isEqualTo("NOT_SCREENED");
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("screening_provider_requests"),
                org.mockito.ArgumentMatchers.eq("LOCAL"),
                org.mockito.ArgumentMatchers.eq("MERCHANT"),
                org.mockito.ArgumentMatchers.eq("M-100"),
                org.mockito.ArgumentMatchers.eq("SANCTIONS"),
                org.mockito.ArgumentMatchers.eq("PENDING_PROVIDER"),
                org.mockito.ArgumentMatchers.eq("NOT_SCREENED"),
                org.mockito.ArgumentMatchers.eq(0),
                anyString(),
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.eq("tester"));
    }

    @Test
    void registerProviderUsesMySqlUpsertAndReturnsRegistrationState() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ScreeningProviderAdapterRegistry registry = new ScreeningProviderAdapterRegistry(
                jdbcTemplate,
                List.of(new LocalScreeningProviderAdapter()));

        Map<String, Object> result = registry.registerProvider(Map.of(
                "providerCode", "local",
                "displayName", "Local Screening",
                "enabled", true,
                "supportsSanctions", true));

        assertThat(result)
                .containsEntry("providerCode", "LOCAL")
                .containsEntry("status", "ENABLED")
                .containsEntry("adapterRegistered", true);
        verify(jdbcTemplate).update(org.mockito.ArgumentMatchers.contains("on duplicate key update"),
                org.mockito.ArgumentMatchers.eq("LOCAL"),
                org.mockito.ArgumentMatchers.eq("Local Screening"),
                org.mockito.ArgumentMatchers.eq("SANDBOX"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("NONE"),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(false));
    }
}
