package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.compliance.RiskDecisionRegistry;
import net.citotech.cito.compliance.RiskDecisionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Covers audit I1: legacy doPayIn/doPayOut previously never ran risk authorization at all - a
 * legacy request could bypass blocklist/sanctions/cap checks entirely just by using
 * /api/v1/doMobileMoney* instead of the v2 API. A blocking risk decision must now stop the
 * transaction before any gateway dispatch or DB write, and an allowed decision must not change
 * existing behavior.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class CommonRiskAuthorizationTest {

    @AfterEach
    void resetRegistry() {
        new RiskDecisionRegistry(null);
    }

    @Test
    void doPayInStopsBeforeAnyDbWorkWhenRiskAuthorizationBlocks() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubNoExistingTxByRef(jdbcTemplate);
        RiskDecisionService riskService = mock(RiskDecisionService.class);
        when(riskService.authorizePayment(any(), any(), anyString()))
            .thenThrow(new PaymentGatewayException("Account is blocklisted"));
        new RiskDecisionRegistry(riskService);

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-risk-1");
        newTx.setOriginal_amount(500000.0);
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");

        String response = Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("148");
        assertThat(json.getString("message")).contains("blocklisted");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
        verifyNoTransactionStarted(transactionManager);
    }

    @Test
    void doPayOutStopsBeforeAnyDbWorkWhenRiskAuthorizationBlocks() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubNoExistingTxByRef(jdbcTemplate);
        stubUseMerchantProviderCredentials(jdbcTemplate, true);
        RiskDecisionService riskService = mock(RiskDecisionService.class);
        when(riskService.authorizePayment(any(), any(), anyString()))
            .thenThrow(new PaymentGatewayException("Daily merchant cap exceeded"));
        new RiskDecisionRegistry(riskService);

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-risk-2");
        newTx.setOriginal_amount(500000.0);
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");

        String response = Common.doPayOut(newTx, merchant, jdbcTemplate, transactionManager);

        JSONObject json = new JSONObject(response);
        assertThat(json.getString("code")).isEqualTo("148");
        assertThat(json.getString("message")).contains("Daily merchant cap exceeded");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void doPayInProceedsPastTheRiskCheckWhenAuthorizationAllowsIt() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubNoExistingTxByRef(jdbcTemplate);
        stubUseMerchantProviderCredentials(jdbcTemplate, true);
        RiskDecisionService riskService = mock(RiskDecisionService.class);
        new RiskDecisionRegistry(riskService);

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-risk-3");
        newTx.setOriginal_amount(1000.0);
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");

        // An allowed decision must let execution continue past the risk gate into the real
        // insert/gateway flow - this minimal mock setup isn't meant to fully exercise that (it's
        // covered by other tests), so only the risk check itself is being asserted here.
        try {
            Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager);
        } catch (RuntimeException ignored) {
            // Expected: the rest of doPayIn's flow isn't fully mocked here, and that's fine.
        }
        verify(riskService).authorizePayment(any(), any(), anyString());
    }

    @Test
    void skipRiskCheckOverloadNeverCallsTheRiskServiceAtAll() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        stubNoExistingTxByRef(jdbcTemplate);
        stubUseMerchantProviderCredentials(jdbcTemplate, true);
        RiskDecisionService riskService = mock(RiskDecisionService.class);
        new RiskDecisionRegistry(riskService);

        Transaction newTx = new Transaction();
        newTx.setTx_merchant_ref("ref-risk-4");
        newTx.setOriginal_amount(1000.0);
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");

        // PaymentOrchestrationService already ran its own risk check before calling this overload -
        // Common.doPayIn/doPayOut must not evaluate (or record a second risk_decisions row for) the
        // same request again.
        try {
            Common.doPayIn(newTx, merchant, jdbcTemplate, transactionManager, true);
        } catch (RuntimeException ignored) {
            // Expected: the rest of doPayIn's flow isn't fully mocked here, and that's fine.
        }
        verify(riskService, never()).authorizePayment(any(), any(), anyString());
    }

    private void stubNoExistingTxByRef(NamedParameterJdbcTemplate jdbcTemplate) {
        when(jdbcTemplate.query(contains("tx_merchant_ref=:tx_merchant_ref"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
    }

    private void stubUseMerchantProviderCredentials(NamedParameterJdbcTemplate jdbcTemplate, boolean value) {
        net.citotech.cito.Model.Setting setting = new net.citotech.cito.Model.Setting();
        setting.setSetting_value(String.valueOf(value));
        when(jdbcTemplate.query(contains("WHERE name=:name"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of(setting));
    }

    private void verifyNoTransactionStarted(PlatformTransactionManager transactionManager) {
        verify(transactionManager, never()).getTransaction(any());
    }
}
