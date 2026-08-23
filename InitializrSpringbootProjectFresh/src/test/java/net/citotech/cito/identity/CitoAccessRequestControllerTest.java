package net.citotech.cito.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import net.citotech.cito.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CitoAccessRequestControllerTest {

    @Mock private NamedParameterJdbcTemplate jdbcTemplate;
    @Mock private LoginRateLimiter rateLimiter;

    private CitoAccessRequestController controller;

    @BeforeEach
    void setUp() {
        controller = new CitoAccessRequestController(jdbcTemplate, rateLimiter);
    }

    @Test
    void validPrivilegedRequestIsPersistedAsPendingOnly() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("192.0.2.10");
        when(rateLimiter.tryConsume("amina@example.com", "192.0.2.10")).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);

        ResponseEntity<Map<String, Object>> response = controller.requestAccess(
                new CitoAccessRequestController.AccessRequest(
                        "Amina Example",
                        "Amina@Example.com",
                        "Example Ltd",
                        "operations",
                        "Approved support operations duties."),
                servletRequest);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("PENDING", response.getBody().get("status"));
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void arbitraryRoleCannotBeRequested() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        ResponseEntity<Map<String, Object>> response = controller.requestAccess(
                new CitoAccessRequestController.AccessRequest(
                        "Amina Example",
                        "amina@example.com",
                        "Example Ltd",
                        "SUPERUSER",
                        "Attempting unsupported privilege assignment."),
                servletRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(rateLimiter, never()).tryConsume(anyString(), anyString());
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void rateLimitStopsRepeatedPublicRequests() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("192.0.2.10");
        when(rateLimiter.tryConsume("amina@example.com", "192.0.2.10")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.requestAccess(
                new CitoAccessRequestController.AccessRequest(
                        "Amina Example",
                        "amina@example.com",
                        "Example Ltd",
                        "COMPLIANCE",
                        "Compliance review access is required."),
                servletRequest);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }
}
