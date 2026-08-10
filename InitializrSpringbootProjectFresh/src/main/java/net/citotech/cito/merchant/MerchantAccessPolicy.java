package net.citotech.cito.merchant;

import java.util.Set;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Single merchant-portal authorization policy.
 *
 * <p>Authentication is established once by the merchant HTTP session. Every merchant module then
 * derives authorization from the persisted {@link MerchantRole}; legacy per-user privilege rows
 * are intentionally not consulted.
 */
public final class MerchantAccessPolicy {
    private MerchantAccessPolicy() {}

    public static MerchantRole role(MerchantUser user) {
        if (user == null) {
            return MerchantRole.VIEWER;
        }
        return MerchantRole.fromString(user.getRole());
    }

    public static Set<String> capabilities(MerchantUser user) {
        return role(user).capabilities();
    }

    public static boolean canAccess(MerchantUser user, String capability) {
        return capability != null && capabilities(user).contains(capability);
    }

    public static void require(MerchantUser user, String capability) {
        if (user == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        if (!canAccess(user, capability)) {
            throw new PaymentGatewayException(
                    "Merchant role " + role(user).name() + " is not permitted to access " + capability);
        }
    }

    public static void requireManageUsers(MerchantUser user) {
        require(user, "ADMINISTRATION");
    }

    public static void requireMoveMoney(MerchantUser user) {
        require(user, "MOVE_MONEY");
    }

    public static void requireKyc(MerchantUser user) {
        require(user, "KYC_CUSTOMER_MGT");
    }

    public static void requireBilling(MerchantUser user) {
        require(user, "BILLING");
    }

    public static void requireCommunication(MerchantUser user) {
        require(user, "COMMUNICATION");
    }

    public static void requireIntegrations(MerchantUser user) {
        require(user, "DEVELOPERS_INTEGRATIONS");
    }

    public static void requireAudit(MerchantUser user) {
        require(user, "AUDIT");
    }
}
