package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReconServiceTest {

    @Test
    void importStatementParsesRoutesToTheRightProviderParserAndTriggersAutoMatch() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());
        String csv = "provider_reference,amount,currency\nPR-1,1000,UGX\nPR-2,2000,UGX\n";
        when(repository.createImport(eq("MTN"), eq("mtn_momo"), anyString(), anyString(), eq(2))).thenReturn(42L);

        long importId = service.importStatement("MTN", "statement.csv", "ops-user",
            csv.getBytes(StandardCharsets.UTF_8));

        assertThat(importId).isEqualTo(42L);
        verify(repository, times(2)).insertStatementRow(eq(42L), any(StatementRow.class));
        verify(repository).autoMatchByMerchantReference();
    }

    @Test
    void importStatementRejectsAnUnknownProvider() {
        ReconciliationRepository repository = mock(ReconciliationRepository.class);
        ReconService service = new ReconService(repository, new ProviderStatementParserRegistry());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.importStatement("NOT_REAL", "statement.csv", "ops-user", new byte[0]))
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
}
