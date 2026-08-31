package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers deterministic component-folding and effective-dated resolution. */
class RatingEngineTest {
    private static final Instant AS_OF = Instant.parse("2026-08-15T10:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rateReturnsEmptyWhenNoPriceBookResolvesAtEventTime() {
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
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
                                AS_OF);

        assertThat(result).isEmpty();
        verify(priceResolver)
                .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);
    }

    @Test
    void ratePercentageComponentOnly() {
        RatingEngine engine = engineWithComponents(List.of(percentage(1, "0.03")));
        assertThat(rate(engine, "1000").orElseThrow().ratedAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void rateFlatPlusPercentageAddsBothContributions() {
        RatingEngine engine = engineWithComponents(List.of(flat(1, "100"), percentage(2, "0.02")));
        assertThat(rate(engine, "1000").orElseThrow().ratedAmount()).isEqualByComparingTo("120.00");
    }

    @Test
    void rateAppliesAMinimumFloorWhenTheComputedChargeIsTooLow() {
        RatingEngine engine =
                engineWithComponents(List.of(percentage(1, "0.001"), minimum(2, "50")));
        assertThat(rate(engine, "1000").orElseThrow().ratedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void rateAppliesAMaximumCapWhenTheComputedChargeIsTooHigh() {
        RatingEngine engine =
                engineWithComponents(List.of(percentage(1, "0.5"), maximum(2, "100")));
        assertThat(rate(engine, "1000").orElseThrow().ratedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void rateTierComponentUsesGraduatedTierMath() {
        String tierDefinition = "[{\"upTo\":10000,\"rate\":0.02},{\"upTo\":null,\"rate\":0.01}]";
        RatedCharge result =
                rate(engineWithComponents(List.of(tier(1, tierDefinition))), "15000").orElseThrow();
        assertThat(result.ratedAmount()).isEqualByComparingTo("250.00");
        assertThat(result.tierPathJson()).contains("bandFrom");
    }

    @Test
    void formulaInputsRetainBusinessTimeAndRatingDimensions() {
        RatedCharge result =
                rate(engineWithComponents(List.of(flat(1, "10"))), "1000").orElseThrow();
        assertThat(result.formulaInputsJson())
                .contains(AS_OF.toString())
                .contains("CUSTOMER_CHARGE")
                .contains("payment_event_count")
                .contains("UGX");
    }

    @Test
    void currencyMismatchFailsClosed() {
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion usd =
                new PriceBookVersion(
                        1L,
                        7L,
                        "PAYMENT",
                        "payment_event_count",
                        "CUSTOMER_CHARGE",
                        "USD",
                        1,
                        AS_OF.minusSeconds(60),
                        null);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(Optional.of(usd));

        RatingEngine engine = new RatingEngine(priceResolver, repository, objectMapper);
        assertThatThrownBy(
                        () ->
                                engine.rate(
                                        7L,
                                        "PAYMENT",
                                        "payment_event_count",
                                        "CUSTOMER_CHARGE",
                                        new BigDecimal("1000"),
                                        "UGX",
                                        AS_OF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("currency");
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
                AS_OF);
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
                        AS_OF.minusSeconds(60),
                        null);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
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
