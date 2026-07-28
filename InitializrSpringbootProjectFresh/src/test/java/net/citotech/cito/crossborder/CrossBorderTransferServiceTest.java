package net.citotech.cito.crossborder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.TransferIntentRequest;
import net.citotech.cito.api.v2.dto.TransferIntentResponse;
import net.citotech.cito.compliance.RiskDecision;
import net.citotech.cito.compliance.RiskDecisionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the cross-border transfer intent money-movement path: corridor limits, FX quote
 * single-use/consistency checks, treasury reservation, and risk-decision handling. This flow
 * previously had no test coverage despite touching money and regulated corridors (see audit K6/I8/P6).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class CrossBorderTransferServiceTest {

    @Test
    void createsReadyIntentForSameCurrencyTransferWithoutFxQuote() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        stubEmptyQuery(jdbcTemplate, "treasury_positions");
        when(riskDecisionService.authorizePayment(any(Merchant.class), any(PaymentRequest.class), eq("PAYOUT")))
            .thenReturn(RiskDecision.allow("ok"));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);
        TransferIntentResponse response = service.createIntent(request("UGX", "UGX", "1000"), merchant());

        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getQuoteReference()).isNull();
        assertThat(response.getSourceAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("1000.00");
        verifyNoInteractions(fxQuoteService);
        verify(jdbcTemplate).update(contains("INSERT INTO transfer_intents"), any(MapSqlParameterSource.class));
    }

    @Test
    void crossCurrencyTransferConsumesMatchingFxQuoteAndMarksItBound() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        stubEmptyQuery(jdbcTemplate, "treasury_positions");
        when(fxQuoteService.findActiveQuote("FX-1", 12L)).thenReturn(new FxQuoteRecord(
            "FX-1", 12L, "UGX", "KES", new BigDecimal("1000.00"), new BigDecimal("3500.00"),
            new BigDecimal("3.5"), Instant.now().plusSeconds(600)));
        when(riskDecisionService.authorizePayment(any(Merchant.class), any(PaymentRequest.class), eq("PAYOUT")))
            .thenReturn(RiskDecision.allow("ok"));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);
        TransferIntentRequest request = request("UGX", "KES", "1000");
        request.setQuoteReference("FX-1");
        TransferIntentResponse response = service.createIntent(request, merchant());

        assertThat(response.getStatus()).isEqualTo("READY");
        assertThat(response.getQuoteReference()).isEqualTo("FX-1");
        assertThat(response.getTargetAmount()).isEqualByComparingTo("3500.00");
        verify(jdbcTemplate).update(contains("UPDATE fx_quotes SET quote_status='BOUND'"), any(MapSqlParameterSource.class));
    }

    @Test
    void rejectsCrossCurrencyTransferWhenQuoteCurrencyDoesNotMatch() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        when(fxQuoteService.findActiveQuote("FX-1", 12L)).thenReturn(new FxQuoteRecord(
            "FX-1", 12L, "UGX", "TZS", new BigDecimal("1000.00"), new BigDecimal("900.00"),
            BigDecimal.ONE, Instant.now().plusSeconds(600)));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);
        TransferIntentRequest request = request("UGX", "KES", "1000");
        request.setQuoteReference("FX-1");

        assertThatThrownBy(() -> service.createIntent(request, merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("FX quote currencies do not match");
        verifyNoInteractions(riskDecisionService);
    }

    @Test
    void rejectsCrossCurrencyTransferWhenQuoteAmountDoesNotMatch() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        when(fxQuoteService.findActiveQuote("FX-1", 12L)).thenReturn(new FxQuoteRecord(
            "FX-1", 12L, "UGX", "KES", new BigDecimal("500.00"), new BigDecimal("1750.00"),
            new BigDecimal("3.5"), Instant.now().plusSeconds(600)));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);
        TransferIntentRequest request = request("UGX", "KES", "1000");
        request.setQuoteReference("FX-1");

        assertThatThrownBy(() -> service.createIntent(request, merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("FX quote amount does not match");
        verifyNoInteractions(riskDecisionService);
    }

    @Test
    void rejectsTransferExceedingCorridorSingleTransferLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("500"), null);

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);

        assertThatThrownBy(() -> service.createIntent(request("UGX", "UGX", "1000"), merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("single-transfer limit");
        verifyNoInteractions(riskDecisionService);
        verifyNoInteractions(fxQuoteService);
    }

    @Test
    void rejectsTransferExceedingCorridorDailyLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), new BigDecimal("2000"));
        when(jdbcTemplate.queryForObject(contains("FROM transfer_intents"), any(MapSqlParameterSource.class), eq(BigDecimal.class)))
            .thenReturn(new BigDecimal("1500"));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);

        assertThatThrownBy(() -> service.createIntent(request("UGX", "UGX", "1000"), merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("daily limit");
        verifyNoInteractions(riskDecisionService);
    }

    @Test
    void rejectsTransferWhenTreasuryPositionIsInsufficient() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        stubTreasury(jdbcTemplate, new BigDecimal("500"), new BigDecimal("0"));
        when(riskDecisionService.authorizePayment(any(Merchant.class), any(PaymentRequest.class), eq("PAYOUT")))
            .thenReturn(RiskDecision.allow("ok"));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);

        assertThatThrownBy(() -> service.createIntent(request("UGX", "UGX", "1000"), merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Treasury position is insufficient");
    }

    @Test
    void marksIntentReviewRequiredWhenRiskDecisionIsReview() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);

        stubCorridor(jdbcTemplate, new BigDecimal("1000000"), null);
        stubEmptyQuery(jdbcTemplate, "treasury_positions");
        when(riskDecisionService.authorizePayment(any(Merchant.class), any(PaymentRequest.class), eq("PAYOUT")))
            .thenReturn(RiskDecision.review("HIGH_VALUE", "needs manual review"));

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);
        TransferIntentResponse response = service.createIntent(request("UGX", "UGX", "1000"), merchant());

        assertThat(response.getStatus()).isEqualTo("REVIEW_REQUIRED");
        assertThat(response.getRiskDecision()).isEqualTo("REVIEW");
    }

    @Test
    void throwsWhenNoActiveCorridorIsConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        FxQuoteService fxQuoteService = mock(FxQuoteService.class);
        RiskDecisionService riskDecisionService = mock(RiskDecisionService.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        CrossBorderTransferService service = new CrossBorderTransferService(jdbcTemplate, fxQuoteService, riskDecisionService);

        assertThatThrownBy(() -> service.createIntent(request("UGX", "UGX", "1000"), merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("No active cross-border corridor");
        verifyNoInteractions(riskDecisionService);
        verifyNoInteractions(fxQuoteService);
    }

    private void stubCorridor(NamedParameterJdbcTemplate jdbcTemplate, BigDecimal singleLimit, BigDecimal dailyLimit) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(1L);
            when(row.getBigDecimal("single_transfer_limit")).thenReturn(singleLimit);
            when(row.getBigDecimal("daily_limit")).thenReturn(dailyLimit);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(contains("cross_border_corridors"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }

    private void stubTreasury(NamedParameterJdbcTemplate jdbcTemplate, BigDecimal available, BigDecimal reserved) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getBigDecimal("available_balance")).thenReturn(available);
            when(row.getBigDecimal("reserved_balance")).thenReturn(reserved);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(contains("treasury_positions"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(row, 1));
            });
    }

    private void stubEmptyQuery(NamedParameterJdbcTemplate jdbcTemplate, String sqlFragment) {
        when(jdbcTemplate.query(contains(sqlFragment), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
    }

    private TransferIntentRequest request(String sourceCurrency, String targetCurrency, String amount) {
        TransferIntentRequest request = new TransferIntentRequest();
        request.setMerchantNumber("1000003");
        request.setSourceCountry("UG");
        request.setTargetCountry("KE");
        request.setSourceCurrency(sourceCurrency);
        request.setTargetCurrency(targetCurrency);
        request.setSourceAmount(amount);
        request.setBeneficiaryAccount("254700000000");
        request.setBeneficiaryName("Jane Doe");
        return request;
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("1000003");
        merchant.setStatus("ACTIVE");
        return merchant;
    }
}
