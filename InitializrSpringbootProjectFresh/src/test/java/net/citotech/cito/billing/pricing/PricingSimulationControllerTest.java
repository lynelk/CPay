package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PricingSimulationControllerTest {

    @Test
    void validShadowWindowDelegatesToComparisonService() {
        ChargeShadowComparisonService service =
                org.mockito.Mockito.mock(ChargeShadowComparisonService.class);
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-01T00:00:00Z");
        ChargeShadowComparisonResult expected =
                new ChargeShadowComparisonResult(start, end, 0, 0, List.of());
        when(service.compare(start, end)).thenReturn(expected);
        PricingSimulationController controller = new PricingSimulationController(service);

        var response =
                controller.shadow(
                        new PricingSimulationController.ShadowSimulationRequest(start, end));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
        verify(service).compare(start, end);
    }

    @Test
    void invalidWindowIsRejectedWithoutSimulation() {
        ChargeShadowComparisonService service =
                org.mockito.Mockito.mock(ChargeShadowComparisonService.class);
        PricingSimulationController controller = new PricingSimulationController(service);
        Instant start = Instant.parse("2026-09-01T00:00:00Z");
        Instant end = Instant.parse("2026-08-01T00:00:00Z");

        var response =
                controller.shadow(
                        new PricingSimulationController.ShadowSimulationRequest(start, end));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
