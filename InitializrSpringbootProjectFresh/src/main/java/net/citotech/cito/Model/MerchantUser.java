package net.citotech.cito.Model;

import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.merchant.MerchantRole;

/** Merchant account user authenticated into the shared merchant portal session. */
public class MerchantUser extends User implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String merchant_number;
    String merchant_status;
    String merchant_name;
    String merchant_account_type;
    Long merchant_id;
    String role;
    String email_verified_at;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /** The canonical parsed role used for every merchant authorization decision. */
    public MerchantRole merchantRole() {
        return MerchantRole.fromString(role);
    }

    /**
     * Compatibility bridge for legacy controllers that still call the old privilege helper.
     *
     * <p>The returned values are synthesized exclusively from {@link MerchantRole}; persisted
     * merchant privilege rows are deliberately ignored. This lets old endpoints keep their public
     * behavior while MerchantRole remains the single merchant authorization source during the
     * controller migration.
     */
    @Override
    public List<UserPrivilege> getPrivileges() {
        MerchantRole merchantRole = merchantRole();
        List<UserPrivilege> privileges = new ArrayList<>();
        if (merchantRole.canViewPaymentsAndTransactions()) {
            addPrivilege(privileges, "ACCESS_TRANSACTION_LOG");
        }
        if (merchantRole.canInitiatePayouts()) {
            addPrivilege(privileges, "CREATE_BATCH_TX");
        }
        if (merchantRole.canUseCommunication()) {
            addPrivilege(privileges, "ACCESS_SMS_LOG");
            addPrivilege(privileges, "SEND_SMS");
        }
        if (merchantRole.canManageUsers()) {
            addPrivilege(privileges, "ACCESS_ADMIN");
            addPrivilege(privileges, "CREATE_ADMIN");
            addPrivilege(privileges, "UPDATE_ADMIN");
            addPrivilege(privileges, "DELETE_ADMIN");
        }
        if (merchantRole.canViewAudit()) {
            addPrivilege(privileges, "ACCESS_AUDITTRAIL");
        }
        return List.copyOf(privileges);
    }

    /** Persisted privilege lists are no longer an authorization input for merchant users. */
    @Override
    public void setPrivileges(List<UserPrivilege> ignored) {
        // Intentionally ignored. MerchantRole is the single source of merchant access authority.
    }

    private void addPrivilege(List<UserPrivilege> privileges, String value) {
        UserPrivilege privilege = new UserPrivilege();
        privilege.setPrivilege(value);
        privileges.add(privilege);
    }

    public String getEmail_verified_at() {
        return email_verified_at;
    }

    public void setEmail_verified_at(String email_verified_at) {
        this.email_verified_at = email_verified_at;
    }

    public String getMerchant_number() {
        return merchant_number;
    }

    public void setMerchant_number(String merchant_number) {
        this.merchant_number = merchant_number;
    }

    public String getMerchant_status() {
        return merchant_status;
    }

    public void setMerchant_status(String merchant_status) {
        this.merchant_status = merchant_status;
    }

    public String getMerchant_name() {
        return merchant_name;
    }

    public void setMerchant_name(String merchant_name) {
        this.merchant_name = merchant_name;
    }

    public String getMerchant_account_type() {
        return merchant_account_type;
    }

    public void setMerchant_account_type(String merchant_account_type) {
        this.merchant_account_type = merchant_account_type;
    }

    public Long getMerchant_id() {
        return merchant_id;
    }

    public void setMerchant_id(Long merchant_id) {
        this.merchant_id = merchant_id;
    }
}
