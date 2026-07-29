package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.Model.Payment;
import net.citotech.cito.security.MerchantMfaService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit E2: a payout batch above the configured threshold must require a fresh MFA code
 * from the merchant user starting it - and must fail closed (blocked, not silently allowed) if
 * that user has never enabled MFA at all, since the whole point of a step-up control is to require
 * it for this specific action regardless of the user's login-time MFA choice.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class TransactionsLogControllerStepUpMfaTest {

    @Test
    void allowsAPayoutBelowTheThresholdWithNoMfaAtAll() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold("1000000");
        controller.merchantMfaService = null;

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(500000.0), new JSONObject());

        assertThat(result).isNull();
    }

    @Test
    void blocksAPayoutAboveTheThresholdWhenMfaIsNotEnabled() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold("1000000");
        MerchantMfaService mfaService = mock(MerchantMfaService.class);
        when(mfaService.isEnabled(anyLong())).thenReturn(false);
        controller.merchantMfaService = mfaService;

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(2000000.0), new JSONObject());

        JSONObject json = new JSONObject(result);
        assertThat(json.getString("code")).isEqualTo("149");
    }

    @Test
    void blocksAPayoutAboveTheThresholdWhenNoMfaCodeIsSupplied() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold("1000000");
        MerchantMfaService mfaService = mock(MerchantMfaService.class);
        when(mfaService.isEnabled(anyLong())).thenReturn(true);
        controller.merchantMfaService = mfaService;

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(2000000.0), new JSONObject());

        JSONObject json = new JSONObject(result);
        assertThat(json.getString("code")).isEqualTo("STEP_UP_MFA_REQUIRED");
    }

    @Test
    void blocksAPayoutAboveTheThresholdWhenTheMfaCodeIsWrong() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold("1000000");
        MerchantMfaService mfaService = mock(MerchantMfaService.class);
        when(mfaService.isEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyCode(anyLong(), anyString())).thenReturn(false);
        controller.merchantMfaService = mfaService;
        JSONObject body = new JSONObject();
        body.put("mfa_code", "000000");

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(2000000.0), body);

        JSONObject json = new JSONObject(result);
        assertThat(json.getString("code")).isEqualTo("STEP_UP_MFA_REQUIRED");
    }

    @Test
    void allowsAPayoutAboveTheThresholdWithAValidMfaCode() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold("1000000");
        MerchantMfaService mfaService = mock(MerchantMfaService.class);
        when(mfaService.isEnabled(anyLong())).thenReturn(true);
        when(mfaService.verifyCode(anyLong(), anyString())).thenReturn(true);
        controller.merchantMfaService = mfaService;
        JSONObject body = new JSONObject();
        body.put("mfa_code", "123456");

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(2000000.0), body);

        assertThat(result).isNull();
    }

    @Test
    void allowsAnythingWhenTheThresholdIsUnset() {
        TransactionsLogController controller = new TransactionsLogController();
        controller.jdbcTemplate = stubThreshold(null);
        controller.merchantMfaService = null;

        String result = controller.requireStepUpMfaIfOverThreshold(user(7L), payment(999999999.0), new JSONObject());

        assertThat(result).isNull();
    }

    private NamedParameterJdbcTemplate stubThreshold(String value) {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        if (value == null) {
            when(jdbcTemplate.query(contains("WHERE name=:name"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        } else {
            net.citotech.cito.Model.Setting setting = new net.citotech.cito.Model.Setting();
            setting.setSetting_value(value);
            when(jdbcTemplate.query(contains("WHERE name=:name"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(setting));
        }
        return jdbcTemplate;
    }

    private MerchantUser user(long id) {
        MerchantUser user = new MerchantUser();
        user.setId(id);
        return user;
    }

    private Payment payment(double totalAmount) {
        Payment payment = new Payment();
        payment.setTotal_amount(totalAmount);
        return payment;
    }
}
