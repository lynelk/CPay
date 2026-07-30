package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProviderStatementParserRegistryTest {

    private final ProviderStatementParserRegistry registry = new ProviderStatementParserRegistry();

    @Test
    void resolvesEachRegisteredProviderToItsOwnDedicatedParserClass() {
        assertThat(registry.get("MTN")).isInstanceOf(MtnStatementParser.class);
        assertThat(registry.get("AIRTEL")).isInstanceOf(AirtelMoneyStatementParser.class);
        assertThat(registry.get("AIRTEL_OPENAPI")).isInstanceOf(AirtelOpenApiStatementParser.class);
        assertThat(registry.get("SAFARICOM")).isInstanceOf(SafaricomStatementParser.class);
        assertThat(registry.get("YO_PAYMENTS")).isInstanceOf(YoPaymentsStatementParser.class);
    }

    @Test
    void lookupIsCaseInsensitive() {
        assertThat(registry.get("mtn")).isInstanceOf(MtnStatementParser.class);
    }

    @Test
    void rejectsAnUnknownProviderCode() {
        assertThatThrownBy(() -> registry.get("NOT_A_REAL_PROVIDER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NOT_A_REAL_PROVIDER");
    }

    @Test
    void rejectsANullProviderCode() {
        assertThatThrownBy(() -> registry.get(null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
