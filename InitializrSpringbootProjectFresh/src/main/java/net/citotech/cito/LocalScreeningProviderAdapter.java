package net.citotech.cito;

import java.time.Instant;
import org.springframework.stereotype.Component;

/** Safe local screening adapter used for sandbox and automated tests. */
@Component
public class LocalScreeningProviderAdapter implements ScreeningProviderAdapter {
    public static final String PROVIDER_CODE = "LOCAL";

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ScreeningProviderAdapterRegistry.ScreeningResult screen(
            ScreeningProviderAdapterRegistry.ScreeningRequest request,
            ScreeningProviderAdapterRegistry.ProviderConfig providerConfig) {
        String status = providerConfig.enabled() ? "SUBMITTED" : "PENDING_PROVIDER";
        String riskLevel = providerConfig.enabled() ? "UNKNOWN" : "NOT_SCREENED";
        String externalReference = "LOCAL-SCR-" + Instant.now().toEpochMilli();
        return new ScreeningProviderAdapterRegistry.ScreeningResult(
                null, status, riskLevel, 0, externalReference);
    }
}
