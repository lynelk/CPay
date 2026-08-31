package net.citotech.cito.billing.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class FocusExportServiceTest {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void exportIsTenantScopedAndKeepsCustomerPriceSeparateFromProviderCost() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        doReturn(List.of())
                .when(jdbc)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        FocusExportService service = new FocusExportService(jdbc);

        service.rows(
                77L, Instant.parse("2026-08-01T00:00:00Z"), Instant.parse("2026-09-01T00:00:00Z"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("ue.billing_tenant_id=:tenant")
                .contains("cc.charge_type='CUSTOMER_CHARGE'")
                .contains("pc.charge_type='PROVIDER_COST'");
        assertThat(parameters.getValue().getValue("tenant")).isEqualTo(77L);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void csvContainsAllUnconditionalMandatoryFocus14CostAndUsageColumns() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        doReturn(List.of())
                .when(jdbc)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        FocusExportService service = new FocusExportService(jdbc);

        String header =
                service.csv(
                                77L,
                                Instant.parse("2026-08-01T00:00:00Z"),
                                Instant.parse("2026-09-01T00:00:00Z"))
                        .lines()
                        .findFirst()
                        .orElseThrow();

        assertThat(header)
                .contains("BilledCost")
                .contains("BillingAccountId")
                .contains("BillingAccountName")
                .contains("BillingCurrency")
                .contains("BillingPeriodEnd")
                .contains("BillingPeriodStart")
                .contains("ChargeCategory")
                .contains("ChargeClass")
                .contains("ChargeDescription")
                .contains("ChargePeriodEnd")
                .contains("ChargePeriodStart")
                .contains("ContractedCost")
                .contains("EffectiveCost")
                .contains("HostProviderName")
                .contains("InvoiceIssuerName")
                .contains("ListCost")
                .contains("PricingQuantity")
                .contains("PricingUnit")
                .contains("ServiceProviderName")
                .contains("ServiceCategory")
                .contains("ServiceName");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void customColumnsUseFocusExternalPrefixAndUnitPricingFieldsArePresent() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        doReturn(List.of())
                .when(jdbc)
                .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        FocusExportService service = new FocusExportService(jdbc);

        String header =
                service.csv(
                                77L,
                                Instant.parse("2026-08-01T00:00:00Z"),
                                Instant.parse("2026-09-01T00:00:00Z"))
                        .lines()
                        .findFirst()
                        .orElseThrow();
        assertThat(header)
                .contains("SkuPriceId")
                .contains("ContractedUnitPrice")
                .contains("ListUnitPrice")
                .contains("PricingCurrency")
                .contains("x_CitoProviderCost")
                .contains("x_CitoUsageEventId");
    }

    @Test
    void invalidTenantOrPeriodFailsClosedBeforeQuery() {
        NamedParameterJdbcTemplate jdbc = org.mockito.Mockito.mock(NamedParameterJdbcTemplate.class);
        FocusExportService service = new FocusExportService(jdbc);

        assertThatThrownBy(
                        () ->
                                service.rows(
                                        0L,
                                        Instant.parse("2026-08-01T00:00:00Z"),
                                        Instant.parse("2026-09-01T00:00:00Z")))
                .isInstanceOf(PaymentGatewayException.class);
        assertThatThrownBy(
                        () ->
                                service.rows(
                                        77L,
                                        Instant.parse("2026-09-01T00:00:00Z"),
                                        Instant.parse("2026-08-01T00:00:00Z")))
                .isInstanceOf(PaymentGatewayException.class);
    }
}
