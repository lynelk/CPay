package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Covers tenant override, global fallback and event-time price resolution. */
class PriceResolverTest {
    private static final Instant AS_OF = Instant.parse("2026-08-15T10:00:00Z");

    @Test
    void resolveReturnsTheTenantSpecificVersionEffectiveAtBusinessTime() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion tenantVersion = version(1L, 7L);
        when(repository.findVersionsAt(
                        7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of(tenantVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);

        assertThat(result).contains(tenantVersion);
        verify(repository, never())
                .findVersionsAt(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);
    }

    @Test
    void resolveFallsBackToTheGlobalVersionAtTheSameBusinessTime() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion globalVersion = version(2L, null);
        when(repository.findVersionsAt(
                        7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of());
        when(repository.findVersionsAt(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of(globalVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);

        assertThat(result).contains(globalVersion);
    }

    @Test
    void resolveGoesStraightToGlobalWhenBillingTenantIdIsNull() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion globalVersion = version(3L, null);
        when(repository.findVersionsAt(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of(globalVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);

        assertThat(result).contains(globalVersion);
    }

    @Test
    void resolveReturnsEmptyWhenNeitherExists() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        when(repository.findVersionsAt(
                        7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of());
        when(repository.findVersionsAt(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF))
                .thenReturn(List.of());

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE", AS_OF);

        assertThat(result).isEmpty();
    }

    private PriceBookVersion version(long id, Long billingTenantId) {
        return new PriceBookVersion(
                id,
                billingTenantId,
                "PAYMENT",
                "payment_event_count",
                "CUSTOMER_CHARGE",
                "UGX",
                1,
                AS_OF.minusSeconds(60),
                null);
    }
}
