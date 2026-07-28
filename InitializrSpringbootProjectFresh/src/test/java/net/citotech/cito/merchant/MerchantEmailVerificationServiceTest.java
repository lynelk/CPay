package net.citotech.cito.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit P4: signup lets anyone create a merchant_admins row with any email address, so
 * login must stay blocked until a real, unexpired, single-use code confirms the address - not a
 * bare code match, and not a code that's already been consumed or has expired.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class MerchantEmailVerificationServiceTest {

    @Test
    void sendVerificationEmailInvalidatesAnyPriorTokenThenInsertsAFreshOne() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        service.sendVerificationEmail(7L, "merchant@example.com", "Jane");

        verify(jdbcTemplate).update(contains("SET consumed_at=CURRENT_TIMESTAMP"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).update(contains("INSERT INTO merchant_email_verification_tokens"), any(MapSqlParameterSource.class));
    }

    @Test
    void verifyAcceptsAMatchingUnexpiredUnconsumedCode() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn((List) List.of(101L));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        assertThat(service.verify(7L, "123456")).isTrue();
        verify(jdbcTemplate).update(contains("email_verified_at=CURRENT_TIMESTAMP"), any(MapSqlParameterSource.class));
    }

    @Test
    void verifyRejectsAnUnknownExpiredOrAlreadyConsumedCode() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        assertThat(service.verify(7L, "000000")).isFalse();
        verify(jdbcTemplate, never()).update(contains("email_verified_at"), any(MapSqlParameterSource.class));
    }

    @Test
    void verifyRejectsABlankCodeWithoutQueryingTheStore() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        assertThat(service.verify(7L, "  ")).isFalse();
        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void verifyByMerchantNumberAndEmailRejectsAnUnknownAccount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("FROM merchant_admins"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        assertThatThrownBy(() -> service.verifyByMerchantNumberAndEmail("1000003", "nobody@example.com", "123456"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void resendVerificationEmailRejectsBlankInputs() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantEmailVerificationService service = new MerchantEmailVerificationService(jdbcTemplate);

        assertThatThrownBy(() -> service.resendVerificationEmail("", "merchant@example.com"))
            .isInstanceOf(PaymentGatewayException.class);
    }
}
