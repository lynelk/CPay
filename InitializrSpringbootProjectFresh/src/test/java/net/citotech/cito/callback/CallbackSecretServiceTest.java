package net.citotech.cito.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit E13: {@code merchant_callback_secrets.secret_value} moved from plaintext to
 * encrypted-at-rest, tolerant of legacy plaintext rows written before this change; and audit E12:
 * visibility into merchants still relying on the shared fallback secret.
 */
class CallbackSecretServiceTest {
    private static final String FALLBACK = "fallback-secret";

    private MerchantChannelCryptoService realCrypto() {
        return new MerchantChannelCryptoService("test-encryption-key-0123456789");
    }

    @Test
    void rotateStoresTheSecretEncryptedNotInPlaintext() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        CallbackSecretService service =
                new CallbackSecretService(jdbcTemplate, realCrypto(), FALLBACK);

        String plaintextSecret = service.rotate(1L, "default");

        org.mockito.ArgumentCaptor<MapSqlParameterSource> captor =
                org.mockito.ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains(
                                "INSERT INTO merchant_callback_secrets"),
                        captor.capture());
        Object storedValue = captor.getValue().getValue("secret");
        assertThat(storedValue).isNotEqualTo(plaintextSecret);
        assertThat(realCrypto().decrypt((String) storedValue)).isEqualTo(plaintextSecret);
    }

    @Test
    void activeSecretDecryptsAnEncryptedRow() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService crypto = realCrypto();
        String encrypted = crypto.encrypt("the-real-secret");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(encrypted);
        CallbackSecretService service = new CallbackSecretService(jdbcTemplate, crypto, FALLBACK);

        assertThat(service.activeSecret(1L)).isEqualTo("the-real-secret");
    }

    @Test
    void activeSecretPassesThroughALegacyPlaintextRowUnchanged() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn("legacy-plaintext-secret-value");
        CallbackSecretService service =
                new CallbackSecretService(jdbcTemplate, realCrypto(), FALLBACK);

        assertThat(service.activeSecret(1L)).isEqualTo("legacy-plaintext-secret-value");
    }

    @Test
    void activeSecretFallsBackWhenNoActiveRowExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenThrow(new org.springframework.dao.EmptyResultDataAccessException(1));
        CallbackSecretService service =
                new CallbackSecretService(jdbcTemplate, realCrypto(), FALLBACK);

        assertThat(service.activeSecret(1L)).isEqualTo(FALLBACK);
    }

    @Test
    void countLegacyPlaintextSecretsCountsOnlyValuesThatFailToDecrypt() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService crypto = realCrypto();
        when(jdbcTemplate.queryForList(
                        anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(
                        List.of(
                                crypto.encrypt("secret-1"),
                                "legacy-plaintext-1",
                                "legacy-plaintext-2"));
        CallbackSecretService service = new CallbackSecretService(jdbcTemplate, crypto, FALLBACK);

        assertThat(service.countLegacyPlaintextSecrets()).isEqualTo(2);
    }

    @Test
    void countMerchantsOnFallbackSecretDelegatesToACountQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(4);
        CallbackSecretService service =
                new CallbackSecretService(jdbcTemplate, realCrypto(), FALLBACK);

        assertThat(service.countMerchantsOnFallbackSecret()).isEqualTo(4);
    }
}
