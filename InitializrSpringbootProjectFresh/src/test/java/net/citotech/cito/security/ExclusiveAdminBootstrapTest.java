package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ExclusiveAdminBootstrapTest {

    @Test
    void remainsDisabledUnlessApplyIsExplicitlyEnabled() {
        ExclusiveAdminProvisioner provisioner = mock(ExclusiveAdminProvisioner.class);
        SessionRevocationService sessions = mock(SessionRevocationService.class);
        ExclusiveAdminBootstrap bootstrap =
                new ExclusiveAdminBootstrap(
                        provisioner,
                        sessions,
                        false,
                        "operation-1",
                        "admin@example.com",
                        "Admin",
                        "");

        bootstrap.run(new DefaultApplicationArguments());

        verify(provisioner, never()).apply(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void rejectsRawPasswordsAndNonCostTwelveHashes() {
        ExclusiveAdminProvisioner provisioner = mock(ExclusiveAdminProvisioner.class);
        SessionRevocationService sessions = mock(SessionRevocationService.class);
        ExclusiveAdminBootstrap bootstrap =
                new ExclusiveAdminBootstrap(
                        provisioner,
                        sessions,
                        true,
                        "operation-1",
                        "admin@example.com",
                        "Admin",
                        "raw-password");

        assertThatThrownBy(() -> bootstrap.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bcrypt cost-12 hash");
        verify(provisioner, never()).apply(anyString(), anyString(), anyString(), anyString());
    }
}
