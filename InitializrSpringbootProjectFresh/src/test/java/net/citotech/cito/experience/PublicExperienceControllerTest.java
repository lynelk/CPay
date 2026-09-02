package net.citotech.cito.experience;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class PublicExperienceControllerTest {
    @Test
    void publicStatusDoesNotInventIncidentsOrProviders() {
        EmbeddedDatabase database =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .generateUniqueName(true)
                        .build();
        try {
            new JdbcTemplate(database)
                    .execute(
                            """
                            CREATE TABLE provider_incidents (
                              incident_reference VARCHAR(80), provider_code VARCHAR(80),
                              country_code VARCHAR(3), channel_code VARCHAR(80),
                              environment VARCHAR(20), severity VARCHAR(20), status VARCHAR(30),
                              public_title VARCHAR(240), public_message VARCHAR(1000),
                              started_at TIMESTAMP, updated_at TIMESTAMP)
                            """);
            PublicExperienceController controller =
                    new PublicExperienceController(new NamedParameterJdbcTemplate(database));

            Map<String, Object> status = controller.status();

            assertThat(status.get("status")).isEqualTo("OPERATIONAL");
            assertThat(status.get("activeIncidents")).isEqualTo(List.of());
        } finally {
            database.shutdown();
        }
    }

    @Test
    void analyticsIdentifiersAreOneWayHashed() {
        assertThat(ProductExperienceController.hash("session-123"))
                .hasSize(64)
                .doesNotContain("session-123");
    }
}
