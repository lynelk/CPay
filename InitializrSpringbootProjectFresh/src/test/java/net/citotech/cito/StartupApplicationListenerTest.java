package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class StartupApplicationListenerTest {

    @Test
    void updateDbIsDisabledUnlessExplicitlyEnabled() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        StartupApplicationListener listener = listener(jdbcTemplate, false, 30);

        assertThat(listener.updateDb()).isEqualTo("Disabled");

        verify(jdbcTemplate, never()).queryForObject(any(String.class), any(MapSqlParameterSource.class), any(Class.class));
    }

    @Test
    void updateDbSkipsWhenAnotherInstanceHoldsTheMigrationLock() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(contains("GET_LOCK"), any(MapSqlParameterSource.class), any(Class.class)))
            .thenReturn(0);
        StartupApplicationListener listener = listener(jdbcTemplate, true, 30);

        assertThat(listener.updateDb()).isEqualTo("Locked");

        verify(jdbcTemplate).queryForObject(contains("GET_LOCK"), any(MapSqlParameterSource.class), any(Class.class));
        verify(jdbcTemplate, never()).queryForObject(contains("RELEASE_LOCK"), any(MapSqlParameterSource.class), any(Class.class));
    }

    @Test
    void updateDbReleasesTheMigrationLockAfterAnEmptyRun() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(contains("GET_LOCK"), any(MapSqlParameterSource.class), any(Class.class)))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("RELEASE_LOCK"), any(MapSqlParameterSource.class), any(Class.class)))
            .thenReturn(1);
        StartupApplicationListener listener = listener(jdbcTemplate, true, 30);

        assertThat(listener.updateDb()).isEqualTo("Skipped");

        verify(jdbcTemplate).queryForObject(contains("GET_LOCK"), any(MapSqlParameterSource.class), any(Class.class));
        verify(jdbcTemplate).queryForObject(contains("RELEASE_LOCK"), any(MapSqlParameterSource.class), any(Class.class));
    }

    private StartupApplicationListener listener(NamedParameterJdbcTemplate jdbcTemplate,
            boolean enabled,
            int timeoutSeconds) {
        StartupApplicationListener listener = new StartupApplicationListener();
        ReflectionTestUtils.setField(listener, "jdbcTemplate", jdbcTemplate);
        ReflectionTestUtils.setField(listener, "legacyDbChangesEnabled", enabled);
        ReflectionTestUtils.setField(listener, "legacyDbChangesLockTimeoutSeconds", timeoutSeconds);
        ReflectionTestUtils.setField(listener, "resources", new Resource[0]);
        return listener;
    }
}
