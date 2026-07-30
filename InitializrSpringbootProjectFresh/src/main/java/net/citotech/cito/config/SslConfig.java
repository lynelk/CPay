package net.citotech.cito.config;

import jakarta.annotation.PostConstruct;
import net.citotech.cito.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Propagates runtime configuration properties to {@link Common} at startup.
 *
 * <ul>
 *   <li>{@code app.base.url} – base URL used in outbound email links.</li>
 *   <li>{@code cpay.security.trusted-proxy-ips} – reverse proxy/load balancer IP(s) whose
 *       X-Forwarded-For/X-Real-IP headers may be trusted for client IP resolution.</li>
 * </ul>
 */
@Configuration
public class SslConfig {

    @Value("${app.base.url:}")
    private String appBaseUrl;

    @Value("${cpay.security.trusted-proxy-ips:}")
    private String trustedProxyIps;

    @PostConstruct
    public void configure() {
        Common.setAppBaseUrl(appBaseUrl);
        Common.setTrustedProxyIps(trustedProxyIps);
    }
}

