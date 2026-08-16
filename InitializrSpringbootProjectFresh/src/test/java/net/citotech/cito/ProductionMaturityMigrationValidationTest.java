package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

class ProductionMaturityMigrationValidationTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");
    private static final Path API_DIR = Path.of("../Docs/Api");
    private static final List<String> PRODUCTION_MATURITY_MIGRATIONS =
            List.of(
                    "V54__compliance_kyb_kyc_foundation.sql",
                    "V55__regional_cross_border_foundation.sql",
                    "V61__finance_operations_foundation.sql",
                    "V62__product_polish_developer_experience_foundation.sql",
                    "V63__production_maturity_execution_automation.sql",
                    "V64__kyb_decision_audit_controls.sql",
                    "V65__payment_state_transition_audit.sql",
                    "V66__ledger_reservation_funds_controls.sql",
                    "V67__ledger_account_scoped_identity.sql",
                    "V68__ledger_finance_constraint_hardening.sql");

    @Test
    void productionMaturityMigrationVersionsArePresentWithoutCollidingWithExistingMigrations()
            throws IOException {
        List<String> migrations =
                Files.list(MIGRATION_DIR)
                        .map(path -> path.getFileName().toString())
                        .filter(PRODUCTION_MATURITY_MIGRATIONS::contains)
                        .sorted()
                        .toList();

        assertThat(migrations).containsExactlyElementsOf(PRODUCTION_MATURITY_MIGRATIONS);
    }

    @Test
    void productionMaturityMigrationsDefineRequiredWorkflowTables() throws IOException {
        String joinedSql =
                String.join(
                        "\n",
                        PRODUCTION_MATURITY_MIGRATIONS.stream()
                                .map(
                                        ProductionMaturityMigrationValidationTest
                                                ::readMigrationUnchecked)
                                .toList());

        assertThat(joinedSql)
                .contains("kyb_profiles")
                .contains("compliance_cases")
                .contains("corridors")
                .contains("cross_border_transfers")
                .contains("finance_settlement_batches")
                .contains("reconciliation_exceptions")
                .contains("merchant_onboarding_workflows")
                .contains("developer_portal_applications")
                .contains("screening_provider_configs")
                .contains("cross_border_payout_rail_dispatches")
                .contains("settlement_posting_runs")
                .contains("production_maturity_validation_runs")
                .contains("kyb_review_decisions")
                .contains("payment_state_transitions")
                .contains("ledger_reservation_controls")
                .contains("owner_scope_id")
                .contains("uk_ledger_account_scope");
    }

    @Test
    void productionMaturityMigrationsStayMySqlCompatible() throws IOException {
        String joinedSql =
                String.join(
                                "\n",
                                PRODUCTION_MATURITY_MIGRATIONS.stream()
                                        .map(
                                                ProductionMaturityMigrationValidationTest
                                                        ::readMigrationUnchecked)
                                        .toList())
                        .toLowerCase();

        assertThat(joinedSql)
                .doesNotContain("bigserial")
                .doesNotContain("jsonb")
                .doesNotContain("::json")
                .doesNotContain("on conflict")
                .doesNotContain("returning")
                .doesNotContain("create index if not exists")
                .doesNotContain("identity")
                .doesNotContain("generated always");
    }

    @Test
    void migrationVersionsRemainUnique() throws IOException {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__.*\\.sql$");
        List<String> versions =
                Files.list(MIGRATION_DIR)
                        .map(path -> path.getFileName().toString())
                        .map(versionPattern::matcher)
                        .filter(Matcher::matches)
                        .map(matcher -> matcher.group(1))
                        .toList();

        assertThat(versions).doesNotHaveDuplicates();
    }

    @Test
    void productionMaturityControllersExposeExpectedRouteRoots() {
        assertRoute(ComplianceKybKycController.class, "/api/v2");
        assertRoute(RegionalCrossBorderController.class, "/api/v2");
        assertRoute(FinanceOperationsController.class, "/api/v2/admin/finance-operations");
        assertRoute(ProductDeveloperExperienceController.class, "/api/v2/product-experience");
        assertRoute(ProductionMaturityAutomationController.class, "/api/v2/production-maturity");
    }

    @Test
    void openApiDoesNotDefaultKybReviewDecisionsToApproval() throws IOException {
        String mainContract = Files.readString(API_DIR.resolve("cpay-v2-openapi.yaml"));

        assertThat(mainContract)
                .doesNotContain("default: APPROVED")
                .contains("operationId: reviewBeneficialOwner")
                .contains("operationId: reviewKycDocument")
                .contains("- APPROVE")
                .contains("- REJECT")
                .contains("reviewerRole")
                .contains("reason");
    }

    @Test
    void openApiContractsMentionProductionMaturitySurfaces() throws IOException {
        String p1p4 = Files.readString(API_DIR.resolve("cpay-v2-p1-p4-openapi-addendum.yaml"));
        String p2p3 = Files.readString(API_DIR.resolve("cpay-v2-p2-p3-openapi-addendum.yaml"));
        String mergedReference =
                Files.readString(
                        API_DIR.resolve("cpay-v2-openapi-production-maturity-merged.yaml"));
        String mainContract = Files.readString(API_DIR.resolve("cpay-v2-openapi.yaml"));

        assertThat(p1p4)
                .contains("/admin/finance-operations/settlements")
                .contains("/product-experience/merchant/{merchantId}/onboarding");
        assertThat(p2p3).contains("/cross-border/transfers").contains("/admin/compliance/cases");
        assertThat(mergedReference)
                .contains("/api/v2/production-maturity/screening/requests")
                .contains("/api/v2/product/onboarding/progress");
        assertThat(mainContract)
                .contains("/api/v2/native/payments/collect")
                .contains("/api/v2/admin/finance-operations/settlements")
                .contains("/api/v2/admin/compliance/cases")
                .contains("/api/v2/cross-border/transfers")
                .contains("/api/v2/production-maturity/screening/requests")
                .contains(
                        "/api/v2/production-maturity/cross-border/transfers/{transferId}/dispatch")
                .contains(
                        "/api/v2/production-maturity/settlements/finance/{settlementBatchId}/post");
    }

    private static String readMigrationUnchecked(String name) {
        try {
            return readMigration(name);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read migration " + name, e);
        }
    }

    private static String readMigration(String name) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(name));
    }

    private static void assertRoute(Class<?> controllerType, String expectedPath) {
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertThat(requestMapping)
                .as(controllerType.getSimpleName() + " has @RequestMapping")
                .isNotNull();
        assertThat(requestMapping.path()).contains(expectedPath);
    }
}
