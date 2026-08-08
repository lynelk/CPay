package net.citotech.cito.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class DoubleEntryLedgerServiceTest {

    @Test
    void rejectsUnbalancedTransactionsBeforeWriting() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        assertThatThrownBy(
                        () ->
                                service.post(
                                        "TX-1",
                                        "PAYMENT",
                                        "PAY-1",
                                        "unbalanced",
                                        List.of(
                                                entry("merchant:cash", "DR", "1000"),
                                                entry("provider:float", "CR", "999"))))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not balanced");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsInvalidEntryDirectionBeforeWriting() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        assertThatThrownBy(
                        () ->
                                service.post(
                                        "TX-2",
                                        "PAYMENT",
                                        "PAY-2",
                                        "bad direction",
                                        List.of(
                                                entry("merchant:cash", "DEBIT", "1000"),
                                                entry("provider:float", "CR", "1000"))))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("DR or CR");

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void repeatedTransactionReferenceReturnsExistingLedgerTransaction() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(55L));
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        long transactionId =
                service.post(
                        "TX-EXISTS",
                        "PAYMENT",
                        "PAY-EXISTS",
                        "idempotent replay",
                        List.of(
                                entry("merchant:cash", "DR", "1000"),
                                entry("provider:float", "CR", "1000")));

        assertThat(transactionId).isEqualTo(55L);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void reverseRejectsBlankReferences() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        assertThatThrownBy(() -> service.reverse("", "TX-NEW", "correction"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("requires an original and a new transaction reference");
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reverseReturnsTheExistingReversalWithoutPostingAgain() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(77L));
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        long txId = service.reverse("TX-ORIGINAL", "TX-ALREADY-REVERSED", "correction");

        assertThat(txId).isEqualTo(77L);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reverseThrowsWhenTheOriginalTransactionIsNotFound() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of());
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        assertThatThrownBy(() -> service.reverse("TX-MISSING", "TX-NEW", "correction"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Original ledger transaction not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void reverseThrowsWhenTheOriginalTransactionHasNoEntries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        // First two lookups (new-reference, then original-reference) succeed differently: no
        // reversal yet, but the original transaction id resolves - then the entries query for
        // that id comes back empty.
        when(jdbcTemplate.query(
                        anyString(),
                        any(MapSqlParameterSource.class),
                        any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(10L))
                .thenReturn(List.of());
        DoubleEntryLedgerService service = new DoubleEntryLedgerService(jdbcTemplate);

        assertThatThrownBy(() -> service.reverse("TX-EMPTY", "TX-NEW", "correction"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("has no entries");
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
                "test");
    }
}
