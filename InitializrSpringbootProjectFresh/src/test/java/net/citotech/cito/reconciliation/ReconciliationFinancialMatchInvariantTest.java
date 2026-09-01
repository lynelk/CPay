package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ReconciliationFinancialMatchInvariantTest {

    @Test
    void automaticMatchRequiresAmountCurrencyFinalityAndUniqueness() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), org.mockito.ArgumentMatchers.<SqlParameterSource>any()))
                .thenReturn(1);
        ReconciliationRepository repository = new ReconciliationRepository(jdbc);

        assertThat(repository.autoMatchByMerchantReference()).isEqualTo(1);

        verify(jdbc)
                .update(
                        argThat(
                                sql ->
                                        sql.contains("tx.tx_merchant_ref = rr2.merchant_reference")
                                                && sql.contains("rr2.amount")
                                                && sql.contains(
                                                        "UPPER(tx.currency) = UPPER(rr2.currency)")
                                                && sql.contains("UPPER(tx.status) IN")
                                                && sql.contains("HAVING COUNT(*) = 1")
                                                && sql.contains(
                                                        "amount+currency+final_status+unique_candidate")),
                        org.mockito.ArgumentMatchers.<SqlParameterSource>argThat(
                                source -> {
                                    if (!(source instanceof MapSqlParameterSource p)) {
                                        return false;
                                    }
                                    return p.hasValue("amount_tolerance")
                                            && "0.0001"
                                                    .equals(
                                                            p.getValue("amount_tolerance")
                                                                    .toString());
                                }));
    }
}
