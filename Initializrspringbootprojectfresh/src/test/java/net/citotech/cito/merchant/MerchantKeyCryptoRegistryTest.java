package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers audit E6: merchant RSA private keys must be encrypted at rest, but existing plaintext
 * rows written before this landed must keep working (tolerant read).
 */
class MerchantKeyCryptoRegistryTest {

    private static final String SAMPLE_PEM = "-----BEGIN PRIVATE KEY-----\nMIIExamplePem==\n-----END PRIVATE KEY-----\n";

    @AfterEach
    void resetRegistry() {
        new MerchantKeyCryptoRegistry(new MerchantChannelCryptoService("test-merchant-key-crypto-registry-secret"));
    }

    @Test
    void encryptsForStorageAndDecryptsBackToTheOriginalPem() {
        new MerchantKeyCryptoRegistry(new MerchantChannelCryptoService("test-merchant-key-crypto-registry-secret"));

        String stored = MerchantKeyCryptoRegistry.encryptForStorage(SAMPLE_PEM);

        assertThat(stored).isNotEqualTo(SAMPLE_PEM);
        assertThat(stored).doesNotContain("BEGIN PRIVATE KEY");
        assertThat(MerchantKeyCryptoRegistry.decryptForUse(stored)).isEqualTo(SAMPLE_PEM);
    }

    @Test
    void legacyPlaintextRowsAreReturnedAsIsRatherThanFailingToDecrypt() {
        new MerchantKeyCryptoRegistry(new MerchantChannelCryptoService("test-merchant-key-crypto-registry-secret"));

        assertThat(MerchantKeyCryptoRegistry.decryptForUse(SAMPLE_PEM)).isEqualTo(SAMPLE_PEM);
    }

    @Test
    void nullAndEmptyValuesPassThroughUnchanged() {
        new MerchantKeyCryptoRegistry(new MerchantChannelCryptoService("test-merchant-key-crypto-registry-secret"));

        assertThat(MerchantKeyCryptoRegistry.encryptForStorage(null)).isNull();
        assertThat(MerchantKeyCryptoRegistry.encryptForStorage("")).isEmpty();
        assertThat(MerchantKeyCryptoRegistry.decryptForUse(null)).isNull();
        assertThat(MerchantKeyCryptoRegistry.decryptForUse("")).isEmpty();
    }
}
