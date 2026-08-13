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

    @Test
    void productionMaturityMigrationVersionsArePresent() throws IOException {
        List<String> migrations = Files.list(MIGRATION_DIR)
            .map(path -> path.getFileName().toString())
            .filter(name -> name.matches("V5[4-8]__.*\\.sql"))
            .sorted()
            .toList();

        assertThat(migrations).containsExactly(
            "V54__compliance_kyb_kyc_foundation.sql",
            "V55__regional_cross_border_foundation.sql",
            "V56__finance_operations_foundation.sql",
            "V57__product_polish_developer_experience_foundation.sql",
            "V58__production_maturity_execution_automation.sql"
        );
    }

    @Test
    void productionMaturityMigrationsDefineRequiredWorkflowTables() throws IOException {
        String joinedSql = String.join("\n", List.of(
            readMigration("V54__compliance_kyb_kyc_foundation.sql"),
            readMigration("V55__regional_cross_border_foundation.sql"),
            readMigration("V56__finance_operations_foundation.sql"),
            readMigration("V57__product_polish_developer_experience_foundation.sql"),
            readMigration("V58__production_maturity_execution_automation.sql")
        ));

        assertThat(joinedSql)
            .contains("merchant_kyb_profiles")
            .contains("compliance_cases")
            .contains("corridors")
            .contains("cross_border_transfers")
            .contains("finance_settlement_batches")
            .contains("reconciliation_exceptions")
            .contains("merchant_onboarding_workflows")
            .contains("developer_portal_apps")
            .contains("screening_provider_configs")
            .contains("cross_border_payout_rail_dispatches")
            .contains("settlement_posting_runs")
            .contains("production_maturity_validation_runs");
    }

    @Test
    void migrationVersionsRemainUnique() throws IOException {
        Pattern versionPattern = Pattern.compile("^V(\\d+)__.*\\.sql$");
        List<String> versions = Files.list(MIGRATION_DIR)
            .map(path -> path.getFileName().toString())
            .map(versionPattern::matcher)
            .filter(Matcher::matches)
            .map(matcher -> matcher.group(1))
            .toList();

        assertThat(versions).doesNotHaveDuplicates();
    }

    @Test
    void productionMaturityControllersExposeExpectedRouteRoots() {
        assertRoute(ComplianceKybKycController.class, "/api/v2/compliance");
        assertRoute(CrossBorderController.class, "/api/v2/cross-border");
        assertRoute(FinanceOperationsController.class, "/api/v2/finance");
        assertRoute(ProductDeveloperExperienceController.class, "/api/v2/product");
        assertRoute(ProductionMaturityAutomationController.class, "/api/v2/production-maturity");
    }

    @Test
    void openApiContractsMentionMergedProductionMaturitySurfaces() throws IOException {
        String p1p4 = Files.readString(API_DIR.resolve("cpay-v2-p1-p4-openapi-addendum.yaml"));
        String p2p3 = Files.readString(API_DIR.resolve("cpay-v2-p2-p3-openapi-addendum.yaml"));
        String merged = Files.readString(API_DIR.resolve("cpay-v2-openapi-production-maturity-merged.yaml"));

        assertThat(p1p4).contains("/api/v2/finance/settlement-batches");
        assertThat(p2p3).contains("/api/v2/cross-border/transfers");
        assertThat(merged)
            .contains("/api/v2/production-maturity/screening/requests")
            .contains("/api/v2/production-maturity/cross-border/transfers/{transferId}/dispatch")
            .contains("/api/v2/production-maturity/settlements/finance/{settlementBatchId}/post")
            .contains("/api/v2/product/onboarding/progress");
    }

    private static String readMigration(String name) throws IOException {
        return Files.readString(MIGRATION_DIR.resolve(name));
    }

    private static void assertRoute(Class<?> controllerType, String expectedPath) {
        RequestMapping requestMapping = controllerType.getAnnotation(RequestMapping.class);
        assertThat(requestMapping).as(controllerType.getSimpleName() + " has @RequestMapping").isNotNull();
        assertThat(requestMapping.path()).contains(expectedPath);
    }
}
