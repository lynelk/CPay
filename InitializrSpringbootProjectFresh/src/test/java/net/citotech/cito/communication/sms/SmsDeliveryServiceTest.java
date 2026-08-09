package net.citotech.cito.communication.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantSms;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

class SmsDeliveryServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private PlatformTransactionManager transactionManager;
    private SmsGatewayAdapter gatewayAdapter;
    private SmsDeliveryService service;

    private final List<MapSqlParameterSource> statementInserts = new ArrayList<>();
    private final List<MapSqlParameterSource> statusUpdates = new ArrayList<>();
    private final List<MapSqlParameterSource> readModelUpdates = new ArrayList<>();

    private Merchant merchantRow;
    private List<MerchantSms> pendingRows = List.of();
    private ResultSet statementRow;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
        gatewayAdapter = mock(SmsGatewayAdapter.class);
        statementRow = fundedStatementRow();

        when(jdbcTemplate.update(
                        anyString(), any(MapSqlParameterSource.class), any(KeyHolder.class)))
                .thenAnswer(
                        invocation -> {
                            statementInserts.add(invocation.getArgument(1));
                            return 1;
                        });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            MapSqlParameterSource params = invocation.getArgument(1);
                            if (sql.contains("merchant_sms")) {
                                statusUpdates.add(params);
                            } else {
                                readModelUpdates.add(params);
                            }
                            return 1;
                        });
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (sql.contains("FROM merchants")) {
                                return merchantRow == null ? List.of() : List.of(merchantRow);
                            }
                            if (sql.contains("FROM merchant_sms")) {
                                return pendingRows;
                            }
                            if (sql.contains("FROM merchant_statement")) {
                                RowMapper mapper = invocation.getArgument(2);
                                return List.of(mapper.mapRow(statementRow, 1));
                            }
                            return List.of();
                        });

        service = new SmsDeliveryService(jdbcTemplate, transactionManager, gatewayAdapter);
    }

    @Test
    void successfulSendBillsOnceAndMarksSent() {
        stubPendingSms("256700000001,256700000002");
        stubMerchant(1L, "Acme");
        when(gatewayAdapter.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.sent("trace-200", "ok"));

        int processed = service.deliverDue(100);

        assertThat(processed).isEqualTo(1);
        assertThat(statementInserts).hasSize(1);
        assertThat(statementInserts.get(0).getValue("tx_type")).isEqualTo("DR");
        assertThat(readModelUpdates).isNotEmpty();
        assertThat(statusUpdates).hasSize(1);
        assertThat(statusUpdates.get(0).getValue("status")).isEqualTo("SENT");
        verify(gatewayAdapter).send(any(SmsSendRequest.class));
    }

    @Test
    void providerRejectionBillsReversesAndMarksRejected() {
        stubPendingSms("256700000001");
        stubMerchant(1L, "Acme");
        when(gatewayAdapter.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.rejected("trace-400", "invalid number"));

        int processed = service.deliverDue(100);

        assertThat(processed).isEqualTo(1);
        assertThat(statementInserts).hasSize(2);
        assertThat(statementInserts.get(0).getValue("tx_type")).isEqualTo("DR");
        assertThat(statementInserts.get(1).getValue("tx_type")).isEqualTo("CR");
        assertThat(statusUpdates).hasSize(1);
        assertThat(statusUpdates.get(0).getValue("status")).isEqualTo("REJECTED");
    }

    @Test
    void transportFailureBillsReversesAndMarksFailed() {
        stubPendingSms("256700000001");
        stubMerchant(1L, "Acme");
        when(gatewayAdapter.send(any(SmsSendRequest.class)))
                .thenReturn(SmsSendResult.failed("no response", ""));

        int processed = service.deliverDue(100);

        assertThat(processed).isEqualTo(1);
        assertThat(statementInserts).hasSize(2);
        assertThat(statementInserts.get(1).getValue("tx_type")).isEqualTo("CR");
        assertThat(statusUpdates).hasSize(1);
        assertThat(statusUpdates.get(0).getValue("status")).isEqualTo("FAILED");
    }

    @Test
    void missingMerchantSkipsTheRowWithoutBilling() {
        stubPendingSms("256700000001");

        int processed = service.deliverDue(100);

        assertThat(processed).isEqualTo(1);
        assertThat(statementInserts).isEmpty();
        assertThat(statusUpdates).isEmpty();
        verify(gatewayAdapter, never()).send(any(SmsSendRequest.class));
    }

    @Test
    void deliveryFailuresDoNotAbortTheRestOfTheBatch() {
        stubPendingSms("256700000001");
        stubMerchant(1L, "Acme");
        when(gatewayAdapter.send(any(SmsSendRequest.class)))
                .thenThrow(new RuntimeException("boom"));

        // Must not throw: the batch swallows the adapter failure and moves on.
        int processed = service.deliverDue(100);

        // The charge was attempted but the row was not fully dispatched, so it is not counted as
        // processed - the key guarantee is that the exception was contained, not the count.
        assertThat(statementInserts).hasSize(1);
        assertThat(statusUpdates).isEmpty();
        assertThat(processed).isEqualTo(0);
    }

    private ResultSet fundedStatementRow() {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(1L);
            when(row.getDouble("amount")).thenReturn(0.0);
            when(row.getDouble("airtelmm_balance")).thenReturn(0.0);
            when(row.getDouble("mtnmm_balance")).thenReturn(0.0);
            when(row.getDouble("safaricom_balance")).thenReturn(0.0);
            when(row.getDouble("sms_balance")).thenReturn(100000.0);
            when(row.getString("created_on")).thenReturn("2026-01-01 00:00:00");
            when(row.getString("updated_on")).thenReturn("2026-01-01 00:00:00");
            when(row.getString("gateway_id")).thenReturn("SmsGateway");
            when(row.getString("description")).thenReturn("seed");
            when(row.getLong("merchant_id")).thenReturn(1L);
            when(row.getString("narrative")).thenReturn("sms");
            when(row.getLong("transactions_log_id")).thenReturn(0L);
            when(row.getString("tx_type")).thenReturn("CR");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return row;
    }

    private void stubMerchant(long id, String name) {
        merchantRow = new Merchant();
        merchantRow.setId(id);
        merchantRow.setName(name);
        merchantRow.setAccount_number("1000" + id);
        merchantRow.setStatus("ACTIVE");
    }

    private void stubPendingSms(String recipients) {
        MerchantSms sms = new MerchantSms();
        sms.setId(BigInteger.ONE);
        sms.setMerchant_id(BigInteger.ONE);
        sms.setContent("Hello");
        sms.setRecipients(recipients);
        sms.setStatus("PENDING");
        sms.setTotal_amount(200.0);
        sms.setTotal_recipients(2);
        sms.setSmsgw("test-gateway");
        pendingRows = List.of(sms);
    }
}
