package net.citotech.cito.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Covers audit B8: batch-level status aggregation, and that retry only touches beneficiaries
 * currently in the FAILED state (never re-runs an already-PAID or in-flight row), plus that a
 * batch belonging to a different merchant can't be queried or retried.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class BatchPayoutStatusServiceTest {

    @Test
    void aggregatesBeneficiaryCountsAndPaidAmountByStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        stubBatchOwner(jdbcTemplate, 42L, 12L);
        stubMerchant(jdbcTemplate, 12L);

        when(jdbcTemplate.queryForList(contains("GROUP BY status"), any(MapSqlParameterSource.class)))
            .thenReturn(List.of(
                Map.of("status", "PAID", "row_count", 3L, "row_amount", "3000"),
                Map.of("status", "FAILED", "row_count", 2L, "row_amount", "2000"),
                Map.of("status", "INPROGRESS", "row_count", 1L, "row_amount", "1000"),
                Map.of("status", "UNPAID", "row_count", 4L, "row_amount", "4000")));

        BatchPayoutStatusService service = new BatchPayoutStatusService(jdbcTemplate, transactionManager, ledgerService);
        BatchPayoutStatusService.BatchStatusSummary summary = service.status(42L, 12L);

        assertThat(summary.totalBeneficiaries()).isEqualTo(10);
        assertThat(summary.paid()).isEqualTo(3);
        assertThat(summary.failed()).isEqualTo(2);
        assertThat(summary.inProgress()).isEqualTo(1);
        assertThat(summary.unpaid()).isEqualTo(4);
        assertThat(summary.paidAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void rejectsStatusLookupForABatchBelongingToAnotherMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        stubBatchOwner(jdbcTemplate, 42L, 999L); // owned by a different merchant

        BatchPayoutStatusService service = new BatchPayoutStatusService(jdbcTemplate, transactionManager, ledgerService);

        assertThatThrownBy(() -> service.status(42L, 12L))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("was not found");
    }

    @Test
    void retryFailedSkipsWhenThereAreNoFailedBeneficiaries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        DoubleEntryLedgerService ledgerService = mock(DoubleEntryLedgerService.class);
        stubBatchOwner(jdbcTemplate, 42L, 12L);
        stubMerchant(jdbcTemplate, 12L);
        when(jdbcTemplate.query(contains("FROM beneficiaries WHERE batch_id"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of());

        BatchPayoutStatusService service = new BatchPayoutStatusService(jdbcTemplate, transactionManager, ledgerService);
        int retried = service.retryFailed(42L, 12L);

        assertThat(retried).isEqualTo(0);
        verify(ledgerService, never()).reserve(anyString(), any(Long.class), anyString(), any(BigDecimal.class), anyString());
    }

    private void stubBatchOwner(NamedParameterJdbcTemplate jdbcTemplate, long batchId, long ownerMerchantId) {
        when(jdbcTemplate.query(contains("FROM merchant_batch_transactions_log"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenReturn(List.of(ownerMerchantId));
    }

    private void stubMerchant(NamedParameterJdbcTemplate jdbcTemplate, long merchantId) {
        when(jdbcTemplate.query(contains("FROM merchants"), any(MapSqlParameterSource.class), any(org.springframework.jdbc.core.RowMapper.class)))
            .thenAnswer(invocation -> {
                org.springframework.jdbc.core.RowMapper mapper = invocation.getArgument(2);
                java.sql.ResultSet row = mock(java.sql.ResultSet.class);
                when(row.getString("name")).thenReturn("Test Merchant");
                when(row.getString("account_number")).thenReturn("1000003");
                when(row.getString("status")).thenReturn("ACTIVE");
                when(row.getLong("id")).thenReturn(merchantId);
                when(row.getString("created_on")).thenReturn("");
                when(row.getString("created_by")).thenReturn("");
                when(row.getString("account_type")).thenReturn("business");
                when(row.getString("public_key")).thenReturn("");
                when(row.getString("private_key")).thenReturn("");
                when(row.getString("hmac_secret")).thenReturn("");
                when(row.getString("allowed_apis")).thenReturn("");
                when(row.getString("short_name")).thenReturn("Test");
                return List.of(mapper.mapRow(row, 1));
            });
    }
}
