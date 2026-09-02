package net.citotech.cito.experience;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ExperienceAccessContext {
    public Access require(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") instanceof User user) {
            return new Access(actor(user), null, true, "ADMIN");
        }
        if (session != null && session.getAttribute("merchantUser") instanceof MerchantUser user) {
            if (user.getMerchant_id() == null) {
                throw unauthorized();
            }
            return new Access(actor(user), user.getMerchant_id(), false, merchantRole(user));
        }
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_ADMIN"::equals)) {
            return new Access(authentication.getName(), null, true, "ADMIN_API");
        }
        throw unauthorized();
    }

    public void requireMerchantScope(Access access, long merchantId) {
        if (!access.admin() && !Long.valueOf(merchantId).equals(access.merchantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access denied");
        }
    }

    public void requireAdmin(Access access) {
        if (!access.admin()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Administrator access required");
        }
    }

    private String actor(User user) {
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim().toLowerCase();
        }
        return "user-" + user.getId();
    }

    private String merchantRole(MerchantUser user) {
        return user.getRole() == null || user.getRole().isBlank()
                ? "OWNER"
                : user.getRole().trim().toUpperCase();
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Portal login is required");
    }

    public record Access(String actor, Long merchantId, boolean admin, String role) {}
}
