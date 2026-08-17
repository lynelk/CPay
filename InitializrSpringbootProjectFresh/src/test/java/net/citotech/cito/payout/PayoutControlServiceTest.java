package net.citotech.cito.payout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import net.citotech.cito.DoPayGateway;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.payout.PayoutControlService.PayoutEvaluation;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Payout risk-control coverage (audit finding: payouts had risk authorization but no configurable
 * limits and no maker-checker approval queue). A control row can park an over-limit or
 * review-triggered payout as APPROVAL_REQUIRED; no control row preserves immediate execution; the
 * approval step enforces maker != checker. P0 section 3: the provider-switch and
 * beneficiary-amount-change triggers are opt-in, so every legacy case stays fail-open.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class PayoutControlServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void evaluateExecutesWhenNoControlRowExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("500"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isFalse();
        assertThat(result.decision()).isEqualTo("EXECUTE");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void evaluateParksAPerTransactionLimitBreachForMakerCheckerApproval() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        stubControl(jdbcTemplate, new BigDecimal("1000"), null, new BigDecimal("500"), null, "NO");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(42L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("900"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("PER_TRANSACTION_LIMIT");
        assertThat(result.queueId()).isEqualTo(42L);
    }

    @Test
    void evaluateParksADailyLimitBreachForMakerCheckerApproval() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        stubControl(jdbcTemplate, new BigDecimal("1000"), null, null, null, "NO");
        when(jdbcTemplate.queryForObject(
                        contains("COALESCE(SUM"),
                        any(MapSqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("800"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(43L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("300"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("DAILY_LIMIT");
        assertThat(result.queueId()).isEqualTo(43L);
    }

    @Test
    void evaluateExecutesWhenTheAmountStaysWithinTheDailyLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        stubControl(jdbcTemplate, new BigDecimal("1000"), new BigDecimal("5000"), null, null, "NO");
        when(jdbcTemplate.queryForObject(
                        contains("COALESCE(SUM"),
                        any(MapSqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("200"));
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("300"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isFalse();
        assertThat(result.decision()).isEqualTo("EXECUTE");
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void evaluateParksABeneficiaryVelocityBreachForMakerCheckerApproval() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(3);
        stubControl(jdbcTemplate, null, null, null, 2, "NO");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(44L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("100"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("BENEFICIARY_VELOCITY_LIMIT");
    }

    @Test
    void evaluateParksTheFirstBeneficiaryPayoutWhenApprovalIsConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        stubControl(jdbcTemplate, null, null, null, null, "YES");
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(45L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("100"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("FIRST_BENEFICIARY");
    }

    @Test
    void evaluateParksAProviderSwitchForAKnownBeneficiaryWhenConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
        stubControl(jdbcTemplate, null, null, null, null, "NO", "YES", null);
        when(jdbcTemplate.query(
                        contains("SELECT gateway_id FROM merchant_transactions_log"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of("GATEWAY_OLD"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(50L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        try (MockedStatic<DoPayGateway> gateway = mockStatic(DoPayGateway.class)) {
            when(DoPayGateway.getGatewayIdByMsisdn(
                            anyString(), any(NamedParameterJdbcTemplate.class)))
                    .thenReturn("GATEWAY_NEW");

            PayoutEvaluation result =
                    service.evaluate(payoutRequest("100"), merchant(7L), "merchant-app");

            assertThat(result.isApprovalRequired()).isTrue();
            assertThat(result.reasonCode()).isEqualTo("PROVIDER_SWITCH");
            assertThat(result.queueId()).isEqualTo(50L);
        }
    }

    @Test
    void evaluateExecutesWhenTheProviderHasNotSwitched() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
        stubControl(jdbcTemplate, null, null, null, null, "NO", "YES", null);
        when(jdbcTemplate.query(
                        contains("SELECT gateway_id FROM merchant_transactions_log"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenReturn(List.of("GATEWAY_OLD"));
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        try (MockedStatic<DoPayGateway> gateway = mockStatic(DoPayGateway.class)) {
            when(DoPayGateway.getGatewayIdByMsisdn(
                            anyString(), any(NamedParameterJdbcTemplate.class)))
                    .thenReturn("GATEWAY_OLD");

            PayoutEvaluation result =
                    service.evaluate(payoutRequest("100"), merchant(7L), "merchant-app");

            assertThat(result.isApprovalRequired()).isFalse();
            assertThat(result.decision()).isEqualTo("EXECUTE");
            verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
        }
    }

    @Test
    void evaluateParksAnAmountBeyondTheBeneficiaryProfileWhenConfigured() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);
        stubControl(jdbcTemplate, null, null, null, null, "NO", "NO", new BigDecimal("2.00"));
        when(jdbcTemplate.queryForObject(
                        contains("COALESCE(SUM"),
                        any(MapSqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(BigDecimal.ZERO);
        when(jdbcTemplate.queryForObject(
                        contains("SELECT MAX(original_amount)"),
                        any(MapSqlParameterSource.class),
                        eq(BigDecimal.class)))
                .thenReturn(new BigDecimal("100"));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(51L);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        PayoutEvaluation result =
                service.evaluate(payoutRequest("300"), merchant(7L), "merchant-app");

        assertThat(result.isApprovalRequired()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("BENEFICIARY_AMOUNT_CHANGE");
        assertThat(result.queueId()).isEqualTo(51L);
    }

    @Test
    void approveRejectsTheSameActorAsTheRequester() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(
                        List.of(queued(7L, "REF-1", "PENDING_APPROVAL", "finance-maker", null)));
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        assertThatThrownBy(() -> service.approve(7L, "finance-maker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("different actor");
    }

    @Test
    void approveFailsWhenThePayoutIsNotPending() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(queued(7L, "REF-1", "REJECTED", "finance-maker", null)));
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        assertThatThrownBy(() -> service.approve(7L, "finance-checker"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not awaiting approval");
    }

    @Test
    void rejectDefaultsTheReasonAndRequiresADifferentActor() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        PayoutControlService service = new PayoutControlService(jdbcTemplate, OBJECT_MAPPER);

        int updated = service.reject(7L, "finance-checker", null);

        assertThat(updated).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("requested_by<>"),
                        any(MapSqlParameterSource.class));
    }

    /**
     * Stubs the service's private {@code Control} RowMapper the way the existing {@code
     * SettlementScheduleServiceTest} does: the mapper is resolved out of the query invocation and
     * applied to a mocked ResultSet, so the service's own generic cast to its private record type
     * succeeds. New P0 section 3 columns default to fail-open (flag "NO", factor null).
     */
    private void stubControl(
            NamedParameterJdbcTemplate jdbcTemplate,
            BigDecimal daily,
            BigDecimal monthly,
            BigDecimal perTx,
            Integer velocity,
            String approvalRequired) {
        stubControl(jdbcTemplate, daily, monthly, perTx, velocity, approvalRequired, "NO", null);
    }

    private void stubControl(
            NamedParameterJdbcTemplate jdbcTemplate,
            BigDecimal daily,
            BigDecimal monthly,
            BigDecimal perTx,
            Integer velocity,
            String approvalRequired,
            String providerSwitch,
            BigDecimal amountFactor) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getBigDecimal("daily_amount_limit")).thenReturn(daily);
            when(row.getBigDecimal("monthly_amount_limit")).thenReturn(monthly);
            when(row.getBigDecimal("per_transaction_limit")).thenReturn(perTx);
            when(row.getObject("beneficiary_velocity_limit")).thenReturn(velocity);
            when(row.getString("approval_required_flag")).thenReturn(approvalRequired);
            when(row.getString("enabled_flag")).thenReturn("YES");
            when(row.getString("provider_switch_approval_flag")).thenReturn(providerSwitch);
            when(row.getBigDecimal("beneficiary_amount_factor")).thenReturn(amountFactor);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(jdbcTemplate.query(
                        contains("FROM payout_controls"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }

    private PaymentRequest payoutRequest(String amount) {
        PaymentRequest request = new PaymentRequest();
        request.setMerchantNumber("M100");
        request.setAmount(amount);
        request.setCurrency("UGX");
        request.setCountry("UG");
        request.setChannel("MTN_MOMO");
        request.setReference("REF-" + System.nanoTime());
        request.setDescription("payout");
        request.setCallbackUrl("https://merchant.example/cb");
        PaymentPartyRequest payee = new PaymentPartyRequest();
        payee.setType("MSISDN");
        payee.setValue("256700000001");
        request.setPayee(payee);
        return request;
    }

    private Merchant merchant(long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setAccount_number("M100");
        merchant.setName("Test Merchant");
        return merchant;
    }

    private PayoutControlService.QueuedPayout queued(
            long id, String reference, String status, String requestedBy, String approvedBy) {
        return new PayoutControlService.QueuedPayout(
                id,
                reference,
                7L,
                "M100",
                "{}",
                new BigDecimal("100"),
                "UGX",
                status,
                "PER_TRANSACTION_LIMIT",
                requestedBy,
                approvedBy);
    }
}
