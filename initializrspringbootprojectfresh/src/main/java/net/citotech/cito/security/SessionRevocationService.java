package net.citotech.cito.security;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Revokes all active sessions belonging to a given admin or merchant user, keyed via Spring
 * Session's standard principal-name index (populated at login by {@code AuthenticationController}
 * setting the {@link FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME} session
 * attribute). Used on password change/reset so a stolen session cannot survive a credential
 * rotation (audit E4/P3: "revoke other sessions on password change").
 */
@Service
public class SessionRevocationService {
    private static final Logger LOGGER = Logger.getLogger(SessionRevocationService.class.getName());

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public static String adminPrincipal(long adminId) {
        return "admin-user:" + adminId;
    }

    public static String merchantUserPrincipal(long merchantUserId) {
        return "merchant-user:" + merchantUserId;
    }

    /** Revokes every active session for the given admin, e.g. after a password reset. */
    public void revokeAllForAdmin(long adminId) {
        revokeAllForPrincipal(adminPrincipal(adminId));
    }

    /** Revokes every active session for the given merchant user, e.g. after a password reset. */
    public void revokeAllForMerchantUser(long merchantUserId) {
        revokeAllForPrincipal(merchantUserPrincipal(merchantUserId));
    }

    private void revokeAllForPrincipal(String principalName) {
        try {
            Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalName);
            for (String sessionId : sessions.keySet()) {
                sessionRepository.deleteById(sessionId);
            }
        } catch (Exception e) {
            // Never let session cleanup block a password-reset response - log and move on.
            LOGGER.log(Level.SEVERE, "Failed to revoke sessions for " + principalName + ": " + e.getMessage(), e);
        }
    }
}
