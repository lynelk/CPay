package net.citotech.cito.checkout;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.PaymentLinkCreateRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PaymentLinkServiceTest {

    @Test
    void refusesToCreateLinksForDifferentMerchantNumber() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);
        PaymentLinkService service = new PaymentLinkService(jdbcTemplate, orchestrationService, "https://cpay.example");

        assertThatThrownBy(() -> service.create(request("OTHER"), merchant()))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("does not match");

        verifyNoInteractions(jdbcTemplate, orchestrationService);
    }

    private PaymentLinkCreateRequest request(String merchantNumber) {
        PaymentLinkCreateRequest request = new PaymentLinkCreateRequest();
        request.setMerchantNumber(merchantNumber);
        request.setAmount("5000");
        request.setCurrency("UGX");
        request.setDescription("Hosted checkout");
        return request;
    }

    private Merchant merchant() {
        Merchant merchant = new Merchant();
        merchant.setId(12L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        return merchant;
    }
}
