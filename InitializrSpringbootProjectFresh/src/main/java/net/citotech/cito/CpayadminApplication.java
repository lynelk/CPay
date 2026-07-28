package net.citotech.cito;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class CpayadminApplication {

	public static void main(String[] args) {
		// Audit H1: route java.util.logging (still used directly by classes not yet converted to
		// SLF4J, plus some third-party libraries) through the same Logback pipeline/format as
		// everything else, instead of leaving it as a second, separately-configured log path.
		// Must run before anything logs via JUL, so this is the first line in main().
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		SpringApplication.run(CpayadminApplication.class, args);
	}
        
    
    @Bean
    WebMvcConfigurer configurer () {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/portal")
                        .addResourceLocations("classpath:/static/portal.html");
            }
        };
    }

}

