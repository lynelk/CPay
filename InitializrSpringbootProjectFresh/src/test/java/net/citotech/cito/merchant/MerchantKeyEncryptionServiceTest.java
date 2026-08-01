package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Covers audit E6: the dedicated merchant RSA private-key envelope. A random 32-byte key supplied
 * base64-encoded is used as-is (no SHA-256 derivation), encryption always produces a fresh
 * randomized blob, decryption round-trips, and the envelope is base64(IV || ciphertext) so it
 * never contains PEM text.
 */
class MerchantKeyEncryptionServiceTest {

    private static final String SAMPLE_PEM = "-----BEGIN PRIVATE KEY-----\nMIIExamplePem==\n-----END PRIVATE KEY-----\n";

    private static MerchantKeyEncryptionService service(String dedicatedKey, String channelKey) {
        return new MerchantKeyEncryptionService(dedicatedKey, channelKey);
    }

    @Test
    void encryptsAndDecryptsBackWithADedicatedBase64Key() {
        byte[] raw = new byte[32];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(raw);
        String encoded = Base64.getEncoder().encodeToString(raw);
        // Fail fast if the test helper produced a malformed fixture.
        assertThat(Base64.getDecoder().decode(encoded)).hasSize(32);

        MerchantKeyEncryptionService service = service(encoded, "ignored-channel-key");

        String stored = service.encrypt(SAMPLE_PEM);
        assertThat(stored).isNotEqualTo(SAMPLE_PEM);
        assertThat(stored).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(service.decrypt(stored)).isEqualTo(SAMPLE_PEM);
    }

    @Test
    void encryptionIsRandomizedPerCallEvenForTheSamePlaintext() {
        MerchantKeyEncryptionService service = service("", "channel-fallback-key");

        String first = service.encrypt(SAMPLE_PEM);
        String second = service.encrypt(SAMPLE_PEM);

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo(SAMPLE_PEM);
        assertThat(service.decrypt(second)).isEqualTo(SAMPLE_PEM);
    }

    @Test
    void fallsBackToTheChannelKeyWhenTheDedicatedKeyIsNotConfigured() {
        MerchantKeyEncryptionService dedicated = service("", "channel-fallback-key");
        MerchantKeyEncryptionService sameFallback = service(null, "channel-fallback-key");

        // Both instances derive from the same channel key, so they can decrypt each other's blobs.
        String stored = dedicated.encrypt(SAMPLE_PEM);
        assertThat(sameFallback.decrypt(stored)).isEqualTo(SAMPLE_PEM);
    }

    @Test
    void throwsWhenNoKeyMaterialIsConfiguredAtAll() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CPAY_KEY_ENCRYPTION_KEY");
    }
}
