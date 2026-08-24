package net.citotech.cito.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 documentation configuration for the Cito platform.
 *
 * <p>CPay is the payments module inside Cito, so payment-specific paths, signing headers and
 * integration contracts retain the CPay name while platform-level documentation is branded Cito.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Method name retained for compatibility with any internal bean-name references. The published
     * API identity is Cito.
     */
    @Bean
    public OpenAPI cpayOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Cito Platform API")
                                .description(
                                        "REST API for Cito, the multi-tenant commerce and service platform. "
                                                + "Payment collection, payout, refund, settlement and payment-provider "
                                                + "capabilities are exposed through the CPay payments module; existing "
                                                + "CPay payment signing headers and compatibility contracts are retained.")
                                .version("v2")
                                .contact(new Contact().name("Cito Support"))
                                .license(new License().name("Proprietary")))
                .servers(List.of(new Server().url("/").description("Current Cito server")));
    }
}
