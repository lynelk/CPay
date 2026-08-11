package net.citotech.cito.communication.campaign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.campaign.CampaignAdminController.CreateCampaignRequest;
import org.junit.jupiter.api.Test;

/** Covers {@link CampaignAdminController}'s create/queue/list/sweep surfaces. */
class CampaignAdminControllerTest {

    @Test
    void createDelegatesToTheServiceAndReturnsTheCampaignId() {
        CampaignService service = mock(CampaignService.class);
        when(service.create(
                        any(Long.class),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(List.class),
                        anyString()))
                .thenReturn(42L);

        Map<String, Object> body =
                new CampaignAdminController(service)
                        .create(
                                new CreateCampaignRequest(
                                        7L,
                                        "Receipt blast",
                                        "SMS",
                                        "merchant_sms_payment_receipt",
                                        List.of("256700000001", "256700000002"),
                                        "admin@cpay"));

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(body.get("campaignId")).isEqualTo(42L);
        verify(service)
                .create(
                        7L,
                        "Receipt blast",
                        "SMS",
                        "merchant_sms_payment_receipt",
                        List.of("256700000001", "256700000002"),
                        "admin@cpay");
    }

    @Test
    void queueDelegatesById() {
        CampaignService service = mock(CampaignService.class);
        when(service.queue(5L)).thenReturn(1);

        Map<String, Object> body = new CampaignAdminController(service).queue(5L);

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(body.get("queued")).isEqualTo(1);
        verify(service).queue(5L);
    }

    @Test
    void listReturnsTheServiceRows() {
        CampaignService service = mock(CampaignService.class);
        when(service.list(7L, 50))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "id", 1L,
                                        "name", "Blast",
                                        "status", "DRAFT")));

        Map<String, Object> body = new CampaignAdminController(service).list(7L, 50);

        assertThat(body.get("code")).isEqualTo("000");
        assertThat((List<?>) body.get("campaigns")).hasSize(1);
        verify(service).list(7L, 50);
    }

    @Test
    void sweepDueDelegatesAndReturnsTheProcessedCount() {
        CampaignService service = mock(CampaignService.class);
        when(service.sweepDue(200)).thenReturn(17);

        Map<String, Object> body = new CampaignAdminController(service).sweepDue(200);

        assertThat(body.get("code")).isEqualTo("000");
        assertThat(body.get("processed")).isEqualTo(17);
        verify(service).sweepDue(200);
    }
}
