package net.citotech.cito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the complete migration history applies to a pristine externally supplied MySQL
 * schema and leaves the database-level audit protections in place.
 */
class FlywayMigrationSmokeTest {

    @Test
    void appliesAllMigrationsToCleanMysqlSchema() throws SQLException {
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

        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals("86", latestSuccessfulVersion(connection));
            assertEquals(4, auditProtectionTriggerCount(connection));
        }
    }

    private static String latestSuccessfulVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT version FROM flyway_schema_history "
                                        + "WHERE success = 1 AND version IS NOT NULL "
                                        + "ORDER BY installed_rank DESC LIMIT 1");
                ResultSet resultSet = statement.executeQuery()) {
            assertTrue(resultSet.next(), "Flyway history must contain a successful migration");
            return resultSet.getString(1);
        }
    }

    private static int auditProtectionTriggerCount(Connection connection) throws SQLException {
        try (PreparedStatement statement =
                        connection.prepareStatement(
                                "SELECT COUNT(*) FROM information_schema.triggers "
                                        + "WHERE trigger_schema = DATABASE() "
                                        + "AND trigger_name IN (?, ?, ?, ?)");) {
            statement.setString(1, "audit_trail_no_update");
            statement.setString(2, "audit_trail_no_delete");
            statement.setString(3, "merchants_audit_trail_no_update");
            statement.setString(4, "merchants_audit_trail_no_delete");
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next(), "Trigger count query must return a row");
                return resultSet.getInt(1);
            }
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for this integration test");
        }
        return value;
    }
}
