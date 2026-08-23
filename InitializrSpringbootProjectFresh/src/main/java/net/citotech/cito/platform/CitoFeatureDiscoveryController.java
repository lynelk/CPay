package net.citotech.cito.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/cito")
public class CitoFeatureDiscoveryController {
    private final CitoFeatureAccessService featureAccessService;
    private final MerchantSessionContext sessionContext;

    public CitoFeatureDiscoveryController(
            CitoFeatureAccessService featureAccessService, MerchantSessionContext sessionContext) {
        this.featureAccessService = featureAccessService;
        this.sessionContext = sessionContext;
    }

    @GetMapping("/features")
    public ResponseEntity<?> features(HttpServletRequest request) {
        long merchantId = sessionContext.requireMerchantId(request);
        return ResponseEntity.ok(featureAccessService.featureDiscovery(merchantId));
    }
}