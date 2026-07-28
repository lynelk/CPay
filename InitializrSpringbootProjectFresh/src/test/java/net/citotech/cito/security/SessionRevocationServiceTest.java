package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.session.Session;

/**
 * Covers audit E4/P3: a password reset must revoke any session still open under the old
 * credentials, so a stolen session cookie doesn't survive a credential rotation.
 */
class SessionRevocationServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void revokeAllForAdminDeletesEverySessionIndexedUnderThatAdmin() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        MapSession sessionA = new MapSession("session-a");
        MapSession sessionB = new MapSession("session-b");
        when(repository.findByPrincipalName("admin-user:42"))
            .thenReturn((Map) Map.of("session-a", sessionA, "session-b", sessionB));

        SessionRevocationService service = new SessionRevocationService(repository);
        service.revokeAllForAdmin(42L);

        verify(repository).deleteById("session-a");
        verify(repository).deleteById("session-b");
    }

    @SuppressWarnings("unchecked")
    @Test
    void revokeAllForMerchantUserUsesTheMerchantUserPrincipalNamespace() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        when(repository.findByPrincipalName("merchant-user:7"))
            .thenReturn((Map) Map.of("session-x", new MapSession("session-x")));

        SessionRevocationService service = new SessionRevocationService(repository);
        service.revokeAllForMerchantUser(7L);

        verify(repository).deleteById("session-x");
        assertThat(SessionRevocationService.merchantUserPrincipal(7L)).isEqualTo("merchant-user:7");
        assertThat(SessionRevocationService.adminPrincipal(7L)).isEqualTo("admin-user:7");
    }

    @SuppressWarnings("unchecked")
    @Test
    void doesNotThrowWhenTheSessionStoreLookupFails() {
        FindByIndexNameSessionRepository<Session> repository = mock(FindByIndexNameSessionRepository.class);
        when(repository.findByPrincipalName(eq("admin-user:1"))).thenThrow(new RuntimeException("db down"));
        SessionRevocationService service = new SessionRevocationService(repository);

        service.revokeAllForAdmin(1L);
        // No exception should propagate - a broken session store must never block the
        // password-reset response itself.
    }
}
