package net.citotech.cito.security;

import java.util.Map;
import java.util.UUID;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/merchant/mfa")
public class MerchantMfaController {
    private final MerchantMfaService merchantMfaService;

    public MerchantMfaController(MerchantMfaService merchantMfaService) {
        this.merchantMfaService = merchantMfaService;
    }

    @PostMapping(path = "/enroll")
    public ResponseEntity<?> enroll(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(merchantMfaService.beginEnrollment(body.get("accountNumber"), body.get("email")));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "MFA_ENROLLMENT_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, String> body) {
        try {
            boolean confirmed = merchantMfaService.confirmEnrollment(
                body.get("accountNumber"),
                body.get("email"),
                body.get("code"));
            if (!confirmed) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_MFA_CODE", "Invalid MFA code");
            }
            return ResponseEntity.ok(Map.of("code", "000", "message", "MFA enabled"));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "MFA_CONFIRMATION_REJECTED", e.getMessage());
        }
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
