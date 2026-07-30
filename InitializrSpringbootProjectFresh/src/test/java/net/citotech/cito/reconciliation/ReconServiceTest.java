package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconServiceTest {

    @Test
    void importStatementParsesRoutesToTheRightProviderParserAndTriggersAutoMatch() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        String csv = "provider_reference,amount,currency\nPR-1,1000,UGX\nPR-2,2000,UGX\n";
        when(repository.createImport(eq("MTN"), eq("mtn_momo"), anyString(), anyString(), eq(2)))
                .thenReturn(42L);

        long importId =
                service.importStatement(
                        "MTN", "statement.csv", "ops-user", csv.getBytes(StandardCharsets.UTF_8));

        assertThat(importId).isEqualTo(42L);
        verify(repository, times(2)).insertStatementRow(eq(42L), any(StatementRow.class));
        verify(repository).autoMatchByMerchantReference();
    }

    @Test
    void importStatementRejectsAnUnknownProvider() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () ->
                                service.importStatement(
                                        "NOT_REAL", "statement.csv", "ops-user", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unmatchedDelegatesToTheRepository() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        List<ReconciliationRecord> expected = List.of(new ReconciliationRecord());
        when(repository.findUnmatched(anyInt())).thenReturn(expected);

        assertThat(service.unmatched(50)).isSameAs(expected);
    }

    @Test
    void approveMatchDefaultsTheReasonWhenNoneIsGiven() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());

        service.approveMatch(7L, "TX-1", null);

        verify(repository).markOperatorMatch(7L, "TX-1", "operator-approved");
    }

    @Test
    void candidateTransactionsDelegatesToTheRepositoryWhenAFilterIsGiven() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        List<CandidateTransaction> expected = List.of(new CandidateTransaction());
        when(repository.findCandidateTransactions(
                        eq("PR-1"),
                        eq(new BigDecimal("100")),
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(25)))
                .thenReturn(expected);

        List<CandidateTransaction> result =
                service.candidateTransactions("PR-1", new BigDecimal("100"), null, null, null, 25);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void candidateTransactionsReturnsNoRowsWithoutAnySearchFilter() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());

        List<CandidateTransaction> result =
                service.candidateTransactions(null, null, null, null, null, 25);

        assertThat(result).isEmpty();
        verify(repository, never())
                .findCandidateTransactions(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void candidateTransactionsClampsAnOutOfRangeLimit() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        when(repository.findCandidateTransactions(
                        eq("PR-1"), isNull(), isNull(), isNull(), isNull(), eq(100)))
                .thenReturn(List.of());

        service.candidateTransactions("PR-1", null, null, null, null, 500);

        verify(repository)
                .findCandidateTransactions(
                        eq("PR-1"), isNull(), isNull(), isNull(), isNull(), eq(100));
    }

    @Test
    void candidateTransactionsAcceptsADateRangeAsASearchFilter() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 30);
        when(repository.findCandidateTransactions(
                        isNull(), isNull(), isNull(), eq(from), eq(to), eq(25)))
                .thenReturn(List.of());

        service.candidateTransactions(null, null, null, from, to, 25);

        verify(repository)
                .findCandidateTransactions(isNull(), isNull(), isNull(), eq(from), eq(to), eq(25));
    }
}
