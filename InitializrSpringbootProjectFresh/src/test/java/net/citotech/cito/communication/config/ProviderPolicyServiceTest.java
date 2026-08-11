package net.citotech.cito.communication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;
import net.citotech.cito.communication.config.ProviderPolicyService.PolicyRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the V54 provider policies (ISO/IEC 27001 A.8.6): the effective policy falls back to the
 * seeded LEGACY_SETTINGS defaults when a provider has no row (never unbounded), and invalid input
 * is rejected.
 */
class ProviderPolicyServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private ProviderPolicyService service;

    private Function<String, List<PolicyRow>> policyLookup = sql -> List.of();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (sql.contains("communication_provider_policies")) {
                                return policyLookup.apply(sql);
                            }
                            return List.of();
                        });
        service = new ProviderPolicyService(jdbcTemplate);
    }

    @Test
    void providerWithoutPolicyInheritsLegacyDefaults() {
        policyLookup = sql -> List.of();

        PolicyRow policy = service.policyFor("WABA_CLOUD_API");

        assertThat(policy.providerCode()).isEqualTo("LEGACY_SETTINGS");
        assertThat(policy.maxPerMinute()).isEqualTo(60);
        assertThat(policy.maxPerHour()).isEqualTo(1000);
        assertThat(policy.connectTimeoutMs()).isEqualTo(10000);
        assertThat(policy.readTimeoutMs()).isEqualTo(30000);
        assertThat(policy.rateLimitEnabled()).isTrue();
    }

    @Test
    void explicitPolicyWins() {
        policyLookup =
                sql ->
                        List.of(
                                new PolicyRow(
                                        "YO_SMS", 80, 2000, 5000, 15000, true, true, null, null));

        PolicyRow policy = service.policyFor("yo_sms");

        assertThat(policy.providerCode()).isEqualTo("YO_SMS");
        assertThat(policy.maxPerMinute()).isEqualTo(80);
    }

    @Test
    void saveRejectsBlankProviderCode() {
        assertThatThrownBy(() -> service.save("  ", 60, 1000, null, null, true))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("providerCode is required");
    }
}
