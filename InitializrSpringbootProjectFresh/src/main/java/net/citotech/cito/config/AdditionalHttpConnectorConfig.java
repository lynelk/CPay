package net.citotech.cito.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds an optional second plain-HTTP connector.
 *
 * <p>This is primarily useful on platforms that probe one discovered container port while a public
 * route is intentionally bound to another port. It is disabled unless
 * {@code server.additional-http-port} is configured, so normal production behavior is unchanged.
 */
@Configuration
public class AdditionalHttpConnectorConfig {

    @Bean
    @ConditionalOnProperty(name = "server.additional-http-port")
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalHttpConnector(
            @Value("${server.additional-http-port}") int port) {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("http");
            connector.setPort(port);
            connector.setSecure(false);
            factory.addAdditionalConnectors(connector);
        };
    }
}
