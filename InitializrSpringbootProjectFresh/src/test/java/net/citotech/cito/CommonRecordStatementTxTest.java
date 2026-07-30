package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Model.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/**
 * Covers the CR/DR balance-update logic shared by {@code Common.recordStatementTx} and
 * {@code Common.recordStatementTxWithoutTransaction}, now consolidated onto one private core
 * (see audit A7 - the two public methods were ~200-line near-duplicates that only differed in
 * how they obtained a {@link TransactionStatus}).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class CommonRecordStatementTxTest {

    @Test
    void creditIncreasesBalanceForConfiguredBalanceType() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplate, 5000.0, 1000.0, 200.0, 50.0);
        List<MapSqlParameterSource> inserted = captureInsertParameters(jdbcTemplate);
        List<MapSqlParameterSource> readModelRows = captureReadModelParameters(jdbcTemplate);
        PlatformTransactionManager transactionManager = transactionManager();

        Statement tx = statement(1L, "CR", 1000.0);
        String result = Common.recordStatementTx(tx, "mtnmm_balance", jdbcTemplate, transactionManager);

        assertThat(result).isEqualTo("success");
        assertThat(inserted).hasSize(1);
        assertThat((Double) inserted.get(0).getValue("mtnmm_balance")).isEqualTo(6000.0);
        assertThat((Double) inserted.get(0).getValue("airtelmm_balance")).isEqualTo(1000.0);
        assertThat(inserted.get(0).getValue("currency")).isEqualTo("UGX");
        assertThat(readModelRows).hasSize(1);
        assertThat(readModelRows.get(0).getValue("channel_code")).isEqualTo("mtn_momo");
        assertThat(readModelRows.get(0).getValue("gateway_id")).isEqualTo("MTNMoMoPaymentGateway");
        assertThat(readModelRows.get(0).getValue("currency")).isEqualTo("UGX");
        assertThat(readModelRows.get(0).getValue("available_balance")).isEqualTo(new BigDecimal("6000.0000"));
        assertThat(readModelRows.get(0).getValue("ledger_balance")).isEqualTo(new BigDecimal("6000.0000"));
    }

    @Test
    void debitDecreasesBalanceWhenSufficientFunds() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplate, 5000.0, 1000.0, 200.0, 50.0);
        List<MapSqlParameterSource> inserted = captureInsertParameters(jdbcTemplate);
        PlatformTransactionManager transactionManager = transactionManager();

        Statement tx = statement(1L, "DR", 1500.0);
        String result = Common.recordStatementTx(tx, "mtnmm_balance", jdbcTemplate, transactionManager);

        assertThat(result).isEqualTo("success");
        assertThat((Double) inserted.get(0).getValue("mtnmm_balance")).isEqualTo(3500.0);
    }

    @Test
    void debitRejectsAndRollsBackWhenBalanceIsInsufficient() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplate, 500.0, 1000.0, 200.0, 50.0);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Statement tx = statement(1L, "DR", 5000.0);
        String result = Common.recordStatementTx(tx, "mtnmm_balance", jdbcTemplate, transactionManager);

        assertThat(result).isNotEqualTo("success");
        assertThat(result).contains("111");
        verify(transactionStatus).setRollbackOnly();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class));
    }

    @Test
    void firstStatementForMerchantDefaultsExistingBalancesToZero() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of());
        List<MapSqlParameterSource> inserted = captureInsertParameters(jdbcTemplate);
        List<MapSqlParameterSource> readModelRows = captureReadModelParameters(jdbcTemplate);
        PlatformTransactionManager transactionManager = transactionManager();

        Statement tx = statement(1L, "CR", 250.0);
        String result = Common.recordStatementTx(tx, "safaricom_balance", jdbcTemplate, transactionManager);

        assertThat(result).isEqualTo("success");
        assertThat((Double) inserted.get(0).getValue("safaricom_balance")).isEqualTo(250.0);
        assertThat((Double) inserted.get(0).getValue("mtnmm_balance")).isEqualTo(0.0);
        assertThat(inserted.get(0).getValue("currency")).isEqualTo("KES");
        assertThat(readModelRows.get(0).getValue("channel_code")).isEqualTo("safaricom_mpesa");
        assertThat(readModelRows.get(0).getValue("currency")).isEqualTo("KES");
        assertThat(readModelRows.get(0).getValue("available_balance")).isEqualTo(new BigDecimal("250.0000"));
    }

    @Test
    void withoutTransactionVariantReusesTheAmbientStatusInsteadOfOpeningItsOwn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplate, 5000.0, 1000.0, 200.0, 50.0);
        List<MapSqlParameterSource> inserted = captureInsertParameters(jdbcTemplate);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus ambientStatus = mock(TransactionStatus.class);

        Statement tx = statement(1L, "CR", 1000.0);
        String result = Common.recordStatementTxWithoutTransaction(
            tx, "mtnmm_balance", jdbcTemplate, transactionManager, ambientStatus);

        assertThat(result).isEqualTo("success");
        assertThat((Double) inserted.get(0).getValue("mtnmm_balance")).isEqualTo(6000.0);
        // Must not open a nested transaction of its own - it is always called from inside one.
        verifyNoInteractions(transactionManager);
    }

    @Test
    void bothVariantsProduceIdenticalResultsForTheSameInput() {
        // Regression guard for the A7 consolidation: recordStatementTx and
        // recordStatementTxWithoutTransaction must stay behaviourally identical now that they
        // share one core implementation.
        NamedParameterJdbcTemplate jdbcTemplateA = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplateA, 5000.0, 1000.0, 200.0, 50.0);
        List<MapSqlParameterSource> insertedA = captureInsertParameters(jdbcTemplateA);

        NamedParameterJdbcTemplate jdbcTemplateB = mock(NamedParameterJdbcTemplate.class);
        stubExistingBalanceRow(jdbcTemplateB, 5000.0, 1000.0, 200.0, 50.0);
        List<MapSqlParameterSource> insertedB = captureInsertParameters(jdbcTemplateB);

        Statement tx = statement(1L, "DR", 750.0);
        String resultA = Common.recordStatementTx(tx, "airtelmm_balance", jdbcTemplateA, transactionManager());
        String resultB = Common.recordStatementTxWithoutTransaction(
            tx, "airtelmm_balance", jdbcTemplateB, mock(PlatformTransactionManager.class), mock(TransactionStatus.class));

        assertThat(resultA).isEqualTo(resultB).isEqualTo("success");
        assertThat((Double) insertedA.get(0).getValue("airtelmm_balance"))
            .isEqualTo((Double) insertedB.get(0).getValue("airtelmm_balance"));
    }

    @Test
    void rejectsUnknownBalanceTypeBeforeWritingEitherBalanceStore() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Statement tx = statement(1L, "CR", 1000.0);
        String result = Common.recordStatementTx(tx, "unknown_balance", jdbcTemplate, transactionManager);

        assertThat(result).contains("102");
        verify(transactionStatus).setRollbackOnly();
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class));
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        return transactionManager;
    }

    private List<MapSqlParameterSource> captureInsertParameters(NamedParameterJdbcTemplate jdbcTemplate) {
        List<MapSqlParameterSource> inserted = new ArrayList<>();
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class)))
            .thenAnswer(invocation -> {
                inserted.add(invocation.getArgument(1));
                return 1;
            });
        return inserted;
    }

    private List<MapSqlParameterSource> captureReadModelParameters(NamedParameterJdbcTemplate jdbcTemplate) {
        List<MapSqlParameterSource> inserted = new ArrayList<>();
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
            .thenAnswer(invocation -> {
                inserted.add(invocation.getArgument(1));
                return 1;
            });
        return inserted;
    }

    private void stubExistingBalanceRow(NamedParameterJdbcTemplate jdbcTemplate,
            double mtnmm, double airtelmm, double safaricom, double sms) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(1L);
            when(row.getDouble("amount")).thenReturn(0.0);
            when(row.getDouble("airtelmm_balance")).thenReturn(airtelmm);
            when(row.getDouble("mtnmm_balance")).thenReturn(mtnmm);
            when(row.getDouble("safaricom_balance")).thenReturn(safaricom);
            when(row.getDouble("sms_balance")).thenReturn(sms);
            when(row.getString("created_on")).thenReturn("2026-01-01 00:00:00");
            when(row.getString("updated_on")).thenReturn("2026-01-01 00:00:00");
            when(row.getString("gateway_id")).thenReturn("MTNMoMoPaymentGateway");
            when(row.getString("description")).thenReturn("previous");
            when(row.getLong("merchant_id")).thenReturn(1L);
            when(row.getString("narrative")).thenReturn("payin");
            when(row.getLong("transactions_log_id")).thenReturn(10L);
            when(row.getString("tx_type")).thenReturn("CR");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }

    private Statement statement(long merchantId, String txType, double amount) {
        Statement tx = new Statement();
        tx.setMerchant_id(merchantId);
        tx.setGateway_id("MTNMoMoPaymentGateway");
        tx.setDescription("test");
        tx.setAmount(amount);
        tx.setTx_type(txType);
        tx.setNarritive("payin");
        tx.setRecorded_by("SYSTEM");
        return tx;
    }
}
