package net.citotech.cito.developer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import net.citotech.cito.platform.CitoEntitlementService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DeveloperScopeEntitlementServiceTest {

    @Test
    void requiresOwningProductEntitlementsForRequestedScopes() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        CitoEntitlementService entitlementService = mock(CitoEntitlementService.class);
        DeveloperScopeEntitlementService service =
                new DeveloperScopeEntitlementService(jdbcTemplate, entitlementService);

        service.requireScopes(
                7L,
                List.of("REFUNDS_WRITE", "MARKETPLACE_READ", "ANALYTICS_READ"),
                "PRODUCTION");

        verify(entitlementService).requireEntitlement(7L, "REFUND_OPERATIONS", "PRODUCTION");
        verify(entitlementService)
                .requireEntitlement(7L, "MARKETPLACE_PAYMENTS", "PRODUCTION");
        verify(entitlementService).requireEntitlement(7L, "MERCHANT_ANALYTICS", "PRODUCTION");
    }
}
