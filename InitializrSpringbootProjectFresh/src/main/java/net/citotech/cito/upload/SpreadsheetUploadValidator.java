package net.citotech.cito.upload;

import java.util.Optional;
import java.util.Set;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Uniform CSV/XLSX upload validation (audit E11) - shared so every multipart upload endpoint
 * enforces the same size/extension/content-type checks, instead of each controller reimplementing
 * (or, as found for {@code StatementCheckController}/{@code ReconController}, omitting) its own.
 *
 * <p>Returns a plain reason string rather than throwing, so each caller can translate a failure
 * into whatever error-response shape its own API contract already uses (legacy {@code
 * GeneralException} codes for the {@code /api/v1}-adjacent {@code TransactionsLogController} upload
 * endpoints, {@code PaymentGatewayException} for the {@code /api/v2/admin/**} REST controllers).
 */
public final class SpreadsheetUploadValidator {
    /** Matches the cap already enforced on the legacy beneficiary/SMS-recipient uploads. */
    public static final long MAX_UPLOAD_BYTES = 2 * 1024 * 1024;

    /** Beneficiary/SMS-recipient uploads: Excel only, no CSV. */
    public static final Set<String> SPREADSHEET_EXTENSIONS = Set.of("xlsx", "xls");

    /** Provider statement uploads: CSV or XLSX (audit O1). */
    public static final Set<String> STATEMENT_EXTENSIONS = Set.of("xlsx", "xls", "csv");

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // xlsx
                    "application/vnd.ms-excel", // xls (some tools also send this for csv)
                    "text/csv",
                    "text/plain", // csv exported by some OSes/tools
                    "application/octet-stream" // generic fallback many browsers send
                    );

    private SpreadsheetUploadValidator() {}

    /** Validate against the default 2 MB cap and the given allowed extensions. */
    public static Optional<String> validate(MultipartFile file, Set<String> allowedExtensions) {
        return validate(file, MAX_UPLOAD_BYTES, allowedExtensions);
    }

    public static Optional<String> validate(
            MultipartFile file, long maxBytes, Set<String> allowedExtensions) {
        if (file == null || file.isEmpty()) {
            return Optional.of("Uploaded file is empty.");
        }
        if (file.getSize() > maxBytes) {
            return Optional.of(
                    "Uploaded file exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit.");
        }
        String originalName = StringUtils.cleanPath(String.valueOf(file.getOriginalFilename()));
        String ext = extensionOf(originalName);
        if (ext == null || !allowedExtensions.contains(ext.toLowerCase())) {
            return Optional.of(
                    "Unsupported file extension for \""
                            + originalName
                            + "\" (allowed: "
                            + String.join(", ", allowedExtensions)
                            + ").");
        }
        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.isBlank()
                && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return Optional.of("Unsupported upload content type: " + contentType + ".");
        }
        return Optional.empty();
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1);
    }
}
