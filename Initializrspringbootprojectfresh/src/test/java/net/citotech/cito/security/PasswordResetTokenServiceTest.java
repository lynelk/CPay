package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PasswordResetTokenServiceTest {

    @Test
    void issuesUrlSafeSingleUseToken() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        PasswordResetTokenService service = new PasswordResetTokenService(jdbcTemplate);

        String token = service.issue("ADMIN", 7L, "admin@example.com", "127.0.0.1");

        assertThat(token).hasSizeGreaterThan(40);
        assertThat(token).doesNotContain("+", "/", "=");
        verify(jdbcTemplate, times(2)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void consumesMatchingUnexpiredToken() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(11L));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        PasswordResetTokenService service = new PasswordResetTokenService(jdbcTemplate);

        assertThat(service.consume("ADMIN", 7L, "admin@example.com", "token")).isTrue();
    }
}
