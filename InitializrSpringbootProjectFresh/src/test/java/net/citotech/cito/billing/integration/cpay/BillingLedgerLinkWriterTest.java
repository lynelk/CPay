package net.citotech.cito.billing.integration.cpay;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class BillingLedgerLinkWriterTest {

    @Test
    void writeInsertsARowWithTheGivenFields() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);

        new BillingLedgerLinkWriter(jdbcTemplate)
                .write(42L, 7L, BillingLedgerLinkType.CHARGE, "CHG-1");

        verify(jdbcTemplate)
                .update(
                        anyString(),
                        argThat(
                                (SqlParameterSource p) ->
                                        p instanceof MapSqlParameterSource
                                                && p.getValue("ledger_transaction_id").equals(42L)
                                                && p.getValue("billing_tenant_id").equals(7L)
                                                && p.getValue("link_type").equals("CHARGE")
                                                && p.getValue("billing_reference")
                                                        .equals("CHG-1")));
    }
}
