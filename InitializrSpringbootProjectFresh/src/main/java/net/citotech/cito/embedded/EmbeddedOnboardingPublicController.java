package net.citotech.cito.embedded;

import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/embedded/onboarding")
public class EmbeddedOnboardingPublicController {
    private final EmbeddedCitoService embeddedService;

    public EmbeddedOnboardingPublicController(EmbeddedCitoService embeddedService) {
        this.embeddedService = embeddedService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<?> resolve(@PathVariable String token) {
        try {
            return ResponseEntity.ok(embeddedService.resolvePublicSession(token));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("code", "EMBEDDED_SESSION_NOT_FOUND", "message", "Embedded onboarding session is invalid or expired"));
        }
    }
}