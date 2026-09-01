package net.citotech.cito.fees;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SuppressWarnings({"rawtypes", "unchecked"})
class FeeScheduleServiceTest {

    @Test
    void currentScheduleReturnsTheMerchantOverrideWhenOnePasses() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet merchantRow = scheduleRow(1L, "MTNMoMoPaymentGateway", 42L, "PERCENTAGE", "1.50");
        when(jdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                (MapSqlParameterSource p) ->
                                        p != null
                                                && java.util.Objects.equals(
                                                        42L, p.getValue("merchant_id"))),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(merchantRow, 1));
                        });

        FeeScheduleService service = new FeeScheduleService(jdbcTemplate);
        FeeSchedule result =
                service.currentSchedule(
                                "MTNMoMoPaymentGateway", 42L, "PAYIN", "CUSTOMER_CHARGE")
                        .orElseThrow();

        assertThat(result.merchantId()).isEqualTo(42L);
        assertThat(result.amount()).isEqualByComparingTo("1.50");
    }

    @Test
    void currentScheduleFallsBackToTheGlobalDefaultWhenNoMerchantOverrideExists() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet globalRow = scheduleRow(2L, "MTNMoMoPaymentGateway", null, "FLAT_FEE", "500");
        when(jdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                (MapSqlParameterSource p) ->
                                        p != null && p.getValue("merchant_id") == null),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(globalRow, 1));
                        });
        when(jdbcTemplate.query(
                        anyString(),
                        org.mockito.ArgumentMatchers.argThat(
                                (MapSqlParameterSource p) ->
                                        p != null
                                                && java.util.Objects.equals(
                                                        7L, p.getValue("merchant_id"))),
                        any(RowMapper.class)))
                .thenReturn(List.of());

        FeeScheduleService service = new FeeScheduleService(jdbcTemplate);
        FeeSchedule result =
                service.currentSchedule(
                                "MTNMoMoPaymentGateway", 7L, "PAYOUT", "CUSTOMER_CHARGE")
                        .orElseThrow();

        assertThat(result.merchantId()).isNull();
        assertThat(result.amount()).isEqualByComparingTo("500");
    }

    @Test
    void createRejectsAnInvalidService() {
        FeeScheduleService service = new FeeScheduleService(mock(NamedParameterJdbcTemplate.class));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        "MTNMoMoPaymentGateway",
                                        null,
                                        "BOGUS",
                                        "CUSTOMER_CHARGE",
                                        "PERCENTAGE",
                                        new BigDecimal("1.5"),
                                        null,
                                        "tester"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("service");
    }

    @Test
    void createRejectsTierUntilRealTierBandsExist() {
        FeeScheduleService service = new FeeScheduleService(mock(NamedParameterJdbcTemplate.class));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        "MTNMoMoPaymentGateway",
                                        null,
                                        "PAYIN",
                                        "CUSTOMER_CHARGE",
                                        "TIER",
                                        new BigDecimal("500"),
                                        null,
                                        "tester"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("chargingMethod");
    }

    @Test
    void createRejectsNonPositiveAndImpossiblePercentageFees() {
        FeeScheduleService service = new FeeScheduleService(mock(NamedParameterJdbcTemplate.class));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        "gw",
                                        null,
                                        "PAYIN",
                                        "CUSTOMER_CHARGE",
                                        "FLAT_FEE",
                                        BigDecimal.ZERO,
                                        null,
                                        "tester"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("greater than zero");

        assertThatThrownBy(
                        () ->
                                service.create(
                                        "gw",
                                        null,
                                        "PAYIN",
                                        "CUSTOMER_CHARGE",
                                        "PERCENTAGE",
                                        new BigDecimal("100.0001"),
                                        null,
                                        "tester"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("100");
    }

    @Test
    void percentageScheduleUsesFourDecimalCalculationPrecision() {
        FeeSchedule schedule =
                new FeeSchedule(
                        1L,
                        "MTNMoMoPaymentGateway",
                        null,
                        "PAYIN",
                        "CUSTOMER_CHARGE",
                        "PERCENTAGE",
                        new BigDecimal("1.2345"),
                        Instant.now(),
                        null);

        assertThat(schedule.apply(new BigDecimal("123.4567")))
                .isEqualByComparingTo("1.5241");
    }

    @Test
    void flatFeeScheduleIgnoresTheTransactionAmount() {
        FeeSchedule schedule =
                new FeeSchedule(
                        1L,
                        "MTNMoMoPaymentGateway",
                        null,
                        "PAYIN",
                        "CUSTOMER_CHARGE",
                        "FLAT_FEE",
                        new BigDecimal("500"),
                        Instant.now(),
                        null);

        assertThat(schedule.apply(new BigDecimal("1000000"))).isEqualByComparingTo("500.0000");
    }

    @Test
    void unsupportedScheduleNeverFallsBackToFlatFee() {
        FeeSchedule schedule =
                new FeeSchedule(
                        1L,
                        "gw",
                        null,
                        "PAYIN",
                        "CUSTOMER_CHARGE",
                        "TIER",
                        new BigDecimal("500"),
                        Instant.now(),
                        null);

        assertThatThrownBy(() -> schedule.apply(new BigDecimal("1000")))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported");
    }

    private ResultSet scheduleRow(
            long id, String gatewayId, Long merchantId, String chargingMethod, String amount) {
        ResultSet row = mock(ResultSet.class);
        try {
            when(row.getLong("id")).thenReturn(id);
            when(row.getString("gateway_id")).thenReturn(gatewayId);
            if (merchantId == null) {
                when(row.getObject("merchant_id")).thenReturn(null);
            } else {
                when(row.getObject("merchant_id")).thenReturn(merchantId);
                when(row.getLong("merchant_id")).thenReturn(merchantId);
            }
            when(row.getString("service")).thenReturn("PAYIN");
            when(row.getString("charge_type")).thenReturn("CUSTOMER_CHARGE");
            when(row.getString("charging_method")).thenReturn(chargingMethod);
            when(row.getBigDecimal("amount")).thenReturn(new BigDecimal(amount));
            when(row.getTimestamp("effective_from"))
                    .thenReturn(java.sql.Timestamp.from(Instant.now().minusSeconds(60)));
            when(row.getTimestamp("effective_to")).thenReturn(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return row;
    }
}
