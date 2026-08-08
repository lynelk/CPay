package net.citotech.cito.billing.tenancy;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;

/**
 * Resolves a CPay {@code merchantId} to its {@code billing_tenant_id} (audit ADR 0003). This is the
 * only place in the billing module that should translate between the two identifiers - everything
 * downstream of resolution should carry {@code billing_tenant_id}, not {@code merchant_id}, even
 * though today's mapping is always 1:1.
 */
@Service
public class BillingTenantResolver {
    private final BillingTenantRepository repository;

    public BillingTenantResolver(BillingTenantRepository repository) {
        this.repository = repository;
    }

    /**
     * @throws PaymentGatewayException if the merchant has no {@code billing_tenants} row - every
     *     merchant is backfilled a tenant by {@code V38}, so this only happens for a merchant
     *     created after that migration ran without the corresponding tenant-provisioning hook (not
     *     yet wired anywhere in this phase) or an invalid merchant id.
     */
    public long resolveTenantId(long merchantId) {
        return repository
                .findByMerchantId(merchantId)
                .map(BillingTenant::id)
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "No billing tenant is mapped for merchant " + merchantId));
    }

    public BillingTenant resolveTenant(long merchantId) {
        return repository
                .findByMerchantId(merchantId)
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "No billing tenant is mapped for merchant " + merchantId));
    }
}
