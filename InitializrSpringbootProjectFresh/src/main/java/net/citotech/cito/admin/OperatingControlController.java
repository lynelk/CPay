package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/operating-controls")
@PreAuthorize("hasRole('ADMIN')")
public class OperatingControlController {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OperatingControlController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openHigh", count("HIGH"));
        result.put("openMedium", count("MEDIUM"));
        result.put("openLow", count("LOW"));
        result.put("totalOpen", totalOpen());
        return result;
    }

    private Integer count(String severity) {
        String sql = "SELECT COUNT(*) FROM operating_control_events WHERE event_status='OPEN' AND severity=:severity";
        Integer value = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("severity", severity), Integer.class);
        return value == null ? 0 : value;
    }

    private Integer totalOpen() {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operating_control_events WHERE event_status='OPEN'", new MapSqlParameterSource(), Integer.class);
        return value == null ? 0 : value;
    }
}

