package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.payout.PayoutControlService;
import net.citotech.cito.payout.PayoutControlService.PayoutEvaluation;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * S1: v1 payout-control parity. The raw legacy payout path ({@code Api.doMobileMoneyPayOut}) now
 * runs the same {@link PayoutControlService} gate as v2 before reserve/execute. These tests assert
 * the wiring behaves correctly: a breached limit parks the payout (APPROVAL_REQUIRED) and a clean
 * request passes through (EXECUTE) - so the V34 controls can no longer be evaded by calling {@code
 * /api/v1} instead of {@code /api/v2}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ApiV1PayoutControlTest {

    @Test
    void evaluateV1PayoutControlParksAPerTransactionLimitBreach() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        stubControl(jdbcTemplate, new BigDecimal("1000"), null, new BigDecimal("500"), null, "NO");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(42L);

        PayoutControlService service =
                new PayoutControlService(
                        jdbcTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
        Api api = apiWithPayoutControl(service);
        Merchant merchant = merchant(7L);

        PayoutEvaluation result =
                invokeEvaluate(
                        api,
                        merchant,
                        "M100",
                        "900",
                        "256700000001",
                        "MTN_MOMO",
                        "REF-PARK-1",
                        "payout",
                        "https://merchant.example/cb");

        assertThat(result).isNotNull();
        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("PER_TRANSACTION_LIMIT");
        assertThat(result.queueId()).isEqualTo(42L);
    }

    @Test
    void evaluateV1PayoutControlExecutesWhenNoControlRowExists() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        PayoutControlService service =
                new PayoutControlService(
                        jdbcTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
        Api api = apiWithPayoutControl(service);
        Merchant merchant = merchant(7L);

        PayoutEvaluation result =
                invokeEvaluate(
                        api,
                        merchant,
                        "M100",
                        "100",
                        "256700000001",
                        "MTN_MOMO",
                        "REF-GO-1",
                        "payout",
                        "https://merchant.example/cb");

        assertThat(result).isNotNull();
        assertThat(result.isApprovalRequired()).isFalse();
        assertThat(result.decision()).isEqualTo("EXECUTE");
    }

    @Test
    void evaluateV1PayoutControlReturnsNullWhenMerchantIsNull() throws Exception {
        PayoutControlService service =
                new PayoutControlService(
                        mock(NamedParameterJdbcTemplate.class),
                        new com.fasterxml.jackson.databind.ObjectMapper());
        Api api = apiWithPayoutControl(service);

        PayoutEvaluation result =
                invokeEvaluate(
                        api,
                        null,
                        "M100",
                        "100",
                        "256700000001",
                        "MTN_MOMO",
                        "REF-NULL-1",
                        "payout",
                        "https://merchant.example/cb");

        assertThat(result).isNull();
    }

    private Api apiWithPayoutControl(PayoutControlService service) throws Exception {
        Api api = new Api();
        Field field = Api.class.getDeclaredField("payoutControlService");
        field.setAccessible(true);
        field.set(api, service);
        return api;
    }

    private PayoutEvaluation invokeEvaluate(
            Api api,
            Merchant merchant,
            String merchantNumber,
            String amount,
            String payeeNumber,
            String gatewayId,
            String reference,
            String description,
            String callbackUrl)
            throws Exception {
        java.lang.reflect.Method method =
                Api.class.getDeclaredMethod(
                        "evaluateV1PayoutControl",
                        Merchant.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class);
        method.setAccessible(true);
        return (PayoutEvaluation)
                method.invoke(
                        api,
                        merchant,
                        merchantNumber,
                        amount,
                        payeeNumber,
                        gatewayId,
                        reference,
                        description,
                        callbackUrl);
    }

    private Merchant merchant(long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setAccount_number("M100");
        merchant.setName("Test Merchant");
        return merchant;
    }

    /** Stubs the private Control RowMapper the same way PayoutControlServiceTest does. */
    private void stubControl(
            NamedParameterJdbcTemplate jdbcTemplate,
            BigDecimal daily,
            BigDecimal monthly,
            BigDecimal perTx,
            Integer velocity,
            String approvalRequired) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getBigDecimal("daily_amount_limit")).thenReturn(daily);
            when(row.getBigDecimal("monthly_amount_limit")).thenReturn(monthly);
            when(row.getBigDecimal("per_transaction_limit")).thenReturn(perTx);
            when(row.getObject("beneficiary_velocity_limit")).thenReturn(velocity);
            when(row.getString("approval_required_flag")).thenReturn(approvalRequired);
            when(row.getString("enabled_flag")).thenReturn("YES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }
}
