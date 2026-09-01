package net.citotech.cito.security;

import java.util.Locale;
import java.util.regex.Pattern;
import net.citotech.cito.security.ExclusiveAdminProvisioner.ProvisionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ExclusiveAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExclusiveAdminBootstrap.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern OPERATION_ID = Pattern.compile("[A-Za-z0-9._-]{8,64}");
    private static final Pattern BCRYPT_12 =
            Pattern.compile("\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}");

    private final ExclusiveAdminProvisioner provisioner;
    private final SessionRevocationService sessionRevocationService;
    private final boolean apply;
    private final String operationId;
    private final String email;
    private final String name;
    private final String passwordHash;

    public ExclusiveAdminBootstrap(
            ExclusiveAdminProvisioner provisioner,
            SessionRevocationService sessionRevocationService,
            @Value("${cpay.exclusive-admin.apply:false}") boolean apply,
            @Value("${cpay.exclusive-admin.operation-id:}") String operationId,
            @Value("${cpay.exclusive-admin.email:}") String email,
            @Value("${cpay.exclusive-admin.name:Platform Administrator}") String name,
            @Value("${cpay.exclusive-admin.password-hash:}") String passwordHash) {
        this.provisioner = provisioner;
        this.sessionRevocationService = sessionRevocationService;
        this.apply = apply;
        this.operationId = trimmed(operationId);
        this.email = trimmed(email).toLowerCase(Locale.ROOT);
        this.name = trimmed(name);
        this.passwordHash = trimmed(passwordHash);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!apply) {
            return;
        }
        validateConfiguration();

        ProvisionResult result = provisioner.apply(operationId, email, name, passwordHash);
        if (result.alreadyProcessed()) {
            LOGGER.info("Exclusive administrator operation {} was already processed", operationId);
            return;
        }
        result.revokedAdminIds().forEach(sessionRevocationService::revokeAllForAdmin);
        LOGGER.warn(
                "Exclusive administrator operation {} completed for {}: removed={}, privileges={}",
                operationId,
                PiiMasking.maskEmail(email),
                result.removedAdminCount(),
                result.grantedPrivilegeCount());
    }

    private void validateConfiguration() {
        if (!OPERATION_ID.matcher(operationId).matches()) {
            throw new IllegalStateException(
                    "Exclusive administrator operation id must contain 8-64 safe characters");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 255) {
            throw new IllegalStateException("Exclusive administrator email is invalid");
        }
        if (name.isBlank() || name.length() > 255) {
            throw new IllegalStateException("Exclusive administrator name is invalid");
        }
        if (!BCRYPT_12.matcher(passwordHash).matches()) {
            throw new IllegalStateException(
                    "Exclusive administrator password must be a bcrypt cost-12 hash");
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
