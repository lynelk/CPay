package net.citotech.cito.config;

import net.citotech.cito.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Propagates the {@code custom.ssl.skip-verify} property to {@link Common}
 * so that outbound HTTPS requests can be made without certificate verification
 * in development / sandbox environments.
 *
 * <p>This flag must be {@code false} (the default) in production.
 */
@Configuration
public class SslConfig {

    @Value("${custom.ssl.skip-verify:false}")
    private boolean skipVerify;

    @PostConstruct
    public void configure() {
        Common.setSslSkipVerify(skipVerify);
    }
}
