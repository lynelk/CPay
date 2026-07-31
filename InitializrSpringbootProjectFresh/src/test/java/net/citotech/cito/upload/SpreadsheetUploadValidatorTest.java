package net.citotech.cito.upload;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Covers audit E11: the shared upload validator that closed the gap where {@code
 * StatementCheckController}/{@code ReconController} accepted a multipart file with no size,
 * extension, or content-type check at all.
 */
class SpreadsheetUploadValidatorTest {

    @Test
    void acceptsAWellFormedXlsxStatementUpload() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "statement.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        new byte[] {1, 2, 3});

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsACsvStatementUploadButRejectsCsvForSpreadsheetOnlyExtensions() {
        MockMultipartFile file =
                new MockMultipartFile("file", "statement.csv", "text/csv", "a,b\n1,2\n".getBytes());

        assertThat(
                        SpreadsheetUploadValidator.validate(
                                file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS))
                .isEmpty();
        assertThat(
                        SpreadsheetUploadValidator.validate(
                                file, SpreadsheetUploadValidator.SPREADSHEET_EXTENSIONS))
                .isPresent();
    }

    @Test
    void rejectsAnEmptyFile() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "statement.xlsx", "application/vnd.ms-excel", new byte[0]);

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("empty");
    }

    @Test
    void rejectsAFileOverTheSizeLimit() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "statement.csv",
                        "text/csv",
                        new byte[(int) SpreadsheetUploadValidator.MAX_UPLOAD_BYTES + 1]);

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("MB limit");
    }

    @Test
    void rejectsAnUnsupportedExtension() {
        MockMultipartFile file =
                new MockMultipartFile("file", "statement.pdf", "application/pdf", new byte[] {1});

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Unsupported file extension");
    }

    @Test
    void rejectsAnUnsupportedContentTypeEvenWithAnAllowedExtension() {
        MockMultipartFile file =
                new MockMultipartFile(
                        "file", "statement.csv", "application/json", "a,b\n1,2\n".getBytes());

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Unsupported upload content type");
    }

    @Test
    void allowsABlankContentTypeAsALegacyBrowserQuirk() {
        MockMultipartFile file =
                new MockMultipartFile("file", "statement.csv", "", "a,b\n1,2\n".getBytes());

        Optional<String> result =
                SpreadsheetUploadValidator.validate(
                        file, SpreadsheetUploadValidator.STATEMENT_EXTENSIONS);

        assertThat(result).isEmpty();
    }
}
