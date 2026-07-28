package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AdminMfaServiceTest {

    @Test
    void reportsEnabledWhenAnActiveTotpRowExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM admin_mfa_totp WHERE admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenReturn(1);

        AdminMfaService service = new AdminMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThat(service.isEnabled(22L)).isTrue();
    }

    @Test
    void reportsDisabledWhenNoEnrollmentExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM admin_mfa_totp WHERE admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenReturn(0);

        AdminMfaService service = new AdminMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThat(service.isEnabled(22L)).isFalse();
    }

    @Test
    void missingMfaTableFailsClosedInsteadOfBypassingMfa() {
        // A missing admin_mfa_totp table must never be read as "MFA disabled" - that would let
        // an attacker (or a botched deployment) silently skip the second factor at login.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM admin_mfa_totp WHERE admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenThrow(new BadSqlGrammarException(
                "query",
                "SELECT COUNT(*) FROM admin_mfa_totp",
                new SQLException("Table 'cpayadmin.admin_mfa_totp' doesn't exist", "42S02", 1146)));

        AdminMfaService service = new AdminMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThatThrownBy(() -> service.isEnabled(22L)).isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void unexpectedSqlGrammarErrorsAlsoFailClosed() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BadSqlGrammarException failure = new BadSqlGrammarException(
            "query",
            "SELECT COUNT(*) FROM admin_mfa_totp",
            new SQLException("Unknown column 'enabled_flag'", "42S22", 1054));
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM admin_mfa_totp WHERE admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenThrow(failure);

        AdminMfaService service = new AdminMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThatThrownBy(() -> service.isEnabled(22L))
            .isInstanceOf(PaymentGatewayException.class)
            .hasCause(failure);
    }
}
