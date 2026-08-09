package net.citotech.cito.billing.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Covers {@link BillingTraceChainService}'s query composition against a mocked {@code
 * NamedParameterJdbcTemplate} - distinguishes the two entry points by their {@code FROM} clause and
 * verifies parameter binding. See {@code BillingTraceChainServiceTestcontainersTest}
 * (docker-tagged) for the real proof that the join actually returns the right rows, including the
 * null-ledger-field mapping a mock cannot exercise (row mapping never runs against a mocked {@code
 * query(...)} call).
 */
@SuppressWarnings("unchecked")
class BillingTraceChainServiceTest {

    private final BillingTraceChainRow sampleRow =
            new BillingTraceChainRow(
                    1L,
                    2L,
                    new BigDecimal("1000"),
                    3L,
                    new BigDecimal("1000"),
                    4L,
                    "BINV-1",
                    "FINALIZED",
                    5L,
                    6L,
                    "DR",
                    new BigDecimal("1000"));

    @Test
    void traceBySourceReferenceQueriesTheUsageEventAnchoredJoinWithTheGivenParameters() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("FROM billing_usage_events ue"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of(sampleRow));

        List<BillingTraceChainRow> rows =
                new BillingTraceChainService(jdbcTemplate).traceBySourceReference(7L, "TX-1");

        assertThat(rows).containsExactly(sampleRow);
        verify(jdbcTemplate)
                .query(
                        contains("FROM billing_usage_events ue"),
                        argThat(
                                (SqlParameterSource p) ->
                                        p.getValue("billing_tenant_id").equals(7L)
                                                && p.getValue("source_reference").equals("TX-1")),
                        any(RowMapper.class));
    }

    @Test
    void traceByInvoiceQueriesTheInvoiceAnchoredJoinWithTheGivenInvoiceId() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("FROM billing_invoices bi"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of(sampleRow));

        List<BillingTraceChainRow> rows =
                new BillingTraceChainService(jdbcTemplate).traceByInvoice(4L);

        assertThat(rows).containsExactly(sampleRow);
        verify(jdbcTemplate)
                .query(
                        contains("FROM billing_invoices bi"),
                        argThat(
                                (SqlParameterSource p) ->
                                        p.getValue("billing_invoice_id").equals(4L)),
                        any(RowMapper.class));
    }

    @Test
    void traceBySourceReferenceReturnsAnEmptyListWhenNothingMatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("FROM billing_usage_events ue"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of());

        List<BillingTraceChainRow> rows =
                new BillingTraceChainService(jdbcTemplate).traceBySourceReference(7L, "TX-UNKNOWN");

        assertThat(rows).isEmpty();
    }
}
