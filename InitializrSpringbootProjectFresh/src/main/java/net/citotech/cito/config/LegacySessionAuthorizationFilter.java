package net.citotech.cito.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.citotech.cito.GeneralException;
import net.citotech.cito.Model.MerchantUser;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LegacySessionAuthorizationFilter extends OncePerRequestFilter {
    private static final String MERCHANT_SELF_SERVICE_PREFIX = "/api/v2/merchant-self-service";

    private static final List<String> PORTAL_SESSION_PREFIXES = List.of(
        "/admins",
        "/audittrail",
        "/merchants",
        "/settings",
        "/transactions",
        MERCHANT_SELF_SERVICE_PREFIX,
        "/api/v2/portal"
    );

    private static final List<String> PUBLIC_SETTINGS_PATHS = List.of(
        "/settings/public-login-appearance"
    );

    private static final List<String> PUBLIC_MERCHANT_SELF_SERVICE_PATHS = List.of(
        "/api/v2/merchant-self-service/signup",
        "/api/v2/merchant-self-service/verify-email",
        "/api/v2/merchant-self-service/verify-email/resend"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!requiresPortalSession(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        String path = request.getRequestURI();

        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX)) {
            if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser merchantUser)) {
                unauthorized(response);
                return;
            }
            if (!merchantModuleAllowed(path, merchantUser)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        GeneralException.getError("110", "Merchant role does not allow access to this module."));
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        boolean loggedIn = session != null
            && (session.getAttribute("user") != null || session.getAttribute("merchantUser") != null);
        if (loggedIn) {
            filterChain.doFilter(request, response);
            return;
        }

        unauthorized(response);
    }

    boolean requiresPortalSession(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (HttpMethod.GET.matches(request.getMethod()) && PUBLIC_SETTINGS_PATHS.contains(path)) {
            return false;
        }
        if (PUBLIC_MERCHANT_SELF_SERVICE_PATHS.contains(path)) {
            return false;
        }
        return PORTAL_SESSION_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean merchantModuleAllowed(String path, MerchantUser user) {
        if (path.equals(MERCHANT_SELF_SERVICE_PREFIX + "/access")) return true;
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/billing")) {
            return user.merchantRole().canViewBilling();
        }
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/kyc")) {
            return user.merchantRole().canAccessKyc();
        }
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/communication")
                || path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/notification-preferences")) {
            return user.merchantRole().canUseCommunication();
        }
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/channels")
                || path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/webhooks")
                || path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/environment")
                || path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/sandbox-guide")) {
            return user.merchantRole().canManageChannels();
        }
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/settlement-preference")) {
            return user.merchantRole().canInitiatePayouts();
        }
        if (path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/statements")
                || path.startsWith(MERCHANT_SELF_SERVICE_PREFIX + "/batches")) {
            return user.merchantRole().canViewPaymentsAndTransactions();
        }
        // Any newly introduced merchant self-service route is denied until it is explicitly mapped.
        return false;
    }

    private void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(GeneralException.getError("107", GeneralException.ERRORS_107));
    }
}
