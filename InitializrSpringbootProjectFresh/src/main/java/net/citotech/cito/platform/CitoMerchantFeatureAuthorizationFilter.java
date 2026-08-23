package net.citotech.cito.platform;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Central entitlement guard for merchant-facing Cito feature modules. */
@Component
public class CitoMerchantFeatureAuthorizationFilter extends OncePerRequestFilter {
    private static final Map<String, String> FEATURE_PREFIXES = featurePrefixes();

    private final CitoFeatureAccessService featureAccessService;

    public CitoMerchantFeatureAuthorizationFilter(CitoFeatureAccessService featureAccessService) {
        this.featureAccessService = featureAccessService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String serviceCode = serviceFor(request.getRequestURI());
        if (serviceCode == null) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)
                || user.getMerchant_id() == null) {
            writeError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "MERCHANT_SESSION_REQUIRED",
                    "Merchant login is required");
            return;
        }

        String environment = request.getHeader("X-CPay-Environment");
        if (environment == null || environment.isBlank()) {
            environment = request.getParameter("environment");
        }
        try {
            featureAccessService.require(user.getMerchant_id(), serviceCode, environment);
            filterChain.doFilter(request, response);
        } catch (PaymentGatewayException e) {
            writeError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "CITO_SERVICE_NOT_ENTITLED",
                    e.getMessage());
        }
    }

    private String serviceFor(String path) {
        if (path == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : FEATURE_PREFIXES.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter()
                .write(
                        "{\"code\":\""
                                + escape(code)
                                + "\",\"message\":\""
                                + escape(message)
                                + "\"}");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> featurePrefixes() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("/api/v2/merchant-self-service/routing", "INTELLIGENT_ROUTING");
        map.put("/api/v2/merchant-self-service/marketplace", "MARKETPLACE_PAYMENTS");
        map.put("/api/v2/merchant-self-service/refunds", "REFUND_OPERATIONS");
        map.put("/api/v2/merchant-self-service/disputes", "REFUND_OPERATIONS");
        map.put("/api/v2/merchant-self-service/recurring", "RECURRING_PAYMENTS");
        map.put("/api/v2/merchant-self-service/analytics", "MERCHANT_ANALYTICS");
        map.put("/api/v2/merchant-self-service/developer", "DEVELOPER_CONTROL_PLANE");
        map.put("/api/v2/merchant-self-service/virtual-accounts", "VIRTUAL_ACCOUNTS");
        map.put("/api/v2/merchant-self-service/embedded", "EMBEDDED_CITO");
        map.put("/api/v2/merchant-self-service/integrations", "INTEGRATIONS_MARKETPLACE");
        return Map.copyOf(map);
    }
}
