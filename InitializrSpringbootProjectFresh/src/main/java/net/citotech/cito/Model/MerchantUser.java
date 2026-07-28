package net.citotech.cito.Model;

/**
 *
 * @author josephtabajjwa
 */
public class MerchantUser extends User implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    String merchant_number;
    String merchant_status;
    String merchant_name;
    String merchant_account_type;
    Long merchant_id;
    /**
     * Team role for this merchant user (audit N7) - raw stored value, see
     * {@code net.citotech.cito.merchant.MerchantRole#fromString(String)} for the fail-open
     * (defaults to OWNER) parsed capability check. Kept as a plain String here, matching how
     * other status-like fields on this class (merchant_status, status) are stored, rather than
     * embedding the enum type directly on this legacy model.
     */
    String role;
    /**
     * When this merchant user's email address was confirmed (audit P4), or null if not yet
     * verified. A null value blocks login - see AuthenticationController#authenticateMerchantUser.
     * Existing accounts are backfilled to their created_on time by the V26 migration so this gate
     * only affects accounts created after it shipped.
     */
    String email_verified_at;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

