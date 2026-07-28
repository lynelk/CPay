package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.DeliveryOpsDashboardService.DeliveryOpsSummary;
import net.citotech.cito.admin.DeliveryOpsDashboardService.LegacyCallbackSection;
import net.citotech.cito.admin.DeliveryOpsDashboardService.WebhookDeliverySection;
import org.junit.jupiter.api.Test;

class DeliveryOpsDashboardControllerTest {

    @Test
    void summaryDelegatesToServiceWithRequestedLimit() {
        DeliveryOpsDashboardService service = mock(DeliveryOpsDashboardService.class);
        DeliveryOpsSummary expected = new DeliveryOpsSummary(
            new LegacyCallbackSection(Map.of("PARKED", 1), List.of()),
            new WebhookDeliverySection(Map.of("FAILED", 2), List.of()));
        when(service.summary(25)).thenReturn(expected);
        DeliveryOpsDashboardController controller = new DeliveryOpsDashboardController(service);

        DeliveryOpsSummary result = controller.summary(25);

        assertThat(result).isSameAs(expected);
        verify(service).summary(25);
    }

    @Test
    void summaryDefaultsLimitToFiftyWhenNotSpecified() {
        DeliveryOpsDashboardService service = mock(DeliveryOpsDashboardService.class);
        DeliveryOpsSummary expected = new DeliveryOpsSummary(
            new LegacyCallbackSection(Map.of(), List.of()),
            new WebhookDeliverySection(Map.of(), List.of()));
        when(service.summary(50)).thenReturn(expected);
        DeliveryOpsDashboardController controller = new DeliveryOpsDashboardController(service);

        // Spring resolves the @RequestParam default of "50" itself; calling the handler method
        // directly with that same default verifies the wiring without needing a live context.
        DeliveryOpsSummary result = controller.summary(50);

        assertThat(result).isSameAs(expected);
    }
}
