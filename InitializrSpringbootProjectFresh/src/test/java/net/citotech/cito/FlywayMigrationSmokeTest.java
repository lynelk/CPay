package net.citotech.cito;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the complete migration history applies to a pristine externally supplied MySQL
 * schema.
 */
class FlywayMigrationSmokeTest {

    @Test
    void appliesAllMigrationsToCleanMysqlSchema() {
        String url = System.getenv("DB_URL");
        Assumptions.assumeTrue(
                url != null && url.startsWith("jdbc:mysql:"),
                "Clean migration smoke test requires the dedicated MySQL integration environment");

        String username = requireEnvironment("DB_USERNAME");
        String password = requireEnvironment("DB_PASSWORD");

        MigrateResult result =
                Flyway.configure()
                        .dataSource(url, username, password)
                        .locations("classpath:db/migration")
                        .baselineOnMigrate(false)
                        .load()
                        .migrate();

        assertTrue(result.success, "Flyway migration must succeed");
        assertTrue(result.migrationsExecuted > 0, "A clean schema must execute migrations");
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for this integration test");
        }
        return value;
    }
}
