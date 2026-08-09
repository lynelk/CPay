package net.citotech.cito.vending;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.stereotype.Service;

/**
 * Normalises customer MSISDNs and stores a tenant-scoped hash plus an encrypted recoverable value.
 * The hash prevents one tenant from correlating another tenant's customers; ciphertext is required
 * only because a later refund/payout must be able to address the original wallet.
 */
@Service
public class VendingCustomerIdentityService {
    private final MerchantChannelCryptoService cryptoService;

    public VendingCustomerIdentityService(MerchantChannelCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public CustomerIdentity protect(long merchantId, String rawMsisdn) {
        if (merchantId <= 0) throw new PaymentGatewayException("merchantId is required");
        String normalized = normalize(rawMsisdn);
        return new CustomerIdentity(
                hash(merchantId + ":" + normalized), mask(normalized), cryptoService.encrypt(normalized));
    }

    public String reveal(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw new PaymentGatewayException("Encrypted customer identifier is missing");
        }
        return cryptoService.decrypt(cipherText);
    }

    public String normalize(String value) {
        if (value == null) throw new PaymentGatewayException("Customer mobile number is required");
        String normalized = value.trim().replaceAll("[\\s()+-]", "");
        if (!normalized.matches("[0-9]{8,15}")) {
            throw new PaymentGatewayException("Customer mobile number must contain 8 to 15 digits");
        }
        return normalized;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash vending customer identifier", e);
        }
    }

    private String mask(String value) {
        if (value.length() <= 6) return "***";
        return value.substring(0, 3) + "***" + value.substring(value.length() - 3);
    }

    public record CustomerIdentity(String hash, String mask, String cipherText) {}
}
