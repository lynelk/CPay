package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class LegacySessionAuthorizationFilterTest {
    private final LegacySessionAuthorizationFilter filter = new LegacySessionAuthorizationFilter();

    @Test
    void protectedLegacyRouteRequiresPortalSession() throws ServletException, IOException {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/merchants/getMerchants");
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
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/settings/getSettings");
        request.getSession(true).setAttribute("user", new Object());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void merchantSelfServiceWebhooksRequirePortalSession() throws ServletException, IOException {
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
    void productExperienceRouteAllowsAuthenticatedAdminApi() throws ServletException, IOException {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(
                                "admin-api",
                                null,
                                java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        try {
            MockHttpServletRequest request =
                    new MockHttpServletRequest("GET", "/api/v2/provider-incidents");
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isSameAs(request);
        } finally {
            SecurityContextHolder.clearContext();
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
}
