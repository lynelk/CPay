package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit B4: phone-prefix routing moves from hardcoded arrays to this DB-backed,
 * in-memory-cached lookup.
 */
class ChannelRoutingServiceTest {

    @Test
    void matchesAConfiguredPrefixForItsGateway() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class))).thenReturn(List.of(
            Map.of("gateway_id", "MTNMoMoPaymentGateway", "msisdn_prefix", "25677"),
            Map.of("gateway_id", "MTNMoMoPaymentGateway", "msisdn_prefix", "25678"),
            Map.of("gateway_id", "SafariComPaymentGateway", "msisdn_prefix", "25470")));

        ChannelRoutingService service = new ChannelRoutingService(jdbcTemplate);

        assertThat(service.matches("MTNMoMoPaymentGateway", "256770000000")).isTrue();
        assertThat(service.matches("MTNMoMoPaymentGateway", "256750000000")).isFalse();
        assertThat(service.matches("SafariComPaymentGateway", "254700000000")).isTrue();
        assertThat(service.prefixesFor("AirtelMoneyPaymentGateway")).isEmpty();
    }

    @Test
    void registryReturnsNullWhenNoPrefixesAreConfiguredSoCallersFallBackToTheHardcodedArray() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class))).thenReturn(List.of());
        ChannelRoutingService service = new ChannelRoutingService(jdbcTemplate);
        new ChannelRoutingRegistry(service);

        assertThat(ChannelRoutingRegistry.matchesConfiguredPrefix("MTNMoMoPaymentGateway", "256770000000")).isNull();
    }

    @Test
    void registryDelegatesToTheServiceWhenPrefixesAreConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class), any(MapSqlParameterSource.class))).thenReturn(List.of(
            Map.of("gateway_id", "MTNMoMoPaymentGateway", "msisdn_prefix", "25677")));
        ChannelRoutingService service = new ChannelRoutingService(jdbcTemplate);
        new ChannelRoutingRegistry(service);

        assertThat(ChannelRoutingRegistry.matchesConfiguredPrefix("MTNMoMoPaymentGateway", "256770000000")).isTrue();
        assertThat(ChannelRoutingRegistry.matchesConfiguredPrefix("MTNMoMoPaymentGateway", "256990000000")).isFalse();
    }
}
