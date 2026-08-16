package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0 §2: maker-checker approval-request endpoints. A privileged action listed in {@code
 * admin_access_matrix} as requiring maker-checker is recorded here by the maker (POST create),
 * then decided by a checker (approve/reject). The checker must differ from the maker; every
 * transition is fully audited via {@link AdminApprovalService}.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/approval-requests")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApprovalController {
    private final AdminApprovalService approvalService;

    public AdminApprovalController(AdminApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> payload) {
        long requestId =
                approvalService.create(
                        required(payload, "approvalType"),
                        required(payload, "resourceType"),
                        required(payload, "resourceId"),
                        object(payload, "payload"),
                        string(payload, "previousStateHash", null),
                        string(payload, "newStateHash", null),
                        string(payload, "requestId", null),
                        string(payload, "expiresInHours", null));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("requestStatus", "PENDING_APPROVAL");
        return result;
    }

    @GetMapping
    public List<Map<String, Object>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "approvalType", required = false) String approvalType,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return approvalService.list(status, approvalType, limit);
    }

    @GetMapping(path = "/{requestId}")
    public Map<String, Object> get(@PathVariable long requestId) {
        return approvalService.get(requestId);
    }

    @PostMapping(path = "/{requestId}/approve")
    public Map<String, Object> approve(
            @PathVariable long requestId, @RequestBody Map<String, Object> payload) {
        return approvalService.approve(
                requestId,
                string(payload, "approvedBy", null),
                string(payload, "note", null));
    }

    @PostMapping(path = "/{requestId}/reject")
    public Map<String, Object> reject(
            @PathVariable long requestId, @RequestBody Map<String, Object> payload) {
        return approvalService.reject(
                requestId,
                string(payload, "rejectedBy", null),
                required(payload, "reason"));
    }

    private static String required(Map<String, Object> payload, String key) {
        String value = string(payload, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String string(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? defaultValue : value.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
}
