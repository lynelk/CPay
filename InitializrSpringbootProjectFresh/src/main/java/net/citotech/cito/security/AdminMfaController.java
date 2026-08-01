package net.citotech.cito.security;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/mfa")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMfaController {
    private final AdminMfaService adminMfaService;

    public AdminMfaController(AdminMfaService adminMfaService) {
        this.adminMfaService = adminMfaService;
    }

    @PostMapping(path = "/totp/enroll")
    public Map<String, Object> enroll(@RequestParam("email") String email) {
        return adminMfaService.beginEnrollment(email);
    }

    @PostMapping(path = "/totp/confirm")
    public ResponseEntity<?> confirm(
            @RequestParam("email") String email, @RequestParam("code") String code) {
        return ResponseEntity.ok(
                Map.of("verified", adminMfaService.confirmEnrollment(email, code)));
    }
}
