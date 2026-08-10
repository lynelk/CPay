package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authorization profile for the currently authenticated merchant session.
 *
 * <p>The merchant authenticates once. Every portal module then relies on the same server-side
 * session and the same MerchantRole capability set; modules must never introduce their own login
 * ceremony or independent identity store.
 */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/access")
public class MerchantAccessController {

    @GetMapping
    public ResponseEntity<?> access(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "MERCHANT_SESSION_REQUIRED", "message", "Merchant login is required"));
        }

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
    }
}
