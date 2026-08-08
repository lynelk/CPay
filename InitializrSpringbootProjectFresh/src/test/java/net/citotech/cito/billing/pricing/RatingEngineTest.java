package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers {@link RatingEngine}'s component-folding logic against hand-computed fixtures. */
class RatingEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rateReturnsEmptyWhenNoPriceBookResolves() {
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.empty());

        Optional<RatedCharge> result =
                new RatingEngine(priceResolver, priceBookRepository, objectMapper)
                        .rate(
                                7L,
                                "PAYMENT",
                                "payment_event_count",
                                "CUSTOMER_CHARGE",
                                new BigDecimal("1000"),
                                "UGX",
                                Instant.now());

        assertThat(result).isEmpty();
    }

    @Test
    void ratePercentageComponentOnly() {
        RatingEngine engine = engineWithComponents(List.of(percentage(1, "0.03")));

        Optional<RatedCharge> result = rate(engine, "1000");

        assertThat(result).isPresent();
        assertThat(result.get().ratedAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void rateFlatPlusPercentageAddsBothContributions() {
        RatingEngine engine = engineWithComponents(List.of(flat(1, "100"), percentage(2, "0.02")));

        Optional<RatedCharge> result = rate(engine, "1000");

        assertThat(result).isPresent();
        assertThat(result.get().ratedAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void rateAppliesAMinimumFloorWhenTheComputedChargeIsTooLow() {
        RatingEngine engine =
                engineWithComponents(List.of(percentage(1, "0.001"), minimum(2, "50")));

        Optional<RatedCharge> result = rate(engine, "1000");

        // 1000 * 0.001 = 1.00, below the 50 floor.
        assertThat(result).isPresent();
        assertThat(result.get().ratedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void rateAppliesAMaximumCapWhenTheComputedChargeIsTooHigh() {
        RatingEngine engine =
                engineWithComponents(List.of(percentage(1, "0.5"), maximum(2, "100")));

        Optional<RatedCharge> result = rate(engine, "1000");

        // 1000 * 0.5 = 500, above the 100 cap.
        assertThat(result).isPresent();
        assertThat(result.get().ratedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rateTierComponentUsesGraduatedTierMath() {
        String tierDefinition = "[{\"upTo\":10000,\"rate\":0.02},{\"upTo\":null,\"rate\":0.01}]";
        RatingEngine engine = engineWithComponents(List.of(tier(1, tierDefinition)));

        Optional<RatedCharge> result = rate(engine, "15000");

        // 10000 @ 2% = 200, 5000 @ 1% = 50, total 250.
        assertThat(result).isPresent();
        assertThat(result.get().ratedAmount()).isEqualByComparingTo("250.00");
        assertThat(result.get().tierPathJson()).contains("bandFrom");
    }

    @Test
    void rateThrowsForAnUnsupportedComponentType() {
        RatingEngine engine =
                engineWithComponents(
                        List.of(new PriceComponent(1L, 1L, "SOMETHING_ELSE", 1, null, null, null)));

        assertThatThrownBy(() -> rate(engine, "1000")).isInstanceOf(IllegalStateException.class);
    }

    private Optional<RatedCharge> rate(RatingEngine engine, String baseAmount) {
        return engine.rate(
                7L,
                "PAYMENT",
                "payment_event_count",
                "CUSTOMER_CHARGE",
                new BigDecimal(baseAmount),
                "UGX",
                Instant.now());
    }

    private RatingEngine engineWithComponents(List<PriceComponent> components) {
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        PriceBookVersion version =
                new PriceBookVersion(
                        1L,
                        7L,
                        "PAYMENT",
                        "payment_event_count",
                        "CUSTOMER_CHARGE",
                        "UGX",
                        1,
                        Instant.now(),
                        null);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.of(version));
        when(priceBookRepository.findComponents(1L)).thenReturn(components);
        return new RatingEngine(priceResolver, priceBookRepository, objectMapper);
    }

    private PriceComponent flat(int sequenceNo, String amount) {
        return new PriceComponent(
                sequenceNo, 1L, "FLAT", sequenceNo, new BigDecimal(amount), null, null);
    }

    private PriceComponent percentage(int sequenceNo, String rate) {
        return new PriceComponent(
                sequenceNo, 1L, "PERCENTAGE", sequenceNo, null, new BigDecimal(rate), null);
    }

    private PriceComponent minimum(int sequenceNo, String amount) {
        return new PriceComponent(
                sequenceNo, 1L, "MINIMUM", sequenceNo, new BigDecimal(amount), null, null);
    }

    private PriceComponent maximum(int sequenceNo, String amount) {
        return new PriceComponent(
                sequenceNo, 1L, "MAXIMUM", sequenceNo, new BigDecimal(amount), null, null);
    }

    private PriceComponent tier(int sequenceNo, String tierDefinitionJson) {
        return new PriceComponent(
                sequenceNo, 1L, "TIER", sequenceNo, null, null, tierDefinitionJson);
    }
}
