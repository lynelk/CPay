package net.citotech.cito.sharedprovider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CpaySharedPaymentsMigrationTest {
    @Test
    void migrationDefaultsCollectionsButKeepsPayoutsControlled() throws Exception {
        String migration =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/V115__cpay_shared_payments_console.sql"));

        assertThat(migration)
                .contains("shared_provider_default_collection_per_transaction_limit")
                .contains("shared_provider_default_payout_enabled', 'false'")
                .contains("CASE WHEN operations.operation = 'COLLECT' THEN 'ACTIVE' ELSE 'PENDING' END")
                .contains("m.account_number NOT LIKE 'CITO-%'");
    }

    @Test
    void migrationNeverInventsProviderBalanceOrOpeningFloat() throws Exception {
        String migration =
                Files.readString(
                        Path.of(
                                "src/main/resources/db/migration/V115__cpay_shared_payments_console.sql"));

        assertThat(migration)
                .contains("provider_reported_balance")
                .doesNotContain("SET book_balance")
                .doesNotContain("provider_reported_balance = 0")
                .doesNotContain("provider_reported_balance, 0");
    }
}
