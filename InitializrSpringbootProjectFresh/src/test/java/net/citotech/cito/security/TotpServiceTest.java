package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    @Test
    void verifiesCurrentTotpCodeAndRejectsInvalidCodes() {
        TotpService service = new TotpService();
        String secret = service.generateSecret();
        long counter = Instant.now().getEpochSecond() / 30;
        String code = service.generateCode(secret, counter);

        assertThat(service.verify(secret, code)).isTrue();
        assertThat(service.verify(secret, "000000")).isFalse();
    }
}
