package net.citotech.cito.reconciliation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Covers audit O1: the import endpoint now accepts a multipart file upload (CSV or XLSX) instead of
 * a raw request body, so this proves the multipart wiring itself works end to end through the real
 * controller, not just the underlying service in isolation (see {@code ReconServiceTest}).
 */
class ReconControllerTest {

    @Test
    void importAcceptsAMultipartFileUploadAndReturnsTheImportId() throws Exception {
        ReconService service = mock(ReconService.class);
        when(service.importStatement(eq("MTN"), anyString(), eq("ops-user"), any(byte[].class))).thenReturn(99L);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ReconController(service)).build();
        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv",
            "provider_reference,amount,currency\nPR-1,100,UGX\n".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v2/admin/reconciliation/import")
                .file(file)
                .param("provider", "MTN")
                .param("importedBy", "ops-user"))
            .andExpect(status().isOk())
            .andExpect(content().string("99"));

        verify(service).importStatement(eq("MTN"), eq("statement.csv"), eq("ops-user"), any(byte[].class));
    }
}
