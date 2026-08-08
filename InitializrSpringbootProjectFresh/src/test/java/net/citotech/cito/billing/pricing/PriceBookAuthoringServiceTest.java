package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import net.citotech.cito.billing.pricing.PriceBookAuthoringService.ComponentDraft;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link PriceBookAuthoringService#publish}'s "close, don't delete" sequencing and input
 * validation.
 */
class PriceBookAuthoringServiceTest {

    @Test
    void publishClosesTheOpenVersionThenInsertsTheNewVersionAndComponentsInOrder() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        when(repository.nextVersionNo(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(2);
        when(repository.insertVersion(
                        eq(7L),
                        eq("PAYMENT"),
                        eq("payment_event_count"),
                        eq("CUSTOMER_CHARGE"),
                        eq("UGX"),
                        eq(2),
                        any(Instant.class),
                        eq("ops@cpay")))
                .thenReturn(100L);

        List<ComponentDraft> components =
                List.of(
                        new ComponentDraft("FLAT", new BigDecimal("50"), null, null),
                        new ComponentDraft("PERCENTAGE", null, new BigDecimal("0.02"), null));

        PriceBookVersion result =
                new PriceBookAuthoringService(repository)
                        .publish(
                                7L,
                                "PAYMENT",
                                "payment_event_count",
                                "CUSTOMER_CHARGE",
                                "UGX",
                                components,
                                null,
                                "ops@cpay");

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.versionNo()).isEqualTo(2);
        verify(repository)
                .closeOpenVersions(
                        eq(7L),
                        eq("PAYMENT"),
                        eq("payment_event_count"),
                        eq("CUSTOMER_CHARGE"),
                        any(Instant.class));
        verify(repository)
                .insertComponent(
                        eq(100L), eq("FLAT"), eq(1), eq(new BigDecimal("50")), isNull(), isNull());
        verify(repository)
                .insertComponent(
                        eq(100L),
                        eq("PERCENTAGE"),
                        eq(2),
                        isNull(),
                        eq(new BigDecimal("0.02")),
                        isNull());
    }

    @Test
    void publishRejectsAnEmptyComponentList() {
        PriceBookRepository repository = mock(PriceBookRepository.class);

        assertThatThrownBy(
                        () ->
                                new PriceBookAuthoringService(repository)
                                        .publish(
                                                7L,
                                                "PAYMENT",
                                                "payment_event_count",
                                                "CUSTOMER_CHARGE",
                                                "UGX",
                                                List.of(),
                                                null,
                                                "ops@cpay"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void publishRejectsAnUnknownComponentType() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        List<ComponentDraft> components =
                List.of(new ComponentDraft("SOMETHING_ELSE", BigDecimal.TEN, null, null));

        assertThatThrownBy(
                        () ->
                                new PriceBookAuthoringService(repository)
                                        .publish(
                                                7L,
                                                "PAYMENT",
                                                "payment_event_count",
                                                "CUSTOMER_CHARGE",
                                                "UGX",
                                                components,
                                                null,
                                                "ops@cpay"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void publishRejectsAPercentageComponentWithoutARate() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        List<ComponentDraft> components =
                List.of(new ComponentDraft("PERCENTAGE", null, null, null));

        assertThatThrownBy(
                        () ->
                                new PriceBookAuthoringService(repository)
                                        .publish(
                                                7L,
                                                "PAYMENT",
                                                "payment_event_count",
                                                "CUSTOMER_CHARGE",
                                                "UGX",
                                                components,
                                                null,
                                                "ops@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("percentageRate");
    }

    @Test
    void publishRejectsATierComponentWithoutATierDefinition() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        List<ComponentDraft> components = List.of(new ComponentDraft("TIER", null, null, null));

        assertThatThrownBy(
                        () ->
                                new PriceBookAuthoringService(repository)
                                        .publish(
                                                7L,
                                                "PAYMENT",
                                                "payment_event_count",
                                                "CUSTOMER_CHARGE",
                                                "UGX",
                                                components,
                                                null,
                                                "ops@cpay"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("tierDefinitionJson");
    }

    @Test
    void publishRejectsAnInvalidChargeType() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        List<ComponentDraft> components =
                List.of(new ComponentDraft("FLAT", BigDecimal.TEN, null, null));

        assertThatThrownBy(
                        () ->
                                new PriceBookAuthoringService(repository)
                                        .publish(
                                                7L,
                                                "PAYMENT",
                                                "payment_event_count",
                                                "NOT_A_REAL_CHARGE_TYPE",
                                                "UGX",
                                                components,
                                                null,
                                                "ops@cpay"))
                .isInstanceOf(PaymentGatewayException.class);
    }
}
