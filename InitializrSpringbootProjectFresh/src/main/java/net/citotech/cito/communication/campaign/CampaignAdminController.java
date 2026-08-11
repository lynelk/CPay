package net.citotech.cito.communication.campaign;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin surface for campaign batches (V52, track B4, ISO domain mapping: communication/campaign):
 * create a DRAFT campaign with a recipient list, queue it for the sweep, list campaigns per
 * merchant, and trigger an on-demand sweep of the queued items (mirroring the SMS {@code
 * deliver-due} admin trigger).
 */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/campaigns")
@PreAuthorize("hasRole('ADMIN')")
public class CampaignAdminController {

    private final CampaignService campaignService;

    public CampaignAdminController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody CreateCampaignRequest request) {
        long campaignId =
                campaignService.create(
                        request.merchantId(),
                        request.name(),
                        request.channel(),
                        request.templateKey(),
                        request.recipients(),
                        request.createdBy());
        return Map.of("code", "000", "campaignId", campaignId);
    }

    @PostMapping(path = "/{campaignId}/queue")
    public Map<String, Object> queue(@PathVariable long campaignId) {
        int queued = campaignService.queue(campaignId);
        return Map.of("code", "000", "queued", queued);
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam long merchantId,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        List<Map<String, Object>> campaigns = campaignService.list(merchantId, limit);
        return Map.of("code", "000", "campaigns", campaigns);
    }

    @PostMapping(path = "/sweep-due")
    public Map<String, Object> sweepDue(
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        int processed = campaignService.sweepDue(limit);
        return Map.of("code", "000", "processed", processed);
    }

    public record CreateCampaignRequest(
            long merchantId,
            String name,
            String channel,
            String templateKey,
            List<String> recipients,
            String createdBy) {}
}
