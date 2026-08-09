package net.citotech.cito.communication.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.SmsDeliveryStatus;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsSendRequest;
import net.citotech.cito.communication.sms.SmsSendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Covers {@link ProviderRouter} rule-based adapter selection and its legacy fallback safety. */
class ProviderRouterTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private SmsGatewayAdapter legacyAdapter;
    private SmsGatewayAdapter yoAdapter;
    private ProviderRouter router;

    private List<String> providerCodes = List.of("LEGACY_SETTINGS");

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        legacyAdapter = mock(SmsGatewayAdapter.class);
        yoAdapter = mock(SmsGatewayAdapter.class);
        when(legacyAdapter.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.sent("legacy-trace", "legacy"));
        when(yoAdapter.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.sent("yo-trace", "yo"));

        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (sql.contains("communication_routing_rules")) {
                                return providerCodes;
                            }
                            return List.of();
                        });

        router =
                new ProviderRouter(
                        jdbcTemplate,
                        Map.of(
                                "LEGACY_SETTINGS", legacyAdapter,
                                "YO_SMS", yoAdapter));
    }

    @Test
    void platformDefaultRuleRoutesToLegacyAdapter() {
        // V50 seed: no merchant-specific rule, provider = LEGACY_SETTINGS.
        SmsSendResult result = router.send(sms(7L));

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(result.trace()).isEqualTo("legacy-trace");
        verify(legacyAdapter).send(any(SmsSendRequest.class));
        verify(yoAdapter, never()).send(any(SmsSendRequest.class));
    }

    @Test
    void merchantSpecificRuleBeatsThePlatformDefaultWhenTheJoinResolvesIt() {
        providerCodes = List.of("YO_SMS");

        SmsSendResult result = router.send(sms(7L));

        assertThat(result.trace()).isEqualTo("yo-trace");
        verify(yoAdapter).send(any(SmsSendRequest.class));
        verify(legacyAdapter, never()).send(any(SmsSendRequest.class));
    }

    @Test
    void unknownProviderCodeFallsBackToLegacyAdapter() {
        providerCodes = List.of("NOT_A_PROVIDER");

        SmsSendResult result = router.send(sms(7L));

        assertThat(result.trace()).isEqualTo("legacy-trace");
        verify(legacyAdapter).send(any(SmsSendRequest.class));
    }

    @Test
    void noResolvableRuleFallsBackToLegacyAdapter() {
        providerCodes = List.of();

        SmsSendResult result = router.send(sms(7L));

        assertThat(result.trace()).isEqualTo("legacy-trace");
        verify(legacyAdapter).send(any(SmsSendRequest.class));
    }

    @Test
    void routingLookupFailureFallsBackToLegacyInsteadOfFailingTheBatch() {
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("db down"));

        SmsSendResult result = router.send(sms(7L));

        assertThat(result.trace()).isEqualTo("legacy-trace");
        verify(legacyAdapter).send(any(SmsSendRequest.class));
    }

    @Test
    void sendForwardsTheOriginalRequestToTheResolvedAdapter() {
        providerCodes = List.of("YO_SMS");
        SmsSendRequest request = sms(7L);

        router.send(request);

        verify(yoAdapter).send(request);
    }

    @Test
    void sendThrowsFastWhenLegacyAdapterIsMissingFromTheMap() {
        ProviderRouter broken = new ProviderRouter(jdbcTemplate, Map.of("YO_SMS", yoAdapter));

        boolean threw;
        try {
            broken.send(sms(7L));
            threw = false;
        } catch (IllegalStateException expected) {
            threw = true;
        }
        assertThat(threw).isTrue();
    }

    private SmsSendRequest sms(long merchantId) {
        return new SmsSendRequest(1L, merchantId, "Hello", "256700000001", "test-gateway");
    }
}
