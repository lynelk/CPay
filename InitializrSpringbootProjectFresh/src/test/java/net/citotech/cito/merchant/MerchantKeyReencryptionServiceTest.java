package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit E6's re-encryption sweep/on-demand path: a legacy plaintext PEM row and a row
 * encrypted under the old channel key must both be migrated to the dedicated {@code
 * cpay.key.encryption.key} envelope (version 2), while an already-migrated row is left untouched.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class MerchantKeyReencryptionServiceTest {

    private static final String SAMPLE_PEM = "-----BEGIN PRIVATE KEY-----\nMIIExamplePem==\n-----END PRIVATE KEY-----\n";

    @Test
    void upgradesALegacyPlaintextPemRowAndMarksItVersionTwo() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("key_encryption_version"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(row(invocation.getArgument(2), 7L, SAMPLE_PEM, 0)));
        when(jdbcTemplate.update(
                        contains("key_encryption_version"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        MerchantKeyReencryptionService service = service(jdbcTemplate);

        boolean upgraded = service.upgradeMerchant(7L);

        assertThat(upgraded).isTrue();
        verify(jdbcTemplate).update(contains("key_encryption_version"), any(MapSqlParameterSource.class));
    }

    @Test
    void leavesAnAlreadyMigratedRowUntouched() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("key_encryption_version"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(row(invocation.getArgument(2), 7L, "ciphertext-already-dedicated", 2)));
        MerchantKeyReencryptionService service = service(jdbcTemplate);

        boolean upgraded = service.upgradeMerchant(7L);

        assertThat(upgraded).isFalse();
        verify(jdbcTemplate, never()).update(any(String.class), any(MapSqlParameterSource.class));
    }

    @Test
    void migratesARowEncryptedUnderTheOldChannelKey() throws Exception {
        MerchantChannelCryptoService channelService = new MerchantChannelCryptoService("channel-secret");
        String channelEncrypted = channelService.encrypt(SAMPLE_PEM);

        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("key_encryption_version"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(row(invocation.getArgument(2), 9L, channelEncrypted, 1)));
        when(jdbcTemplate.update(
                        contains("key_encryption_version"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        MerchantKeyReencryptionService service = service(jdbcTemplate);

        boolean upgraded = service.upgradeMerchant(9L);

        assertThat(upgraded).isTrue();
        verify(jdbcTemplate).update(contains("key_encryption_version"), any(MapSqlParameterSource.class));
    }

    private MerchantKeyReencryptionService service(NamedParameterJdbcTemplate jdbcTemplate) {
        return new MerchantKeyReencryptionService(
                jdbcTemplate,
                new MerchantKeyEncryptionService("dedicated-test-key", "channel-fallback-key"),
                new MerchantChannelCryptoService("channel-secret"));
    }

    private Object row(RowMapper mapper, long id, String privateKey, int version) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("private_key")).thenReturn(privateKey);
        when(rs.getInt("key_encryption_version")).thenReturn(version);
        return mapper.mapRow(rs, 1);
    }
}
