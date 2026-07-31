package net.citotech.cito.reconciliation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatementCheckControllerTest {

    @Test
    void checkAcceptsAMultipartFileUploadAndReturnsTheValidationRunId() throws Exception {
        ProviderStatementValidator validator = mock(ProviderStatementValidator.class);
        when(validator.validate(eq("SAFARICOM"), eq("statement.xlsx"), any(byte[].class)))
                .thenReturn(7L);
        MockMvc mockMvc =
                MockMvcBuilders.standaloneSetup(new StatementCheckController(validator)).build();
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "statement.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {1, 2, 3});

        mockMvc.perform(
                        multipart("/api/v2/admin/statements/check")
                                .file(file)
                                .param("provider", "SAFARICOM"))
                .andExpect(status().isOk())
                .andExpect(content().string("7"));

        verify(validator).validate(eq("SAFARICOM"), eq("statement.xlsx"), any(byte[].class));
    }

    /**
     * Audit E11: previously this endpoint had no size/extension/content-type check at all - any
     * file, of any size or type, reached {@link ProviderStatementValidator} directly.
     */
    @Test
    void rejectsAnUnsupportedFileExtensionBeforeReachingTheValidator() {
        ProviderStatementValidator validator = mock(ProviderStatementValidator.class);
        StatementCheckController controller = new StatementCheckController(validator);
        MockMultipartFile file =
                new MockMultipartFile("file", "statement.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> controller.check("SAFARICOM", file))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported file extension");
        verifyNoInteractions(validator);
    }
}
