package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import net.citotech.cito.Model.MerchantUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LegacySessionAuthorizationFilterTest {
    private final LegacySessionAuthorizationFilter filter = new LegacySessionAuthorizationFilter();

    @Test
    void protectedLegacyRouteRequiresPortalSession() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/merchants/getMerchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"107\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void protectedLegacyRouteAllowsAdminSession() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/settings/getSettings");
        request.getSession(true).setAttribute("user", new Object());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void merchantSelfServiceRequiresMerchantSession() throws ServletException, IOException {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v2/merchant-self-service/webhooks");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"107\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void oneFinanceSessionAllowsBillingButRejectsCommunication() throws ServletException, IOException {
        MerchantUser finance = merchantUser("FINANCE");

        MockHttpServletRequest billing =
                new MockHttpServletRequest("GET", "/api/v2/merchant-self-service/billing/usage");
        billing.getSession(true).setAttribute("merchantUser", finance);
        MockHttpServletResponse billingResponse = new MockHttpServletResponse();
        MockFilterChain billingChain = new MockFilterChain();
        filter.doFilter(billing, billingResponse, billingChain);
        assertThat(billingResponse.getStatus()).isEqualTo(200);
        assertThat(billingChain.getRequest()).isSameAs(billing);

        MockHttpServletRequest communication =
                new MockHttpServletRequest(
                        "GET", "/api/v2/merchant-self-service/communication/ussd/sessions");
        communication.getSession(true).setAttribute("merchantUser", finance);
        MockHttpServletResponse communicationResponse = new MockHttpServletResponse();
        MockFilterChain communicationChain = new MockFilterChain();
        filter.doFilter(communication, communicationResponse, communicationChain);
        assertThat(communicationResponse.getStatus()).isEqualTo(403);
        assertThat(communicationResponse.getContentAsString()).contains("\"code\":\"110\"");
        assertThat(communicationChain.getRequest()).isNull();
    }

    @Test
    void unknownRoleFallsBackToViewerReadOnlyAccess() throws ServletException, IOException {
        MerchantUser unknownRole = merchantUser(null);

        MockHttpServletRequest statements =
                new MockHttpServletRequest("GET", "/api/v2/merchant-self-service/statements");
        statements.getSession(true).setAttribute("merchantUser", unknownRole);
        MockHttpServletResponse statementResponse = new MockHttpServletResponse();
        MockFilterChain statementChain = new MockFilterChain();
        filter.doFilter(statements, statementResponse, statementChain);
        assertThat(statementResponse.getStatus()).isEqualTo(200);
        assertThat(statementChain.getRequest()).isSameAs(statements);

        for (String path :
                new String[] {
                    "/api/v2/merchant-self-service/billing/usage",
                    "/api/v2/merchant-self-service/kyc",
                    "/api/v2/merchant-self-service/communication/ussd/sessions",
                    "/api/v2/merchant-self-service/channels",
                    "/api/v2/merchant-self-service/settlement-preference",
                    "/api/v2/merchant-self-service/team"
                }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.getSession(true).setAttribute("merchantUser", unknownRole);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).as(path).isEqualTo(403);
            assertThat(chain.getRequest()).as(path).isNull();
        }
    }

    @Test
    void oneOwnerSessionCanReachEveryOwnedMerchantModule() throws ServletException, IOException {
        MerchantUser owner = merchantUser("OWNER");
        String[] paths = {
            "/api/v2/merchant-self-service/billing/usage",
            "/api/v2/merchant-self-service/kyc",
            "/api/v2/merchant-self-service/communication/ussd/sessions",
            "/api/v2/merchant-self-service/channels",
            "/api/v2/merchant-self-service/settlement-preference",
            "/api/v2/merchant-self-service/statements",
            "/api/v2/merchant-self-service/team",
            "/api/v2/merchant-self-service/access"
        };
        for (String path : paths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.getSession(true).setAttribute("merchantUser", owner);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).as(path).isEqualTo(200);
            assertThat(chain.getRequest()).as(path).isSameAs(request);
        }
    }

    @Test
    void signupAndVerificationBypassSessionGate() throws ServletException, IOException {
        for (String path :
                new String[] {
                    "/api/v2/merchant-self-service/signup",
                    "/api/v2/merchant-self-service/verify-email",
                    "/api/v2/merchant-self-service/verify-email/resend"
                }) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).as(path).isEqualTo(200);
            assertThat(chain.getRequest()).isSameAs(request);
        }
    }

    @Test
    void publicAuthRouteBypassesSessionGate() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/authenticate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void publicLoginAppearanceSettingsBypassSessionGate() throws ServletException, IOException {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/settings/public-login-appearance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private MerchantUser merchantUser(String role) {
        MerchantUser user = new MerchantUser();
        user.setMerchant_id(1L);
        user.setRole(role);
        return user;
    }
}
