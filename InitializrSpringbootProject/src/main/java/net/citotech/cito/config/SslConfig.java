package net.citotech.cito.config;

import net.citotech.cito.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Propagates runtime configuration properties to {@link Common} at startup.
 *
 * <ul>
 *   <li>{@code custom.ssl.skip-verify} – skip SSL certificate verification for
 *       outbound HTTPS calls (development / sandbox only).</li>
 *   <li>{@code app.base.url} – base URL used in outbound email links.</li>
 * </ul>
 */
@Configuration
public class SslConfig {

    @Value("${custom.ssl.skip-verify:false}")
    private boolean skipVerify;

    @Value("${app.base.url:}")
    private String appBaseUrl;

    @PostConstruct
    public void configure() {
        Common.setSslSkipVerify(skipVerify);
        Common.setAppBaseUrl(appBaseUrl);
    }
}
