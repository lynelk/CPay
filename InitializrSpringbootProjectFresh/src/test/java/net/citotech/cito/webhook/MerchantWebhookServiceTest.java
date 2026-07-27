package net.citotech.cito.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MerchantWebhookServiceTest {

    @Test
    void registersEndpointAndReturnsPlainSecretOnce() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(cryptoService.encrypt(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        Map<String, Object> result = service.registerEndpoint(15L, "payment.pending", "https://merchant.test/webhook", "tester");

        assertThat(result.get("code")).isEqualTo("000");
        assertThat((String) result.get("secret")).hasSizeGreaterThan(40);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void enqueueCreatesDeliveryForEveryActiveEndpoint() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(99L);
                return List.of(mapper.mapRow(rs, 0));
            });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThat(service.enqueue(15L, "payment.pending", "TX-1", "{}")).isEqualTo(1);
    }
}
