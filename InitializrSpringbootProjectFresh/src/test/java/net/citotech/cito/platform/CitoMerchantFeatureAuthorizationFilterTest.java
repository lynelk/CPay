package net.citotech.cito.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CitoMerchantFeatureAuthorizationFilterTest {

    @Test
    void mapsMerchantRefundWorkspaceToProductionRefundEntitlement() throws Exception {
        CitoFeatureAccessService accessService = mock(CitoFeatureAccessService.class);
        CitoMerchantFeatureAuthorizationFilter filter =
                new CitoMerchantFeatureAuthorizationFilter(accessService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v2/merchant-self-service/refunds");
        request.addHeader("X-CPay-Environment", "PRODUCTION");
        MerchantUser user = new MerchantUser();
        user.setMerchant_id(42L);
        request.getSession(true).setAttribute("merchantUser", user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(accessService).require(42L, "REFUND_OPERATIONS", "PRODUCTION");
        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void deniesFeatureWhenEntitlementServiceRejectsAccess() throws Exception {
        CitoFeatureAccessService accessService = mock(CitoFeatureAccessService.class);
        doThrow(new PaymentGatewayException("Service is not enabled"))
                .when(accessService)
                .require(99L, "MARKETPLACE_PAYMENTS", "SANDBOX");
        CitoMerchantFeatureAuthorizationFilter filter =
                new CitoMerchantFeatureAuthorizationFilter(accessService);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v2/merchant-self-service/marketplace/subaccounts");
        MerchantUser user = new MerchantUser();
        user.setMerchant_id(99L);
        request.getSession(true).setAttribute("merchantUser", user);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CITO_SERVICE_NOT_ENTITLED");
    }
}
