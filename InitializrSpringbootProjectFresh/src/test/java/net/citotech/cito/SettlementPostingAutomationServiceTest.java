package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

@SuppressWarnings({"unchecked", "rawtypes"})
class SettlementPostingAutomationServiceTest {

    @Test
    void financeSettlementPostsBalancedLedgerEntries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from finance_settlement_batches"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 10L, "settlement_reference", "SET-10", "currency_code", "UGX", "net_amount", new BigDecimal("100.00"), "status", "APPROVED")));
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("settlement_posting_runs"), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from finance_settlement_items"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 1L, "transaction_reference", "TX-1", "net_amount", new BigDecimal("100.00"))));
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(88L);
        SettlementPostingAutomationService service = new SettlementPostingAutomationService(jdbcTemplate, ledgerService);

        Map<String, Object> result = service.postFinanceSettlement(10L, "finance-user");

        assertThat(result)
                .containsEntry("postingRunId", 88L)
                .containsEntry("settlementType", "FINANCE")
                .containsEntry("status", "POSTED");
        ArgumentCaptor<List<LedgerEntryCommand>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).post(anyString(), anyString(), anyString(), anyString(), entriesCaptor.capture());
        assertBalanced(entriesCaptor.getValue());
    }

    @Test
    void corridorSettlementPostsBalancedLedgerEntries() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from corridor_settlement_batches"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 20L, "settlement_reference", "COR-20", "settlement_currency_code", "KES", "net_amount", new BigDecimal("50.00"), "status", "APPROVED")));
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("settlement_posting_runs"), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from corridor_settlement_items"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 2L, "transfer_id", 42L, "settlement_amount", new BigDecimal("50.00"))));
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(89L);
        SettlementPostingAutomationService service = new SettlementPostingAutomationService(jdbcTemplate, ledgerService);

        Map<String, Object> result = service.postCorridorSettlement(20L, "finance-user");

        assertThat(result)
                .containsEntry("postingRunId", 89L)
                .containsEntry("settlementType", "CORRIDOR")
                .containsEntry("status", "POSTED");
        ArgumentCaptor<List<LedgerEntryCommand>> entriesCaptor = ArgumentCaptor.forClass(List.class);
        verify(ledgerService).post(anyString(), anyString(), anyString(), anyString(), entriesCaptor.capture());
        assertBalanced(entriesCaptor.getValue());
    }

    @Test
    void duplicatePostingIsRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from finance_settlement_batches"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 10L, "settlement_reference", "SET-10", "currency_code", "UGX", "net_amount", BigDecimal.ONE, "status", "APPROVED")));
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("settlement_posting_runs"), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(1);
        SettlementPostingAutomationService service = new SettlementPostingAutomationService(jdbcTemplate, ledgerService);

        assertThatThrownBy(() -> service.postFinanceSettlement(10L, "finance-user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been posted");
    }

    @Test
    void imbalancedSettlementIsRejectedBeforeLedgerPosting() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from finance_settlement_batches"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 10L, "settlement_reference", "SET-10", "currency_code", "UGX", "net_amount", new BigDecimal("100.00"), "status", "APPROVED")));
        when(jdbcTemplate.queryForObject(org.mockito.ArgumentMatchers.contains("settlement_posting_runs"), org.mockito.ArgumentMatchers.eq(Integer.class), any(), any()))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.contains("from finance_settlement_items"), org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(List.of(Map.of("id", 1L, "transaction_reference", "TX-1", "net_amount", new BigDecimal("99.00"))));
        SettlementPostingAutomationService service = new SettlementPostingAutomationService(jdbcTemplate, ledgerService);

        assertThatThrownBy(() -> service.postFinanceSettlement(10L, "finance-user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("variance");
    }

    private void assertBalanced(List<LedgerEntryCommand> entries) {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerEntryCommand entry : entries) {
            if ("DR".equals(entry.direction())) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
        }
        assertThat(debits).isEqualByComparingTo(credits);
    }
}
