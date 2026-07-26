package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class RiskDecisionServiceTest {

    @Test
    void blocksBlocklistedAccountsBeforeProviderCall() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ComplianceCaseService complianceCaseService = mock(ComplianceCaseService.class);
        SanctionsScreeningService sanctionsScreeningService = mock(SanctionsScreeningService.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
            .thenReturn(1);

        RiskDecisionService service = new RiskDecisionService(jdbcTemplate, complianceCaseService, sanctionsScreeningService);

        assertThatThrownBy(() -> service.authorizePayment(merchant(), request(), "COLLECT"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("Risk authorization blocked");

        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
        verify(complianceCaseService).captureRiskDecision(
            eq(17L),
            eq("RISK-1"),
            eq("COLLECT"),
            argThat(amount -> amount.compareTo(new BigDecimal("10000")) == 0),
            eq("UGX"),
            any(RiskDecision.class));
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(17L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        return merchant;
    }

    private PaymentRequest request() {
        PaymentPartyRequest payer = new PaymentPartyRequest();
        payer.setType("MSISDN");
        payer.setValue("256770000000");

        PaymentRequest request = new PaymentRequest();
        request.setMerchantNumber("M100");
        request.setAmount("10000");
        request.setCurrency("UGX");
        request.setReference("RISK-1");
        request.setPayer(payer);
        return request;
    }
}
