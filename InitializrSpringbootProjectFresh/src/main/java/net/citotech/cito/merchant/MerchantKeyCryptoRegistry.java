package net.citotech.cito.merchant;

import org.springframework.stereotype.Component;

/**
 * Static bridge so legacy static-utility code ({@code Common}, not a Spring bean) can encrypt/
 * decrypt merchant secrets at rest, using the same key material as {@link
 * MerchantChannelCryptoService} (audit E6). Covers merchant RSA private keys and the legacy
 * per-merchant {@code hmac_secret} (an alternative to RSA for signing outgoing legacy callbacks,
 * read in {@code Common}'s merchant row-mappers and used in {@code Common#legacyCallbackSignature}
 * - there is no current write path for it in application code, so any manual population should
 * store the result of {@link #encryptForStorage(String)} rather than a raw value). Follows the same
 * static-registry pattern as {@code ProviderTokenStoreRegistry}.
 *
 * <p>Storage is tolerant of legacy plaintext rows written before this was added: a value that still
 * looks like a raw PEM block (starts with {@code -----BEGIN}) is returned as-is on read. Any other
 * value that fails to decrypt (e.g. a legacy plaintext {@code hmac_secret}, which isn't PEM-shaped)
 * falls back to the raw stored value rather than throwing. New writes are always encrypted.
 */
@Component
public class MerchantKeyCryptoRegistry {
    private static final String PEM_PREFIX = "-----BEGIN";

    private static volatile MerchantChannelCryptoService cryptoService;

    public MerchantKeyCryptoRegistry(MerchantChannelCryptoService cryptoService) {
        MerchantKeyCryptoRegistry.cryptoService = cryptoService;
    }

    public static String encryptForStorage(String plaintextPem) {
        if (plaintextPem == null || plaintextPem.isEmpty() || cryptoService == null) {
            return plaintextPem;
        }
        return cryptoService.encrypt(plaintextPem);
    }

    public static String decryptForUse(String storedValue) {
        if (storedValue == null || storedValue.isEmpty()) {
            return storedValue;
        }
        if (storedValue.startsWith(PEM_PREFIX)) {
            // Legacy plaintext row, written before encryption-at-rest was added.
            return storedValue;
        }
        if (cryptoService == null) {
            return storedValue;
        }
        try {
            return cryptoService.decrypt(storedValue);
        } catch (Exception ex) {
            // Not a value this service encrypted (or key mismatch) - fall back to the raw stored
            // value rather than breaking every read (e.g. legacy/unencrypted key material).
            return storedValue;
        }
    }
}
