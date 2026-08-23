package net.citotech.cito.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Component;

@Component
public class MerchantSessionContext {
    public MerchantUser requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user;
    }

    public long requireMerchantId(HttpServletRequest request) {
        MerchantUser user = requireUser(request);
        if (user.getMerchant_id() == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user.getMerchant_id();
    }

    public String actor(HttpServletRequest request) {
        MerchantUser user = requireUser(request);
        String email = user.getEmail();
        return email == null || email.isBlank() ? "merchant-user" : email.trim().toLowerCase();
    }
}