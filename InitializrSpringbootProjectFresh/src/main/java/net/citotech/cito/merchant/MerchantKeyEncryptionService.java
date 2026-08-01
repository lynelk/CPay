package net.citotech.cito.merchant;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Audit E6: dedicated AES-GCM envelope for merchant RSA private keys at rest. The key material
 * comes from {@code cpay.key.encryption.key} (a random 256-bit secret), deliberately separate
 * from {@code merchant.channel.encryption.key} used by {@link MerchantChannelCryptoService} for
 * channel credentials - rotating/compromising a channel-credential key must never be able to touch
 * signing keys. When the dedicated variable is not configured (backward compatibility for existing
 * single-key deployments), the channel key value is used so rows encrypted before this service
 * existed remain readable; operators are expected to set {@code cpay.key.encryption.key} and run
 * the on-demand re-encryption (V31 + code) to move onto the dedicated key.
 *
 * <p>Envelope format is {@code base64(IV || ciphertext)} with a random 12-byte IV per encryption
 * and a 128-bit GCM tag, so re-encrypting the same plaintext always produces a fresh blob. The
 * {@code cpay.key.encryption.hsm} flag is the documented seam an operator can implement against
 * the same {@code encrypt}/{@code decrypt} contract when they deploy an HSM-backed vault.
 */
@Service
public class MerchantKeyEncryptionService {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec keySpec;

    public MerchantKeyEncryptionService(
            @Value("${cpay.key.encryption.key:}") String keyValue,
            @Value("${merchant.channel.encryption.key:}") String channelKeyValue) {
        this.keySpec = new SecretKeySpec(deriveKey(resolveKeyMaterial(keyValue, channelKeyValue)), "AES");
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt merchant private key", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] payload = Base64.getDecoder().decode(cipherText);
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt merchant private key", e);
        }
    }

    private String resolveKeyMaterial(String dedicated, String channelFallback) {
        if (dedicated != null && !dedicated.trim().isEmpty()) {
            return dedicated;
        }
        if (channelFallback == null || channelFallback.trim().isEmpty()) {
            throw new IllegalStateException(
                    "CPAY_KEY_ENCRYPTION_KEY (cpay.key.encryption.key) or "
                            + "MERCHANT_CHANNEL_ENCRYPTION_KEY must be set");
        }
        return channelFallback;
    }

    /**
     * A 32-byte value supplied base64-encoded (the documented format for a random 256-bit key) is
     * used as-is; any other string is hashed with SHA-256, mirroring the channel service's
     * derivation so short passphrase-style secrets still yield a full 256-bit key.
     */
    private byte[] deriveKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value.trim());
            if (decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Not base64 - fall through to SHA-256 derivation.
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to derive merchant key encryption key", e);
        }
    }
}
