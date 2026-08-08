package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.admin.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Covers {@link PriceBookAdminController}'s read/publish surfaces. */
class PriceBookAdminControllerTest {

    @Test
    void activeReturnsANotConfiguredBodyWhenNothingResolves() {
        PriceBookAuthoringService authoringService = mock(PriceBookAuthoringService.class);
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(priceResolver.resolve(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE"))
                .thenReturn(Optional.empty());

        ResponseEntity<?> response =
                new PriceBookAdminController(
                                authoringService, priceResolver, priceBookRepository, auditService)
                        .active(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

        assertThat(((Map<?, ?>) response.getBody()).get("code"))
                .isEqualTo("PRICE_BOOK_NOT_CONFIGURED");
    }

    @Test
    void activeReturnsTheResolvedVersionWithItsComponents() {
        PriceBookAuthoringService authoringService = mock(PriceBookAuthoringService.class);
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
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
        when(priceBookRepository.findComponents(1L))
                .thenReturn(
                        List.of(new PriceComponent(1L, 1L, "FLAT", 1, BigDecimal.TEN, null, null)));

        ResponseEntity<?> response =
                new PriceBookAdminController(
                                authoringService, priceResolver, priceBookRepository, auditService)
                        .active(7L, "PAYMENT", "payment_event_count", "CUSTOMER_CHARGE");

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("id")).isEqualTo(1L);
        assertThat((List<?>) body.get("components")).hasSize(1);
    }

    @Test
    void publishReturnsThePublishedVersionAndRecordsAnAuditEntry() {
        PriceBookAuthoringService authoringService = mock(PriceBookAuthoringService.class);
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        PriceBookVersion published =
                new PriceBookVersion(
                        5L,
                        7L,
                        "PAYMENT",
                        "payment_event_count",
                        "CUSTOMER_CHARGE",
                        "UGX",
                        1,
                        Instant.now(),
                        null);
        when(authoringService.publish(
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString()))
                .thenReturn(published);
        when(priceBookRepository.findComponents(5L)).thenReturn(List.of());

        Map<String, Object> body =
                Map.of(
                        "billingTenantId", 7,
                        "serviceCode", "PAYMENT",
                        "meterCode", "payment_event_count",
                        "chargeType", "CUSTOMER_CHARGE",
                        "currency", "UGX",
                        "createdBy", "ops@cpay",
                        "components", List.of(Map.of("componentType", "FLAT", "flatAmount", 50)));

        ResponseEntity<?> response =
                new PriceBookAdminController(
                                authoringService, priceResolver, priceBookRepository, auditService)
                        .publish(body);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(((Map<?, ?>) response.getBody()).get("id")).isEqualTo(5L);
        verify(auditService).record("BILLING_PRICE_BOOK", "PRICE_BOOK_PUBLISH", "5", "ops@cpay");
    }

    @Test
    void publishReturnsBadRequestWhenTheServiceRejectsTheInput() {
        PriceBookAuthoringService authoringService = mock(PriceBookAuthoringService.class);
        PriceResolver priceResolver = mock(PriceResolver.class);
        PriceBookRepository priceBookRepository = mock(PriceBookRepository.class);
        AdminAuditService auditService = mock(AdminAuditService.class);
        when(authoringService.publish(
                        any(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString()))
                .thenThrow(
                        new net.citotech.cito.gateway.PaymentGatewayException(
                                "serviceCode is required"));

        Map<String, Object> body =
                Map.of(
                        "serviceCode", "PAYMENT",
                        "meterCode", "payment_event_count",
                        "chargeType", "CUSTOMER_CHARGE",
                        "currency", "UGX",
                        "components", List.of(Map.of("componentType", "FLAT", "flatAmount", 50)));

        ResponseEntity<?> response =
                new PriceBookAdminController(
                                authoringService, priceResolver, priceBookRepository, auditService)
                        .publish(body);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }
}
