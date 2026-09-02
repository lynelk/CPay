package net.citotech.cito.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class ExperienceAccessContextTest {
    private final ExperienceAccessContext context = new ExperienceAccessContext();

    @Test
    void merchantSessionIsTenantScoped() {
        MerchantUser user = new MerchantUser();
        user.setMerchant_id(42L);
        user.setEmail("owner@example.com");
        user.setRole("FINANCE");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("merchantUser", user);
        request.setSession(session);

        ExperienceAccessContext.Access access = context.require(request, null);

        assertThat(access.admin()).isFalse();
        assertThat(access.merchantId()).isEqualTo(42L);
        assertThat(access.role()).isEqualTo("FINANCE");
        context.requireMerchantScope(access, 42L);
        assertThatThrownBy(() -> context.requireMerchantScope(access, 43L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void administratorMayReadAnyMerchantScope() {
        User user = new User();
        user.setId(7L);
        user.setEmail("admin@example.com");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("user", user);
        request.setSession(session);

        ExperienceAccessContext.Access access = context.require(request, null);

        assertThat(access.admin()).isTrue();
        context.requireMerchantScope(access, 999L);
    }

    @Test
    void missingPortalSessionIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> context.require(request, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
