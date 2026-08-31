package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BillingEffectiveDateReleaseGateTest {
    private String source(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    void priceResolutionUsesBusinessTimeNotDatabaseWallClock() throws Exception {
        String repository =
                source(
                        "src/main/java/net/citotech/cito/billing/pricing/PriceBookRepository.java");
        String engine =
                source("src/main/java/net/citotech/cito/billing/pricing/RatingEngine.java");

        assertThat(repository)
                .contains("effective_from <= :as_of")
                .contains("effective_to > :as_of")
                .contains("Timestamp.from(asOf)");
        assertThat(engine)
                .contains("priceResolver.resolve(")
                .contains("chargeType, asOf")
                .contains("formulaInputs.put(\"asOf\", asOf.toString())");
    }

    @Test
    void taxAndFxResolutionUseTheSameBusinessTime() throws Exception {
        String tax =
                source(
                        "src/main/java/net/citotech/cito/billing/tax/BillingTaxRuleResolver.java");
        String fx =
                source("src/main/java/net/citotech/cito/billing/fx/BillingFxResolver.java");

        assertThat(tax)
                .contains("effective_from<=:as_of")
                .contains("effective_to>:as_of")
                .contains("Timestamp.from(asOf)");
        assertThat(fx)
                .contains("valid_from<=:as_of")
                .contains("valid_until>:as_of")
                .contains("Timestamp.from(asOf)");
    }

    @Test
    void contractOverridesAreApprovedEffectiveDatedAndMakerCheckerControlled() throws Exception {
        String override =
                source(
                        "src/main/java/net/citotech/cito/billing/pricing/ContractPriceOverrideService.java");

        assertThat(override)
                .contains("status='APPROVED'")
                .contains("effective_from<=:as_of")
                .contains("effective_to>:as_of")
                .contains("maker and checker must be different actors")
                .contains("would overlap an existing version");
    }
}
