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

/**
 * Covers {@link PriceResolver}'s tenant-override-then-global lookup, mirroring FeeScheduleService's
 * pattern.
 */
class PriceResolverTest {

    @Test
    void resolveReturnsTheTenantSpecificVersionWhenOnePresent() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion tenantVersion = version(1L, 7L);
        when(repository.findActiveVersions(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of(tenantVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

        assertThat(result).contains(tenantVersion);
        verify(repository, never())
                .findActiveVersions(null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");
    }

    @Test
    void resolveFallsBackToTheGlobalVersionWhenNoTenantSpecificOneExists() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion globalVersion = version(2L, null);
        when(repository.findActiveVersions(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of());
        when(repository.findActiveVersions(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of(globalVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

        assertThat(result).contains(globalVersion);
    }

    @Test
    void resolveGoesStraightToGlobalWhenBillingTenantIdIsNull() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        PriceBookVersion globalVersion = version(3L, null);
        when(repository.findActiveVersions(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of(globalVersion));

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

        assertThat(result).contains(globalVersion);
    }

    @Test
    void resolveReturnsEmptyWhenNeitherExists() {
        PriceBookRepository repository = mock(PriceBookRepository.class);
        when(repository.findActiveVersions(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of());
        when(repository.findActiveVersions(
                        null, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(List.of());

        Optional<PriceBookVersion> result =
                new PriceResolver(repository)
                        .resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

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
                Instant.now(),
                null);
    }
}
