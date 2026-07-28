package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ProviderTokenStoreServiceTest {

    @Test
    void storesEncryptedTokenValue() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = new MerchantChannelCryptoService("unit-test-secret");
        ProviderTokenStoreService service = new ProviderTokenStoreService(jdbcTemplate, cryptoService);
        when(jdbcTemplate.update(contains("INSERT INTO provider_tokens"), any(MapSqlParameterSource.class)))
            .thenReturn(1);

        service.save("mtn_momo", "collect", "production", "raw-provider-token", Instant.parse("2026-12-31T00:00:00Z"));

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(contains("INSERT INTO provider_tokens"), captor.capture());
        String stored = String.valueOf(captor.getValue().getValue("token_value"));

        assertThat(stored).isNotEqualTo("raw-provider-token");
        assertThat(cryptoService.decrypt(stored)).isEqualTo("raw-provider-token");
    }
}
