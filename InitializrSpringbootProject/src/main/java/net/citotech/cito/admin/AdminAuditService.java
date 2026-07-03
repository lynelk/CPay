package net.citotech.cito.admin;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuditService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminAuditService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String permissionCode, String actionName, String resourceReference, String requestSummary) {
        String actor = currentActor();
        String sql = "INSERT INTO admin_audit_events (actor, permission_code, action_name, resource_reference, request_summary) VALUES (:actor, :permission_code, :action_name, :resource_reference, :request_summary)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("actor", actor);
        p.addValue("permission_code", permissionCode);
        p.addValue("action_name", actionName);
        p.addValue("resource_reference", resourceReference);
        p.addValue("request_summary", requestSummary);
        jdbcTemplate.update(sql, p);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "system";
        }
        return authentication.getName();
    }
}
