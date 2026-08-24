package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordUtilsTest {

    @Test
    void newPasswordsUseCurrentBcryptCostAndVerify() {
        String hash = PasswordUtils.hashPassword("a-long-test-password");

        assertThat(hash).contains("$12$");
        assertThat(PasswordUtils.verifyPassword("a-long-test-password", hash)).isTrue();
        assertThat(PasswordUtils.verifyPassword("wrong-password", hash)).isFalse();
    }

    @Test
    void legacySha256PasswordsRemainVerifiableAndNeedUpgrade() {
        String legacyHash = PasswordUtils.sha256Hex("legacy-password");

        assertThat(PasswordUtils.verifyPassword("legacy-password", legacyHash)).isTrue();
        assertThat(PasswordUtils.isLegacyHash(legacyHash)).isTrue();
        assertThat(PasswordUtils.needsRehash(legacyHash)).isTrue();
    }

    @Test
    void lowerCostBcryptHashesAreRecognizedForFutureRehash() {
        String oldHash = new BCryptPasswordEncoder(10).encode("existing-password");

        assertThat(PasswordUtils.verifyPassword("existing-password", oldHash)).isTrue();
        assertThat(PasswordUtils.needsRehash(oldHash)).isTrue();
    }

    @Test
    void blankPasswordsAreNotHashable() {
        assertThatThrownBy(() -> PasswordUtils.hashPassword("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
