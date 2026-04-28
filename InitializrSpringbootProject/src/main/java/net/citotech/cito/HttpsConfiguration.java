/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.citotech.cito;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional HTTP→HTTPS redirect configuration.
 *
 * <p>Enable by setting {@code server.ssl.enabled=true} in application.properties
 * (or via the {@code SERVER_SSL_ENABLED} environment variable).
 * The plain HTTP connector listens on {@code server.httpport} and redirects
 * every request to the HTTPS port ({@code server.port}).
 *
 * <p>Prerequisites:
 * <ol>
 *   <li>Uncomment the {@code server.ssl.*} properties in application.properties</li>
 *   <li>Provide a valid PKCS12 keystore (e.g. from Let's Encrypt via certbot)</li>
 *   <li>Set {@code server.ssl.enabled=true}</li>
 * </ol>
 */
@Configuration
public class HttpsConfiguration {

    @Bean
    @ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        tomcat.addAdditionalTomcatConnectors(redirectConnector());
        return tomcat;
    }

    @Value("${server.httpport:9000}")
    private int httpPort;

    @Value("${server.port:443}")
    private int httpsPort;

    private Connector redirectConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }
}
