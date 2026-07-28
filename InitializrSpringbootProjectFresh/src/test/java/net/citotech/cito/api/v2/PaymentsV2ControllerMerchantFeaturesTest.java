package net.citotech.cito.api.v2;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.AccountValidationRequest;
import net.citotech.cito.api.v2.dto.AccountValidationResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.api.v2.dto.StatementExportResponse.StatementRow;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

class PaymentsV2ControllerMerchantFeaturesTest {

    private final PaymentOrchestrationService orchestrationService = mock(PaymentOrchestrationService.class);
    private final PaymentStatusService paymentStatusService = mock(PaymentStatusService.class);
    private final V2RequestSecurityService securityService = mock(V2RequestSecurityService.class);
    private final IdempotencyService idempotencyService = mock(IdempotencyService.class);
    private final AccountValidationService accountValidationService = mock(AccountValidationService.class);
    private final MerchantStatementExportService statementExportService = mock(MerchantStatementExportService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void accountValidationUsesV2SecurityAndReturnsAccountDetails() throws Exception {
        String body = "{\"merchantNumber\":\"M100\",\"msisdn\":\"256770000000\"}";
        Merchant merchant = merchant(Common.API_ACCOUNT_VALIDATION);
        AccountValidationResponse response = new AccountValidationResponse();
        response.setMsisdn("256770000000");
        response.setName("Jane Customer");
        response.setStatus("ACTIVE");

        when(securityService.verify(any(), eq(body), eq("M100"))).thenReturn(merchant);
        when(accountValidationService.validate(any(AccountValidationRequest.class), eq(merchant))).thenReturn(response);

        mockMvc().perform(post("/api/v2/accounts/validate")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.msisdn").value("256770000000"))
            .andExpect(jsonPath("$.name").value("Jane Customer"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void statementsCanBeExportedAsCsv() throws Exception {
        Merchant merchant = merchant(Common.API_STATEMENT_EXPORT);
        StatementExportResponse export = statementExport();

        when(securityService.verify(any(), eq(""), eq("M100"))).thenReturn(merchant);
        when(statementExportService.export(eq(merchant), eq("M100"), eq("2026-07-01"), eq("2026-07-16"), eq(100)))
            .thenReturn(export);
        when(statementExportService.toCsv(export)).thenReturn("id,created_on\n1,2026-07-16 09:30:00\n");

        mockMvc().perform(get("/api/v2/statements")
                .param("merchantNumber", "M100")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-16")
                .param("format", "csv")
                .param("limit", "100"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"cpay-statement-M100-2026-07-01-to-2026-07-16.csv\""))
            .andExpect(content().string("id,created_on\n1,2026-07-16 09:30:00\n"));
    }

    @Test
    void statementsDefaultToJson() throws Exception {
        Merchant merchant = merchant(Common.API_STATEMENT_EXPORT);
        StatementExportResponse export = statementExport();

        when(securityService.verify(any(), eq(""), eq("M100"))).thenReturn(merchant);
        when(statementExportService.export(eq(merchant), eq("M100"), eq("2026-07-01"), eq("2026-07-16"), eq(null)))
            .thenReturn(export);

        mockMvc().perform(get("/api/v2/statements")
                .param("merchantNumber", "M100")
                .param("startDate", "2026-07-01")
                .param("endDate", "2026-07-16"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.merchantNumber").value("M100"))
            .andExpect(jsonPath("$.count").value(1));
    }

    private MockMvc mockMvc() {
        PaymentsV2Controller controller = new PaymentsV2Controller(
            orchestrationService,
            paymentStatusService,
            securityService,
            idempotencyService,
            accountValidationService,
            statementExportService,
            objectMapper
        );
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private Merchant merchant(String api) {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setAccount_number("M100");
        merchant.setStatus("ACTIVE");
        merchant.setAllowed_apis(new String[] {api});
        return merchant;
    }

    private StatementExportResponse statementExport() {
        StatementRow row = new StatementRow();
        row.setId(1L);
        row.setCreatedOn("2026-07-16 09:30:00");
        row.setAmount(new BigDecimal("2500.00"));
        row.setCurrency("UGX");

        StatementExportResponse response = new StatementExportResponse();
        response.setMerchantNumber("M100");
        response.setStartDate("2026-07-01");
        response.setEndDate("2026-07-16");
        response.setCount(1);
        response.setRows(List.of(row));
        return response;
    }
}
