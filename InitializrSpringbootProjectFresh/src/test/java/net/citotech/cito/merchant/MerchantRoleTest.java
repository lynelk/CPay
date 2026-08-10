package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers the canonical merchant role capability matrix and maximum-authority parsing contract. */
class MerchantRoleTest {

    @Test
    void ownerHasMaximumAccountAuthority() {
        assertThat(MerchantRole.MAXIMUM_ACCOUNT_AUTHORITY).isEqualTo(MerchantRole.OWNER);
        assertThat(MerchantRole.OWNER.canManageUsers()).isTrue();
        assertThat(MerchantRole.OWNER.canManageChannels()).isTrue();
        assertThat(MerchantRole.OWNER.canInitiatePayouts()).isTrue();
        assertThat(MerchantRole.OWNER.canViewStatements()).isTrue();
        assertThat(MerchantRole.OWNER.canAccessKyc()).isTrue();
        assertThat(MerchantRole.OWNER.canViewBilling()).isTrue();
        assertThat(MerchantRole.OWNER.canUseCommunication()).isTrue();
        assertThat(MerchantRole.OWNER.capabilities())
                .contains(
                        "HOME",
                        "PAYMENTS_TRANSACTIONS",
                        "MOVE_MONEY",
                        "KYC_CUSTOMER_MGT",
                        "BILLING",
                        "COMMUNICATION",
                        "DEVELOPERS_INTEGRATIONS",
                        "ADMINISTRATION",
                        "AUDIT");
    }

    @Test
    void financeCanMoveMoneyAndViewBillingButNotManageUsersOrChannels() {
        assertThat(MerchantRole.FINANCE.canManageUsers()).isFalse();
        assertThat(MerchantRole.FINANCE.canManageChannels()).isFalse();
        assertThat(MerchantRole.FINANCE.canInitiatePayouts()).isTrue();
        assertThat(MerchantRole.FINANCE.canViewStatements()).isTrue();
        assertThat(MerchantRole.FINANCE.canViewBilling()).isTrue();
        assertThat(MerchantRole.FINANCE.canUseCommunication()).isFalse();
        assertThat(MerchantRole.FINANCE.capabilities())
                .containsExactlyInAnyOrder("HOME", "PAYMENTS_TRANSACTIONS", "MOVE_MONEY", "BILLING");
    }

    @Test
    void developerCanManageChannelsAndCommunicationButNotMoveMoneyOrManageUsers() {
        assertThat(MerchantRole.DEVELOPER.canManageUsers()).isFalse();
        assertThat(MerchantRole.DEVELOPER.canManageChannels()).isTrue();
        assertThat(MerchantRole.DEVELOPER.canUseCommunication()).isTrue();
        assertThat(MerchantRole.DEVELOPER.canInitiatePayouts()).isFalse();
        assertThat(MerchantRole.DEVELOPER.canViewStatements()).isTrue();
        assertThat(MerchantRole.DEVELOPER.capabilities())
                .containsExactlyInAnyOrder(
                        "HOME", "PAYMENTS_TRANSACTIONS", "COMMUNICATION", "DEVELOPERS_INTEGRATIONS");
    }

    @Test
    void viewerIsReadOnly() {
        assertThat(MerchantRole.VIEWER.canManageUsers()).isFalse();
        assertThat(MerchantRole.VIEWER.canManageChannels()).isFalse();
        assertThat(MerchantRole.VIEWER.canInitiatePayouts()).isFalse();
        assertThat(MerchantRole.VIEWER.canViewStatements()).isTrue();
        assertThat(MerchantRole.VIEWER.capabilities())
                .containsExactlyInAnyOrder("HOME", "PAYMENTS_TRANSACTIONS");
    }

    @Test
    void fromStringParsesValidValuesCaseInsensitivelyAndTrimmed() {
        assertThat(MerchantRole.fromString("FINANCE")).isEqualTo(MerchantRole.FINANCE);
        assertThat(MerchantRole.fromString("developer")).isEqualTo(MerchantRole.DEVELOPER);
        assertThat(MerchantRole.fromString("  Viewer  ")).isEqualTo(MerchantRole.VIEWER);
        assertThat(MerchantRole.fromString("Owner")).isEqualTo(MerchantRole.OWNER);
    }

    @Test
    void fromStringUsesMaximumAccountAuthorityForNullBlankOrUnknownValues() {
        assertThat(MerchantRole.fromString(null)).isEqualTo(MerchantRole.OWNER);
        assertThat(MerchantRole.fromString("")).isEqualTo(MerchantRole.OWNER);
        assertThat(MerchantRole.fromString("   ")).isEqualTo(MerchantRole.OWNER);
        assertThat(MerchantRole.fromString("SUPERADMIN")).isEqualTo(MerchantRole.OWNER);
        assertThat(MerchantRole.fromString("not-a-role")).isEqualTo(MerchantRole.OWNER);
    }
}
