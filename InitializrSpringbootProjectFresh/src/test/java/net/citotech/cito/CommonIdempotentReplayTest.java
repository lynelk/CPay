package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.Model.Transaction;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Covers audit D1: a legacy doPayIn/doPayOut retry that reuses a tx_merchant_ref must replay the
 * original request's actual outcome (for a terminal transaction) instead of a bare "already
 * submitted" rejection, so a client that retried after a timeout can tell a genuine duplicate from
 * a successful/failed original apart and knows the real result either way.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class CommonIdempotentReplayTest {

    @Test
    void doPayInReplaysTheSuccessfulOutcomeOfTheOriginalRequest() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubExistingTxByRef(jdbcTemplate, "ref-1", "SUCCESSFUL", "net-1", "uid-1", "trace-1");

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-1");
        Merchant merchant = new Merchant();
        merchant.setId(12L);

        String response = Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("state")).isEqualTo("OK");
        assertThat(json.getString("code")).isEqualTo("000");
        JSONObject txDetails = json.getJSONObject("txDetails");
        assertThat(txDetails.getString("uniqueTransactionId")).isEqualTo("uid-1");
        assertThat(txDetails.getString("networkRef")).isEqualTo("net-1");
        assertThat(txDetails.getString("transactionStatus")).isEqualTo("SUCCESSFUL");
        verifyNoInteractions(transactionManager);
    }

    @Test
    void doPayInReplaysTheFailedOutcomeOfTheOriginalRequest() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubExistingTxByRef(jdbcTemplate, "ref-2", "FAILED", "net-2", "uid-2", "trace-2");

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-2");
        Merchant merchant = new Merchant();
        merchant.setId(12L);

        String response = Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("state")).isEqualTo("ERROR");
        assertThat(json.getString("code")).isEqualTo("143");
        JSONObject txDetails = json.getJSONObject("txDetails");
        assertThat(txDetails.getString("networkRef")).isEqualTo("net-2");
        assertThat(txDetails.getString("transactionStatus")).isEqualTo("FAILED");
        verifyNoInteractions(transactionManager);
    }

    @Test
    void doPayInStillRejectsARetryWhileTheOriginalIsNotYetTerminal() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubExistingTxByRef(jdbcTemplate, "ref-3", "PENDING", "", "uid-3", "");

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-3");
        Merchant merchant = new Merchant();
        merchant.setId(12L);

        String response = Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("state")).isEqualTo("ERROR");
        assertThat(json.getString("code")).isEqualTo("121");
        assertThat(json.getString("message")).contains("ref-3");
        verifyNoInteractions(transactionManager);
    }

    @Test
    void doPayOutAlsoReplaysTheSuccessfulOutcomeOfTheOriginalRequest() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubExistingTxByRef(jdbcTemplate, "ref-4", "SUCCESSFUL", "net-4", "uid-4", "trace-4");
        stubUseMerchantProviderCredentials(jdbcTemplate, true);

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-4");
        Merchant merchant = new Merchant();
        merchant.setId(12L);

        String response = Common.doPayOut(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("state")).isEqualTo("OK");
        assertThat(json.getString("code")).isEqualTo("000");
        assertThat(json.getJSONObject("txDetails").getString("uniqueTransactionId")).isEqualTo("uid-4");
        verifyNoInteractions(transactionManager);
    }

    private void stubExistingTxByRef(NamedParameterJdbcTemplate jdbcTemplate, String ref, String status,
            String gatewayRef, String uniqueId, String requestTrace) {
        Transaction existing = new Transaction();
        existing.setTx_merchant_ref(ref);
        existing.setStatus(status);
        existing.setTx_gateway_ref(gatewayRef);
        existing.setTx_unique_id(uniqueId);
        existing.setTx_request_trace(requestTrace);
        when(jdbcTemplate.query(contains("tx_merchant_ref=:tx_merchant_ref"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(existing));
    }

    private void stubUseMerchantProviderCredentials(NamedParameterJdbcTemplate jdbcTemplate, boolean value) {
        Setting setting = new Setting();
        setting.setSetting_value(String.valueOf(value));
        when(jdbcTemplate.query(contains("WHERE name=:name"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(setting));
    }
}
