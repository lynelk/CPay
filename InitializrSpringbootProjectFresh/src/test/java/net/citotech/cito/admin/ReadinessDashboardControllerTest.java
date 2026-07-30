package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadinessDashboardControllerTest {

    @Test
    void summaryDelegatesToServicePlatformWideView() {
        ReadinessDashboardService service = mock(ReadinessDashboardService.class);
        Map<String, Object> expected = Map.of("checklist", List.of());
        when(service.summary()).thenReturn(expected);
        ReadinessDashboardController controller = new ReadinessDashboardController(service);

        Map<String, Object> result = controller.summary();

        assertThat(result).isSameAs(expected);
        verify(service).summary();
    }

    @Test
    void merchantSummaryDelegatesToServiceWithThePathVariableMerchantId() {
        ReadinessDashboardService service = mock(ReadinessDashboardService.class);
        Map<String, Object> expected = Map.of("merchantId", 42L, "checklist", List.of());
        when(service.merchantSummary(42L)).thenReturn(expected);
        ReadinessDashboardController controller = new ReadinessDashboardController(service);

        Map<String, Object> result = controller.merchantSummary(42L);

        assertThat(result).isSameAs(expected);
        verify(service).merchantSummary(42L);
    }
}
