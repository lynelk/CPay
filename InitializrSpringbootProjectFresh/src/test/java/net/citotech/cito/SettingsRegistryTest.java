package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import net.citotech.cito.SettingsRegistry.Entry;
import net.citotech.cito.SettingsRegistry.SettingType;
import org.junit.jupiter.api.Test;

/**
 * Covers audit O5: the typed settings registry must be a fixed, documented set - every entry
 * needs a valid type, a default value that actually parses under that type, a description, and a
 * group - and a name that isn't registered must resolve to an empty lookup rather than the
 * registry silently guessing a type for it.
 */
class SettingsRegistryTest {

    @Test
    void knownSettingsResolveToTheirRegisteredMetadata() {
        Entry entry = SettingsRegistry.lookup("production_transaction_limit_count").orElseThrow();
        assertThat(entry.type()).isEqualTo(SettingType.INTEGER);
        assertThat(entry.defaultValue()).isEqualTo("10");
        assertThat(entry.group()).isEqualTo("merchant");
        assertThat(entry.description()).isNotBlank();

        Entry credsEntry = SettingsRegistry.lookup("use_merchant_provider_credentials").orElseThrow();
        assertThat(credsEntry.type()).isEqualTo(SettingType.BOOLEAN);
        assertThat(credsEntry.defaultValue()).isEqualTo("false");
    }

    @Test
    void unknownOrNullSettingNamesAreRejected() {
        assertThat(SettingsRegistry.isKnown("not_a_real_setting")).isFalse();
        assertThat(SettingsRegistry.isKnown(null)).isFalse();
        assertThat(SettingsRegistry.lookup("not_a_real_setting")).isEmpty();
        assertThat(SettingsRegistry.lookup(null)).isEmpty();
    }

    @Test
    void everyCatalogEntryHasCompleteMetadataAndADefaultThatParsesUnderItsOwnType() {
        for (Entry entry : SettingsRegistry.all()) {
            assertThat(entry.name()).isNotBlank();
            assertThat(entry.type()).isNotNull();
            assertThat(entry.defaultValue()).isNotNull();
            assertThat(entry.description()).isNotBlank();
            assertThat(entry.group()).isNotBlank();

            switch (entry.type()) {
                case BOOLEAN -> assertThat(entry.defaultValue()).isIn("true", "false");
                case INTEGER -> assertThatCode(() -> Integer.parseInt(entry.defaultValue()))
                    .as("default for '%s' must parse as an integer", entry.name())
                    .doesNotThrowAnyException();
                case DECIMAL -> assertThatCode(() -> new BigDecimal(entry.defaultValue()))
                    .as("default for '%s' must parse as a decimal", entry.name())
                    .doesNotThrowAnyException();
                case STRING -> { /* any string, including blank, is a valid default */ }
            }
        }
        assertThat(SettingsRegistry.all()).isNotEmpty();
    }

    @Test
    void registeredSettingNamesAreUnique() {
        long distinctCount = SettingsRegistry.all().stream().map(Entry::name).distinct().count();
        assertThat(distinctCount).isEqualTo(SettingsRegistry.all().size());
    }

    @Test
    void noRegisteredSettingLooksCredentialShaped() {
        // The real provider credentials in this codebase (gw_*_api_password, gw_*_api_pin,
        // gw_*_api_*_key, ...) are deliberately left out of this registry entirely so the admin
        // listing endpoint never has a secret to display in the first place. This pins that
        // invariant so nobody accidentally registers one later.
        for (Entry entry : SettingsRegistry.all()) {
            assertThat(SettingsRegistry.isSecretLike(entry.name()))
                .as("entry '%s' looks credential-shaped and should not be in this registry", entry.name())
                .isFalse();
        }
    }

    @Test
    void isSecretLikeFlagsObviouslyCredentialShapedNamesFromThisCodebase() {
        assertThat(SettingsRegistry.isSecretLike("gw_mtn_api_collections_user_key")).isTrue();
        assertThat(SettingsRegistry.isSecretLike("gw_mtn_api_disbursements_subscription_key")).isTrue();
        assertThat(SettingsRegistry.isSecretLike("gw_airtelmoney_api_password")).isTrue();
        assertThat(SettingsRegistry.isSecretLike("gw_airtelmoney_api_pin")).isTrue();
        assertThat(SettingsRegistry.isSecretLike("mail.smtp.password")).isTrue();
        assertThat(SettingsRegistry.isSecretLike(null)).isFalse();
        assertThat(SettingsRegistry.isSecretLike("float_stock_account")).isFalse();
    }
}
