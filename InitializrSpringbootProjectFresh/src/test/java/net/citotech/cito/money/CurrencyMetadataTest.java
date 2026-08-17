package net.citotech.cito.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CurrencyMetadataTest {

    @Test
    void minorUnitScaleReturnsIsoMinorUnitsForKnownCurrencies() {
        assertThat(CurrencyMetadata.minorUnitScale("UGX")).isZero();
        assertThat(CurrencyMetadata.minorUnitScale("RWF")).isZero();
        assertThat(CurrencyMetadata.minorUnitScale("KES")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("TZS")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("NGN")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("GHS")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("ZAR")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("USD")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("EUR")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("GBP")).isEqualTo(2);
    }

    @Test
    void minorUnitScaleNormalizesCaseAndWhitespace() {
        assertThat(CurrencyMetadata.minorUnitScale("ugx")).isZero();
        assertThat(CurrencyMetadata.minorUnitScale("kes")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale(" UGX ")).isZero();
    }

    @Test
    void minorUnitScaleDefaultsToTwoForUnknownOrMissingCurrencies() {
        assertThat(CurrencyMetadata.minorUnitScale("XYZ")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale("")).isEqualTo(2);
        assertThat(CurrencyMetadata.minorUnitScale(null)).isEqualTo(2);
    }

    @Test
    void normalizeReturnsUpperTrimmedCodeAndRejectsBlank() {
        assertThat(CurrencyMetadata.normalize("kes")).isEqualTo("KES");
        assertThat(CurrencyMetadata.normalize(" UGX ")).isEqualTo("UGX");
        assertThat(CurrencyMetadata.normalize(null)).isNull();
        assertThatThrownBy(() -> CurrencyMetadata.normalize("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency is required");
    }

    @Test
    void settlementScaleIsFinerThanOrEqualToEveryMinorUnit() {
        assertThat(CurrencyMetadata.SETTLEMENT_SCALE).isGreaterThanOrEqualTo(2);
        for (String currency :
                new String[] {
                    "UGX", "KES", "TZS", "RWF", "NGN", "GHS", "ZAR", "USD", "EUR", "GBP"
                }) {
            assertThat(CurrencyMetadata.SETTLEMENT_SCALE)
                    .as("settlement scale must cover display scale for %s", currency)
                    .isGreaterThanOrEqualTo(CurrencyMetadata.minorUnitScale(currency));
        }
    }
}
