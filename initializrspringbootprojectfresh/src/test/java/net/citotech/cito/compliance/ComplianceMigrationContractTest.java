package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ComplianceMigrationContractTest {

    @Test
    void auditMigrationCreatesComplianceAndProviderEvidenceTables() throws IOException {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V9__compliance_and_provider_evidence.sql"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `compliance_profiles`")
            .contains("CREATE TABLE IF NOT EXISTS `compliance_cases`")
            .contains("CREATE TABLE IF NOT EXISTS `risk_decision_scores`")
            .contains("CREATE TABLE IF NOT EXISTS `provider_certification_requirements`")
            .contains("CREATE TABLE IF NOT EXISTS `provider_certification_evidence`");
    }
}
