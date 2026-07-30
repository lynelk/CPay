package net.citotech.cito.reconciliation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Covers audit O1: the import endpoint now accepts a multipart file upload (CSV or XLSX) instead of
 * a raw request body, so this proves the multipart wiring itself works end to end through the real
 * controller, not just the underlying service in isolation (see {@code ReconServiceTest}).
 */
class ReconControllerTest {

    @Test
    void importAcceptsAMultipartFileUploadAndReturnsTheImportId() throws Exception {
        ReconService service = mock(ReconService.class);
        when(service.importStatement(eq("MTN"), anyString(), eq("ops-user"), any(byte[].class)))
                .thenReturn(99L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReconController(service)).build();
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "statement.csv",
                        "text/csv",
                        "provider_reference,amount,currency\nPR-1,100,UGX\n"
                                .getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(
                        multipart("/api/v2/admin/reconciliation/import")
                                .file(file)
                                .param("provider", "MTN")
                                .param("importedBy", "ops-user"))
                .andExpect(status().isOk())
                .andExpect(content().string("99"));

        verify(service)
                .importStatement(eq("MTN"), eq("statement.csv"), eq("ops-user"), any(byte[].class));
    }

    @Test
    void candidateTransactionsParsesQueryParametersAndDelegatesToTheService() throws Exception {
        ReconService service = mock(ReconService.class);
        CandidateTransaction match = new CandidateTransaction();
        match.txUniqueId = "TX-1";
        when(service.candidateTransactions(
                        eq("PR-1"),
                        eq(new BigDecimal("100.50")),
                        eq("UGX"),
                        eq(LocalDate.of(2026, 7, 1)),
                        eq(LocalDate.of(2026, 7, 30)),
                        eq(25)))
                .thenReturn(List.of(match));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReconController(service)).build();

        mockMvc.perform(
                        get("/api/v2/admin/reconciliation/candidate-transactions")
                                .param("reference", "PR-1")
                                .param("amount", "100.50")
                                .param("currency", "UGX")
                                .param("from", "2026-07-01")
                                .param("to", "2026-07-30"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"txUniqueId\":\"TX-1\"}]"));
    }

    @Test
    void candidateTransactionsDefaultsOptionalFiltersToNull() throws Exception {
        ReconService service = mock(ReconService.class);
        when(service.candidateTransactions(
                        isNull(), isNull(), isNull(), isNull(), isNull(), eq(25)))
                .thenReturn(List.of());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReconController(service)).build();

        mockMvc.perform(get("/api/v2/admin/reconciliation/candidate-transactions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(service)
                .candidateTransactions(isNull(), isNull(), isNull(), isNull(), isNull(), eq(25));
    }
}
