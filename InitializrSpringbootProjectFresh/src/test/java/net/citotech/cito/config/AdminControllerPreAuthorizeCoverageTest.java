package net.citotech.cito.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Audit E3: guards the class-level {@code @PreAuthorize("hasRole('ADMIN')")} reinforcement added to
 * the admin-facing controllers under {@code /api/v2/admin/**} identified as the clearest
 * "admin-only sensitive action" candidates (see {@code SecurityConfig}'s {@code filterChain} bean
 * for the path-level rule this mirrors).
 *
 * <p>This is a pure reflection check (no Spring context) so it stays cheap while catching an easy
 * regression: someone later removing the annotation from one of these classes (e.g. during a
 * refactor) without realizing it was intentional defense-in-depth on top of the path matcher, not
 * dead code. {@link MethodSecurityEnforcementTest} separately proves the annotation actually
 * fires through Spring's AOP method-security proxy, not just that it is present as text.
 */
class AdminControllerPreAuthorizeCoverageTest {

    private static final List<Class<?>> ADMIN_ROLE_GUARDED_CONTROLLERS = List.of(
        net.citotech.cito.admin.AdminOpsController.class,
        net.citotech.cito.admin.AdminImpersonationController.class,
        net.citotech.cito.admin.ReadinessDashboardController.class,
        net.citotech.cito.admin.OperatingControlController.class,
        net.citotech.cito.admin.DeliveryOpsDashboardController.class,
        net.citotech.cito.admin.FeatureFlagController.class,
        net.citotech.cito.admin.SettingsRegistryController.class,
        net.citotech.cito.admin.AdminMerchantStatementController.class,
        net.citotech.cito.portal.GatewayAdminController.class,
        net.citotech.cito.callback.CallbackAdminController.class,
        net.citotech.cito.callback.CallbackOpsController.class,
        net.citotech.cito.reconciliation.ReconFinanceController.class,
        net.citotech.cito.reconciliation.SettlementOpsController.class,
        net.citotech.cito.reconciliation.ReconciliationReviewController.class,
        net.citotech.cito.reconciliation.ReconController.class,
        net.citotech.cito.reconciliation.StatementCheckController.class,
        net.citotech.cito.audit.AuditChainVerificationController.class,
        net.citotech.cito.balance.BalanceAdminController.class,
        net.citotech.cito.compliance.ComplianceReportingController.class,
        net.citotech.cito.compliance.KycController.class,
        net.citotech.cito.fees.FeeScheduleAdminController.class,
        net.citotech.cito.gateway.ProviderCertificationController.class,
        net.citotech.cito.gateway.ProviderTokenController.class,
        net.citotech.cito.gateway.SandboxRunController.class,
        net.citotech.cito.ledger.LedgerAdminController.class,
        net.citotech.cito.reporting.ReportingAdminController.class,
        net.citotech.cito.scheduler.TransactionLogArchivalController.class,
        net.citotech.cito.security.AdminMfaController.class,
        net.citotech.cito.webhook.MerchantWebhookController.class
    );

    @Test
    void everyAuditE3TargetControllerCarriesClassLevelAdminPreAuthorize() {
        for (Class<?> controllerClass : ADMIN_ROLE_GUARDED_CONTROLLERS) {
            PreAuthorize annotation = controllerClass.getAnnotation(PreAuthorize.class);
            assertThat(annotation)
                .as("expected @PreAuthorize on %s", controllerClass.getName())
                .isNotNull();
            assertThat(annotation.value())
                .as("expected hasRole('ADMIN') on %s", controllerClass.getName())
                .isEqualTo("hasRole('ADMIN')");
        }
    }
}
