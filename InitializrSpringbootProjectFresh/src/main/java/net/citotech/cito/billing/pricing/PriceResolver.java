package net.citotech.cito.billing.pricing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Resolves an effective price-book version for a tenant/service/meter/chargeType. Tenant-specific
 * pricing wins over the global default. Rating paths must supply the event/business time; the
 * compatibility overload resolves at the current instant for admin/current-price reads.
 */
@Service
public class PriceResolver {
    private final PriceBookRepository repository;

    public PriceResolver(PriceBookRepository repository) {
        this.repository = repository;
    }

    public Optional<PriceBookVersion> resolve(
            Long billingTenantId, String serviceCode, String meterCode, String chargeType) {
        return resolve(billingTenantId, serviceCode, meterCode, chargeType, Instant.now());
    }

    public Optional<PriceBookVersion> resolve(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException(
                    "asOf is required for effective-dated price resolution");
        }
        if (billingTenantId != null) {
            List<PriceBookVersion> tenantSpecific =
                    repository.findVersionsAt(
                            billingTenantId, serviceCode, meterCode, chargeType, asOf);
            if (!tenantSpecific.isEmpty()) {
                return Optional.of(tenantSpecific.get(0));
            }
        }
        List<PriceBookVersion> global =
                repository.findVersionsAt(null, serviceCode, meterCode, chargeType, asOf);
        return global.isEmpty() ? Optional.empty() : Optional.of(global.get(0));
    }
}
