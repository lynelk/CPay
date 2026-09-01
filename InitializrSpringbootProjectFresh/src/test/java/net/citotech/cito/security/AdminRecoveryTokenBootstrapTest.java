package net.citotech.cito.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citotech.cito.security.AdminRecoveryTokenIssuer.IssueResult;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class AdminRecoveryTokenBootstrapTest {

    @Test
    void remainsDisabledWhenRecoveryPropertiesAreBlank() {
        AdminRecoveryTokenIssuer issuer = mock(AdminRecoveryTokenIssuer.class);
        AdminRecoveryTokenBootstrap bootstrap =
                new AdminRecoveryTokenBootstrap(issuer, "", "");

        bootstrap.run(new DefaultApplicationArguments());

        verify(issuer, never()).issue(anyString(), anyString());
    }

    @Test
    void rejectsRawOrMalformedTokenValues() {
        AdminRecoveryTokenIssuer issuer = mock(AdminRecoveryTokenIssuer.class);
        AdminRecoveryTokenBootstrap bootstrap =
                new AdminRecoveryTokenBootstrap(issuer, "admin@example.com", "raw-reset-code");

        bootstrap.run(new DefaultApplicationArguments());

        verify(issuer, never()).issue(anyString(), anyString());
    }

    @Test
    void passesOnlyNormalizedDigestToIssuer() {
        AdminRecoveryTokenIssuer issuer = mock(AdminRecoveryTokenIssuer.class);
        String uppercaseHash = "A".repeat(64);
        when(issuer.issue("admin@example.com", "a".repeat(64))).thenReturn(IssueResult.ISSUED);
        AdminRecoveryTokenBootstrap bootstrap =
                new AdminRecoveryTokenBootstrap(issuer, "admin@example.com", uppercaseHash);

        bootstrap.run(new DefaultApplicationArguments());

        verify(issuer).issue("admin@example.com", "a".repeat(64));
    }
}
