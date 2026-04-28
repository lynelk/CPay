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
 * OpenAPI 3 documentation configuration.
 *
 * <p>The interactive Swagger UI is available at {@code /swagger-ui.html} when
 * the application is running.  The raw OpenAPI JSON is served at
 * {@code /v3/api-docs}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cpayOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CPay Payment Gateway API")
                        .description(
                                "REST API for CPay – a multi-gateway mobile money payment platform "
                                + "supporting MTN MoMo, Airtel Money, Safaricom M-Pesa, and SMS.")
                        .version("v1")
                        .contact(new Contact()
                                .name("CPay Support")
                                .email("support@cpay.example.com"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://cpay.example.com/license")))
                .servers(List.of(
                        new Server().url("/").description("Current server")));
    }
}
