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

class MerchantMfaServiceTest {

    @Test
    void reportsEnabledWhenAnActiveTotpRowExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM merchant_mfa_totp WHERE merchant_admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenReturn(1);

        MerchantMfaService service = new MerchantMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThat(service.isEnabled(9L)).isTrue();
    }

    @Test
    void missingMfaTableFailsClosedInsteadOfBypassingMfa() {
        // A missing merchant_mfa_totp table must never be read as "MFA disabled" - that would
        // let a broken/missing table silently skip the second factor at merchant login.
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM merchant_mfa_totp WHERE merchant_admin_id=:admin_id AND enabled_flag='YES'"),
                any(MapSqlParameterSource.class),
                eq(Integer.class)))
            .thenThrow(new BadSqlGrammarException(
                "query",
                "SELECT COUNT(*) FROM merchant_mfa_totp",
                new SQLException("Table 'cpayadmin.merchant_mfa_totp' doesn't exist", "42S02", 1146)));

        MerchantMfaService service = new MerchantMfaService(
            jdbcTemplate,
            mock(TotpService.class),
            mock(MerchantChannelCryptoService.class));

        assertThatThrownBy(() -> service.isEnabled(9L)).isInstanceOf(PaymentGatewayException.class);
    }
}
