package net.citotech.cito.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Covers audit K1: Testcontainers-based DB integration testing. Everything else covering {@link
 * DoubleEntryLedgerService} ({@code DoubleEntryLedgerServiceTest}) mocks {@code
 * NamedParameterJdbcTemplate}, which proves the service's own logic but never proves the SQL it
 * emits is actually correct against a real MySQL schema (correct column names, working {@code ON
 * DUPLICATE KEY UPDATE} semantics, a real unique constraint enforcing idempotency). This runs the
 * full Flyway migration history (V1..current head) against a real MySQL 8 container and exercises
 * the ledger's core documented invariant (see {@code Docs/Testing-strategy.md}: "balanced ledger
 * debits/credits") plus its idempotent-post guarantee end to end.
 *
 * <p>Requires a running Docker daemon, so it is tagged {@code "docker"} and excluded from the
 * default {@code mvn test}/{@code mvn verify} run (see the {@code docker.tests.excludedGroups}
 * property and surefire {@code excludedGroups} binding in {@code pom.xml}). Run explicitly in a
 * Docker-capable environment with: {@code mvn test -Ddocker.tests.excludedGroups=}
 */
@Tag("docker")
@Testcontainers
class DoubleEntryLedgerServiceTestcontainersTest {

    @Container
    private static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.0.36")
                    .withDatabaseName("cpay_test")
                    .withUsername("cpay")
                    .withPassword("cpay");

    private static DataSource dataSource;

    @BeforeAll
    static void migrateSchema() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(MYSQL.getJdbcUrl());
        config.setUsername(MYSQL.getUsername());
        config.setPassword(MYSQL.getPassword());
        dataSource = new HikariDataSource(config);

        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }

    @Test
    void postsBalancedEntriesAndTheyAreVisibleInTheRealSchema() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        long txId =
                service.post(
                        "TX-K1-REAL-DB-1",
                        "PAYMENT",
                        "PAY-K1-1",
                        "testcontainers real-db post",
                        List.of(
                                entry("merchant:1001:UGX:collections_payable", "CR", "5000"),
                                entry("provider:mtn_momo:UGX:float", "DR", "5000")));

        assertThat(txId).isPositive();

        List<Map<String, Object>> entries =
                jdbcTemplate
                        .getJdbcTemplate()
                        .queryForList(
                                "SELECT entry_direction, amount FROM ledger_entries WHERE ledger_transaction_id = "
                                        + txId
                                        + " ORDER BY entry_direction");
        assertThat(entries).hasSize(2);

        BigDecimal debitTotal =
                entries.stream()
                        .filter(row -> "DR".equals(row.get("entry_direction")))
                        .map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal creditTotal =
                entries.stream()
                        .filter(row -> "CR".equals(row.get("entry_direction")))
                        .map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(debitTotal).isEqualByComparingTo(creditTotal);
    }

    @Test
    void repostingTheSameTransactionReferenceDoesNotDuplicateEntriesInTheRealSchema() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);
        List<LedgerEntryCommand> entries =
                List.of(
                        entry("merchant:1002:UGX:collections_payable", "CR", "1000"),
                        entry("provider:mtn_momo:UGX:float", "DR", "1000"));

        long firstPost =
                service.post("TX-K1-IDEMPOTENT-1", "PAYMENT", "PAY-K1-2", "first attempt", entries);
        long secondPost =
                service.post(
                        "TX-K1-IDEMPOTENT-1", "PAYMENT", "PAY-K1-2", "retried delivery", entries);

        assertThat(secondPost).isEqualTo(firstPost);
        Integer entryCount =
                jdbcTemplate
                        .getJdbcTemplate()
                        .queryForObject(
                                "SELECT COUNT(*) FROM ledger_entries WHERE ledger_transaction_id = "
                                        + firstPost,
                                Integer.class);
        assertThat(entryCount).isEqualTo(2);
    }

    @Test
    void reversePostsFlippedEntriesAndTheWholeSetStaysBalanced() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);
        List<LedgerEntryCommand> entries =
                List.of(
                        entry("merchant:1003:UGX:collections_payable", "CR", "2500"),
                        entry("provider:mtn_momo:UGX:float", "DR", "2500"));

        long originalTxId =
                service.post(
                        "TX-K1-REVERSAL-ORIGINAL",
                        "PAYMENT",
                        "PAY-K1-3",
                        "original posting",
                        entries);
        long reversalTxId =
                service.reverse(
                        "TX-K1-REVERSAL-ORIGINAL", "TX-K1-REVERSAL-NEW", "correcting error");

        assertThat(reversalTxId).isNotEqualTo(originalTxId);

        List<Map<String, Object>> reversalEntries =
                jdbcTemplate
                        .getJdbcTemplate()
                        .queryForList(
                                "SELECT entry_direction, amount FROM ledger_entries WHERE ledger_transaction_id = "
                                        + reversalTxId
                                        + " ORDER BY entry_direction");
        assertThat(reversalEntries).hasSize(2);
        // The reversal flips each original entry's direction: the original CR merchant entry
        // becomes a DR reversal entry, and vice versa for the provider entry.
        BigDecimal reversalDebitTotal =
                reversalEntries.stream()
                        .filter(row -> "DR".equals(row.get("entry_direction")))
                        .map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reversalCreditTotal =
                reversalEntries.stream()
                        .filter(row -> "CR".equals(row.get("entry_direction")))
                        .map(row -> (BigDecimal) row.get("amount"))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(reversalDebitTotal).isEqualByComparingTo("2500");
        assertThat(reversalCreditTotal).isEqualByComparingTo("2500");

        // Reversing is idempotent, same as post().
        long reversalReplay =
                service.reverse(
                        "TX-K1-REVERSAL-ORIGINAL", "TX-K1-REVERSAL-NEW", "retried delivery");
        assertThat(reversalReplay).isEqualTo(reversalTxId);
        Integer reversalEntryCount =
                jdbcTemplate
                        .getJdbcTemplate()
                        .queryForObject(
                                "SELECT COUNT(*) FROM ledger_entries WHERE ledger_transaction_id = "
                                        + reversalTxId,
                                Integer.class);
        assertThat(reversalEntryCount).isEqualTo(2);
    }

    @Test
    void postThrowsWhenARealLockRowCoversTodayForTheCurrency() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);
        jdbcTemplate
                .getJdbcTemplate()
                .update(
                        "INSERT INTO ledger_period_locks (currency, period_start, period_end, locked_by, reason) "
                                + "VALUES ('KES', CURRENT_DATE, CURRENT_DATE, 'finance-ops', 'testcontainers lock test')");

        assertThatThrownBy(
                        () ->
                                service.post(
                                        "TX-K1-LOCKED",
                                        "PAYMENT",
                                        "PAY-K1-LOCKED",
                                        "should be rejected",
                                        List.of(
                                                entry(
                                                        "merchant:1004:KES:collections_payable",
                                                        "CR",
                                                        "500"),
                                                entry("provider:mtn_momo:KES:float", "DR", "500"))))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("KES")
                .hasMessageContaining("locked");

        Integer transactionCount =
                jdbcTemplate
                        .getJdbcTemplate()
                        .queryForObject(
                                "SELECT COUNT(*) FROM ledger_transactions WHERE transaction_reference = 'TX-K1-LOCKED'",
                                Integer.class);
        assertThat(transactionCount).isZero();
    }

    @Test
    void postStillSucceedsForAnUnrelatedCurrencyWhileAnotherCurrencyIsLocked() {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);
        // Reuses the KES lock inserted by the test above (same class-level dataSource); UGX has
        // no lock row at all, proving the fail-open default holds per-currency, not globally.
        long txId =
                service.post(
                        "TX-K1-UNLOCKED-CURRENCY",
                        "PAYMENT",
                        "PAY-K1-UNLOCKED-CURRENCY",
                        "different currency, should succeed",
                        List.of(
                                entry("merchant:1005:UGX:collections_payable", "CR", "750"),
                                entry("provider:mtn_momo:UGX:float", "DR", "750")));

        assertThat(txId).isPositive();
    }

    private LedgerEntryCommand entry(String account, String direction, String amount) {
        return new LedgerEntryCommand(
                account,
                account,
                "CONTROL",
                "SYSTEM",
                null,
                direction,
                new BigDecimal(amount),
                "UGX",
                "testcontainers K1 test");
    }
}
