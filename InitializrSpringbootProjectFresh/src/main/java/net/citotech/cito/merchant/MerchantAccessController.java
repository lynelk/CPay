package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authorization profile for the one currently authenticated merchant session.
 *
 * <p>This endpoint never authenticates a module independently. It describes the role/capabilities
 * already attached to the server-side merchant session so the SPA can expose every module the
 * signed-in user is entitled to use.
 */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/access")
public class MerchantAccessController {

    @GetMapping
    public ResponseEntity<?> access(HttpServletRequest request) {
        try {
            MerchantUser user = MerchantAuthorization.requireUser(request);
            MerchantRole role = user.merchantRole();
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("code", "000");
            profile.put("role", role.name());
            profile.put("capabilities", role.capabilities());
            profile.put("merchantNumber", user.getMerchant_number());
            profile.put("merchantName", user.getMerchant_name());
            profile.put("userId", user.getId());
            profile.put("email", user.getEmail());
            return ResponseEntity.ok(profile);
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "MERCHANT_SESSION_REQUIRED", "message", ex.getMessage()));
        }
    }
}
