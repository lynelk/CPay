package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers audit O1: Safaricom M-Pesa statements commonly use "Receipt No.", "Completion Time", and
 * "Paid In" rather than the generic aliases the other providers share (see
 * {@link SafaricomStatementParser}'s javadoc) - this proves those extra aliases are actually wired
 * up, on top of (not instead of) the shared generic ones.
 */
class SafaricomStatementParserTest {

    private final ProviderStatementParser parser = new SafaricomStatementParser();

    @Test
    void parsesTheMpesaStyleColumnNames() {
        String csv = "Receipt No.,Completion Time,Paid In,currency\nQA12345,2026-07-01 10:00,1500,KES\n";

        List<StatementRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "statement.csv");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).providerCode).isEqualTo("SAFARICOM");
        assertThat(rows.get(0).channelCode).isEqualTo("safaricom_mpesa");
        assertThat(rows.get(0).providerReference).isEqualTo("QA12345");
        assertThat(rows.get(0).transactionDate).isEqualTo("2026-07-01 10:00");
        assertThat(rows.get(0).amount).isEqualByComparingTo(new BigDecimal("1500"));
    }

    @Test
    void stillAcceptsTheSharedGenericColumnNames() {
        String csv = "provider_reference,amount,currency\nPR-1,1000,KES\n";

        List<StatementRow> rows = parser.parse(csv.getBytes(StandardCharsets.UTF_8), "statement.csv");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).providerReference).isEqualTo("PR-1");
    }
}
