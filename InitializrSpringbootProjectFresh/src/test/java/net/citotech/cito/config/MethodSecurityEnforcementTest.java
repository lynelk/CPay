package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.DeliveryOpsDashboardController;
import net.citotech.cito.admin.DeliveryOpsDashboardService;
import net.citotech.cito.admin.DeliveryOpsDashboardService.DeliveryOpsSummary;
import net.citotech.cito.admin.DeliveryOpsDashboardService.LegacyCallbackSection;
import net.citotech.cito.admin.DeliveryOpsDashboardService.WebhookDeliverySection;
import net.citotech.cito.callback.CallbackAdminController;
import net.citotech.cito.callback.CallbackAdminService;
import net.citotech.cito.reconciliation.ReconciliationReviewController;
import net.citotech.cito.reconciliation.ReconciliationReviewService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Audit E3: proves the class-level {@code @PreAuthorize("hasRole('ADMIN')")} added to the
 * admin-facing controllers actually fires through Spring's AOP method-security proxy, not just
 * that the annotation is present as text (see {@link AdminControllerPreAuthorizeCoverageTest} for
 * the cheaper reflection-based coverage check across every annotated controller).
 *
 * <p>Deliberately does not use {@code @SpringBootTest}: this repo has no full-application-context
 * test today (it would require a live MySQL database - see root {@code CLAUDE.md}), so this test
 * boots a minimal, standalone {@code @EnableMethodSecurity} context containing only the controller
 * beans under test and their mocked service dependencies. This exercises the exact same
 * {@code @EnableMethodSecurity} mechanism enabled on {@link SecurityConfig}, just without pulling
 * in the full {@code SecurityFilterChain}/CORS/CSRF/DB wiring that class also configures.
 *
 * <p>Covers three representative controllers spanning the three packages targeted by audit E3
 * (admin/, callback/, reconciliation/) rather than every annotated controller, since the
 * authorization mechanism being tested is identical for all of them - {@link
 * AdminControllerPreAuthorizeCoverageTest} guarantees the annotation itself is present everywhere
 * intended.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MethodSecurityEnforcementTest.TestConfig.class)
class MethodSecurityEnforcementTest {

    @Autowired
    private DeliveryOpsDashboardController deliveryOpsDashboardController;

    @Autowired
    private CallbackAdminController callbackAdminController;

    @Autowired
    private ReconciliationReviewController reconciliationReviewController;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminIsAllowedToReadDeliveryOpsDashboard() {
        DeliveryOpsSummary result = deliveryOpsDashboardController.summary(10);

        assertThat(result).isNotNull();
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void nonAdminRoleIsDeniedOnDeliveryOpsDashboard() {
        assertThatThrownBy(() -> deliveryOpsDashboardController.summary(10))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerIsDeniedOnDeliveryOpsDashboard() {
        assertThatThrownBy(() -> deliveryOpsDashboardController.summary(10))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminIsAllowedToRotateCallbackSecret() {
        String result = callbackAdminController.rotate(1L, "default");

        assertThat(result).isEqualTo("rotated");
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void nonAdminRoleIsDeniedOnCallbackSecretRotation() {
        assertThatThrownBy(() -> callbackAdminController.rotate(1L, "default"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerIsDeniedOnCallbackSecretRotation() {
        assertThatThrownBy(() -> callbackAdminController.rotate(1L, "default"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminIsAllowedToApproveReconciliationReview() {
        reconciliationReviewController.approve(1L, "admin", "looks good");
        // No exception means the pre-authorization check let the call through to the mock service.
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    void nonAdminRoleIsDeniedOnReconciliationReviewApproval() {
        assertThatThrownBy(() -> reconciliationReviewController.approve(1L, "merchant", "note"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithAnonymousUser
    void anonymousCallerIsDeniedOnReconciliationReviewApproval() {
        assertThatThrownBy(() -> reconciliationReviewController.approve(1L, "anon", "note"))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        DeliveryOpsDashboardService deliveryOpsDashboardService() {
            DeliveryOpsDashboardService service = mock(DeliveryOpsDashboardService.class);
            when(service.summary(10)).thenReturn(new DeliveryOpsSummary(
                new LegacyCallbackSection(Map.of(), List.of()),
                new WebhookDeliverySection(Map.of(), List.of())));
            return service;
        }

        @Bean
        DeliveryOpsDashboardController deliveryOpsDashboardController(DeliveryOpsDashboardService service) {
            return new DeliveryOpsDashboardController(service);
        }

        @Bean
        CallbackAdminService callbackAdminService() {
            CallbackAdminService service = mock(CallbackAdminService.class);
            when(service.rotateSecret(1L, "default")).thenReturn("rotated");
            return service;
        }

        @Bean
        CallbackAdminController callbackAdminController(CallbackAdminService service) {
            return new CallbackAdminController(service);
        }

        @Bean
        ReconciliationReviewService reconciliationReviewService() {
            return mock(ReconciliationReviewService.class);
        }

        @Bean
        ReconciliationReviewController reconciliationReviewController(ReconciliationReviewService service) {
            return new ReconciliationReviewController(service);
        }
    }
}
