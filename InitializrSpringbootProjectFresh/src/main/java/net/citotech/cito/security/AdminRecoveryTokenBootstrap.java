package net.citotech.cito.security;

import java.util.Locale;
import java.util.regex.Pattern;
import net.citotech.cito.security.AdminRecoveryTokenIssuer.IssueResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Disabled-by-default operational bridge for issuing an admin reset code when email is unavailable.
 *
 * <p>Both properties are required. Only a SHA-256 digest is accepted, so the operator-held raw code
 * never enters application configuration or logs.
 */
@Component
public class AdminRecoveryTokenBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminRecoveryTokenBootstrap.class);
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");

    private final AdminRecoveryTokenIssuer issuer;
    private final String email;
    private final String tokenSha256;

    public AdminRecoveryTokenBootstrap(
            AdminRecoveryTokenIssuer issuer,
            @Value("${cpay.admin-recovery.email:}") String email,
            @Value("${cpay.admin-recovery.token-sha256:}") String tokenSha256) {
        this.issuer = issuer;
        this.email = email == null ? "" : email.trim();
        this.tokenSha256 = tokenSha256 == null ? "" : tokenSha256.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() && tokenSha256.isBlank()) {
            return;
        }
        if (email.isBlank() || tokenSha256.isBlank()) {
            LOGGER.error(
                    "Admin recovery is incomplete; both recovery email and token digest are required");
            return;
        }
        if (!SHA256.matcher(tokenSha256).matches()) {
            LOGGER.error("Admin recovery token digest must be exactly 64 hexadecimal characters");
            return;
        }

        IssueResult result = issuer.issue(email, tokenSha256.toLowerCase(Locale.ROOT));
        String maskedEmail = PiiMasking.maskEmail(email);
        switch (result) {
            case ISSUED ->
                    LOGGER.warn(
                            "Issued a single-use operational password-reset token for admin {}",
                            maskedEmail);
            case ALREADY_PROCESSED ->
                    LOGGER.info(
                            "Operational password-reset token for admin {} was already processed",
                            maskedEmail);
            case ACCOUNT_NOT_FOUND ->
                    LOGGER.error(
                            "Operational password-reset token was not issued: admin {} does not exist",
                            maskedEmail);
            case ACCOUNT_NOT_ACTIVE ->
                    LOGGER.error(
                            "Operational password-reset token was not issued: admin {} is not active",
                            maskedEmail);
            case AMBIGUOUS_ACCOUNT ->
                    LOGGER.error(
                            "Operational password-reset token was not issued: admin {} is ambiguous",
                            maskedEmail);
        }
    }
}
