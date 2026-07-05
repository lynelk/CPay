package net.citotech.cito;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
public class CpayadminApplication {

	public static void main(String[] args) {
		SpringApplication.run(CpayadminApplication.class, args);
	}
        
    
    @Bean
    WebMvcConfigurer configurer () {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(@org.springframework.lang.NonNull ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/portal")
                        .addResourceLocations("classpath:/static/portal.html");
            }
        };
    }

}
