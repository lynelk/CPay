package net.citotech.cito.billing.pricing;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves the active price-book version for a tenant/service/meter/chargeType, mirroring {@code
 * fees.FeeScheduleService#currentSchedule}'s merchant-override-then-global lookup pattern: a
 * tenant-specific version wins over the global ({@code billing_tenant_id IS NULL}) one when both
 * exist.
 */
@Service
public class PriceResolver {
    private final PriceBookRepository repository;

    public PriceResolver(PriceBookRepository repository) {
        this.repository = repository;
    }

    public Optional<PriceBookVersion> resolve(
            Long billingTenantId, String serviceCode, String meterCode, String chargeType) {
        if (billingTenantId != null) {
            List<PriceBookVersion> tenantSpecific =
                    repository.findActiveVersions(
                            billingTenantId, serviceCode, meterCode, chargeType);
            if (!tenantSpecific.isEmpty()) {
                return Optional.of(tenantSpecific.get(0));
            }
        }
        List<PriceBookVersion> global =
                repository.findActiveVersions(null, serviceCode, meterCode, chargeType);
        return global.isEmpty() ? Optional.empty() : Optional.of(global.get(0));
    }
}
