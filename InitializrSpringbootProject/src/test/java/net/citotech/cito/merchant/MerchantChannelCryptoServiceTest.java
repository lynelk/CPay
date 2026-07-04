package net.citotech.cito.merchant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class MerchantChannelCryptoServiceTest {
    @Test
    void encryptsAndDecryptsChannelSetupPayload() {
        MerchantChannelCryptoService service = new MerchantChannelCryptoService("test-key");
        String plain = "{\"apiUser\":\"user\",\"apiKey\":\"value\"}";
        String encrypted = service.encrypt(plain);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, service.decrypt(encrypted));
    }
}
