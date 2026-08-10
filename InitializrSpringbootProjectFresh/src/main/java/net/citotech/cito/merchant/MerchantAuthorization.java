package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;

/** Shared role-only authorization boundary for merchant portal/self-service endpoints. */
public final class MerchantAuthorization {
    private MerchantAuthorization() {}

    public static MerchantUser requireUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        if (user.getMerchant_id() == null || user.getMerchant_id() <= 0) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user;
    }

    public static MerchantUser requireCapability(HttpServletRequest request, String capability) {
        MerchantUser user = requireUser(request);
        if (!user.merchantRole().capabilities().contains(capability)) {
            throw new PaymentGatewayException(
                    "Merchant role " + user.merchantRole().name() + " does not allow " + capability);
        }
        return user;
    }
}
