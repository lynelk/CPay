package net.citotech.cito.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

@Configuration
@EnableJdbcHttpSession(tableName = "SPRING_SESSION")
public class SpringSessionConfig {
}
