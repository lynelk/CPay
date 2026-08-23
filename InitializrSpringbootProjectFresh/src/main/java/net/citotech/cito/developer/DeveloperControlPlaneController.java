package net.citotech.cito.developer;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/developer")
public class DeveloperControlPlaneController {
    private final DeveloperControlPlaneService developerService;
    private final MerchantSessionContext sessionContext;

    public DeveloperControlPlaneController(
            DeveloperControlPlaneService developerService, MerchantSessionContext sessionContext) {
        this.developerService = developerService;
        this.sessionContext = sessionContext;
    }

    @PostMapping("/projects")
    public ResponseEntity<?> createProject(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.createProject(
                            sessionContext.requireMerchantId(request),
                            text(body.get("projectName")),
                            text(body.get("description")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("DEVELOPER_PROJECT_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<?> projects(HttpServletRequest request) {
        return ResponseEntity.ok(developerService.projects(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/environments/activate")
    public ResponseEntity<?> activateEnvironment(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.activateEnvironment(
                            sessionContext.requireMerchantId(request),
                            text(body.get("projectReference")),
                            text(body.get("environment")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("DEVELOPER_ENVIRONMENT_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/service-accounts")
    public ResponseEntity<?> createServiceAccount(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.createServiceAccount(
                            sessionContext.requireMerchantId(request),
                            text(body.get("projectReference")),
                            text(body.get("displayName")),
                            stringList(body.get("scopes")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("SERVICE_ACCOUNT_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/service-accounts")
    public ResponseEntity<?> serviceAccounts(
            @RequestParam("projectReference") String projectReference,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.serviceAccounts(
                            sessionContext.requireMerchantId(request), projectReference));
        } catch (PaymentGatewayException e) {
            return bad("DEVELOPER_PROJECT_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping("/credentials")
    public ResponseEntity<?> issueCredential(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.issueCredential(
                            sessionContext.requireMerchantId(request),
                            text(body.get("serviceAccountReference")),
                            instant(body.get("expiresAt"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("DEVELOPER_CREDENTIAL_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/credentials")
    public ResponseEntity<?> credentials(
            @RequestParam("serviceAccountReference") String serviceAccountReference,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.credentials(
                            sessionContext.requireMerchantId(request), serviceAccountReference));
        } catch (PaymentGatewayException e) {
            return bad("SERVICE_ACCOUNT_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping("/credentials/revoke")
    public ResponseEntity<?> revokeCredential(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            developerService.revokeCredential(
                    sessionContext.requireMerchantId(request),
                    text(body.get("credentialReference")));
            return ResponseEntity.ok(Map.of("revoked", true));
        } catch (PaymentGatewayException e) {
            return bad("CREDENTIAL_REVOCATION_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/test-events")
    public ResponseEntity<?> testEvent(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.createTestEvent(
                            sessionContext.requireMerchantId(request),
                            text(body.get("projectReference")),
                            text(body.get("eventType")),
                            text(body.get("payloadJson")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("TEST_EVENT_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/request-log")
    public ResponseEntity<?> requestLog(
            @RequestParam("projectReference") String projectReference,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.requestLog(
                            sessionContext.requireMerchantId(request), projectReference, limit));
        } catch (PaymentGatewayException e) {
            return bad("DEVELOPER_PROJECT_NOT_FOUND", e.getMessage());
        }
    }

    @GetMapping("/readiness")
    public ResponseEntity<?> readiness(
            @RequestParam("projectReference") String projectReference,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    developerService.readiness(
                            sessionContext.requireMerchantId(request), projectReference));
        } catch (PaymentGatewayException e) {
            return bad("DEVELOPER_READINESS_REJECTED", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new PaymentGatewayException("scopes must be an array");
        }
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new PaymentGatewayException("Every scope must be a string");
            }
        }
        return (List<String>) value;
    }

    private Instant instant(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("expiresAt must use ISO-8601 UTC format");
        }
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}