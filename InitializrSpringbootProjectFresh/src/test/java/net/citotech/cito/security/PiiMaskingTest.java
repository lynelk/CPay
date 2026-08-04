package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiMaskingTest {

    @Test
    void masksMsisdnKeepingOnlyFirstFourAndLastFourDigits() {
        assertThat(PiiMasking.maskMsdn("256770000001")).isEqualTo("2567***0001");
    }

    @Test
    void maskMsdnShortValuesBecomeAsterisks() {
        assertThat(PiiMasking.maskMsdn("12345")).isEqualTo("***");
        assertThat(PiiMasking.maskMsdn(null)).isNull();
        assertThat(PiiMasking.maskMsdn("")).isEmpty();
    }

    @Test
    void masksEmailKeepingFirstCharacterAndDomain() {
        assertThat(PiiMasking.maskEmail("jane@example.com")).isEqualTo("j***@example.com");
    }

    @Test
    void masksGenericValuesToFirstTwoCharacters() {
        assertThat(PiiMasking.maskGeneric("A1B2C3")).isEqualTo("A1***");
        assertThat(PiiMasking.maskGeneric("ab")).isEqualTo("***");
    }

    @Test
    void masksMerchantNamesInListSurfaces() {
        assertThat(PiiMasking.maskName("Acme Ltd")).isEqualTo("Ac*** L.");
        assertThat(PiiMasking.maskName("Solo")).isEqualTo("So***");
    }
}
