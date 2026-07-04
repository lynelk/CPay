package net.citotech.cito.merchant;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.SimpleRateLimitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/merchant-self-service")
public class MerchantSelfServiceController {
    private final MerchantSelfServiceSignupService signupService;
    private final MerchantChannelCredentialService channelService;
    private final SimpleRateLimitService rateLimitService;

    public MerchantSelfServiceController(MerchantSelfServiceSignupService signupService,
                                         MerchantChannelCredentialService channelService,
                                         SimpleRateLimitService rateLimitService) {
        this.signupService = signupService;
        this.channelService = channelService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping(path = "/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            if (!rateLimitService.allow("merchant-signup:" + clientIp(request), 5)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("RATE_LIMITED", "Too many registration attempts. Please try again later."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("SIGNUP_REJECTED", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(error("SIGNUP_FAILED", "Unable to complete merchant signup"));
        }
    }

    @GetMapping(path = "/channels")
    public ResponseEntity<?> channels(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.list(currentMerchantUser(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/save")
    public ResponseEntity<?> saveChannel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.save(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("CHANNEL_SAVE_REJECTED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/test")
    public ResponseEntity<?> testChannel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.test(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("CHANNEL_TEST_REJECTED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/submit")
    public ResponseEntity<?> submitChannel(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.submitForApproval(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("CHANNEL_SUBMIT_REJECTED", e.getMessage()));
        }
    }

    private MerchantUser currentMerchantUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("merchantUser") == null) throw new PaymentGatewayException("Merchant login is required");
        return (MerchantUser) session.getAttribute("merchantUser");
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
