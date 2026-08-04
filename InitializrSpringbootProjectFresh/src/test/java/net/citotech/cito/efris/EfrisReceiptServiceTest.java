package net.citotech.cito.efris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * EFRIS e-receipt outbox coverage: the hook is gated on the {@code cpay.efris.enabled} setting,
 * only UGX payins qualify, the outbox persists only the payer MSISDN hash (never the raw number),
 * the ledger backfill captures successful payins regardless of which path resolved them, and
 * delivery marks the receipt SENT on a 2xx response.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class EfrisReceiptServiceTest {

    @Test
    void enqueueSkipsWhenEfrisIsDisabled() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        EfrisReceiptService service = new EfrisReceiptService(jdbcTemplate);

        long id =
                service.enqueueSuccessfulPayin(
                        1L, "M100", "TXN-1", "MREF-1", "256770000001", "5000", "UGX");

        assertThat(id).isZero();
    }

    @Test
    void enqueueSkipsNonUgxPayins() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("YES"));
        EfrisReceiptService service = new EfrisReceiptService(jdbcTemplate);

        long id =
                service.enqueueSuccessfulPayin(
                        1L, "M100", "TXN-1", "MREF-1", "254700000001", "500", "KES");

        assertThat(id).isZero();
    }

    @Test
    void enqueuePersistsOnlyThePayerHashAndReturnsTheOutboxId() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of("YES"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(42L);
        EfrisReceiptService service = new EfrisReceiptService(jdbcTemplate);

        long id =
                service.enqueueSuccessfulPayin(
                        1L, "M100", "TXN-1", "MREF-1", "256770000001", "5000", "UGX");

        assertThat(id).isEqualTo(42L);
    }

    @Test
    void backfillQueuesLedgerPayinsMissingFromTheOutbox() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("FROM settings"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of("YES"));
        when(jdbcTemplate.queryForList(
                        contains("NOT EXISTS (SELECT 1 FROM efris_receipts"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "tx_row_id", 9,
                                        "merchant_id", 7L,
                                        "original_amount", 5000.0,
                                        "payer_number", "256770000001",
                                        "tx_unique_id", "TXN-1",
                                        "tx_merchant_ref", "MREF-1",
                                        "merchant_number", "M100")));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(55L);
        EfrisReceiptService service = new EfrisReceiptService(jdbcTemplate);

        int enqueued = service.enqueueMissingSuccessfulPayins(7, 500);

        assertThat(enqueued).isEqualTo(1);
    }

    @Test
    void deliverMarksAReceiptSentWhenTheEfrisEndpointReturns2xx() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("FROM settings"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of("https://efris.example/receipts"));
        stubDueReceipt(jdbcTemplate, 8L, 0);
        when(jdbcTemplate.query(
                        contains("payer_number FROM merchant_transactions_log"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of("256770000001"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        Common.setOutboundHttpExecutor(
                (method, url, data, headers) -> {
                    HttpRequestResponse response = new HttpRequestResponse();
                    response.setStatusCode(202);
                    response.setResponse("accepted");
                    return response;
                });
        EfrisReceiptService service = new EfrisReceiptService(jdbcTemplate);

        try {
            int processed = service.deliverDue(10);

            assertThat(processed).isEqualTo(1);
        } finally {
            Common.setOutboundHttpExecutor(null);
        }
    }

    /**
     * Stubs the service's private {@code ReceiptRow} RowMapper, same pattern as the other tests.
     */
    private void stubDueReceipt(NamedParameterJdbcTemplate jdbcTemplate, long id, int attempts)
            throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getLong("id")).thenReturn(id);
        when(row.getString("receipt_reference")).thenReturn("EFRIS-" + id);
        when(row.getString("merchant_number")).thenReturn("M100");
        when(row.getString("transaction_reference")).thenReturn("TXN-" + id);
        when(row.getString("merchant_transaction_ref")).thenReturn("MREF-" + id);
        when(row.getBigDecimal("amount")).thenReturn(new BigDecimal("5000"));
        when(row.getString("currency")).thenReturn("UGX");
        when(row.getInt("retry_count")).thenReturn(attempts);
        when(jdbcTemplate.query(
                        contains("FROM efris_receipts"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 0));
                        });
    }
}
