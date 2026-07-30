package net.citotech.cito.Model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GatewayBalanceTypeTest {

    @Test
    void resolvesTheCorrectColumnAndLabelPerGateway() {
        assertThat(GatewayBalanceType.fromGatewayId("MTNMoMoPaymentGateway").columnName())
                .isEqualTo("mtnmm_balance");
        assertThat(GatewayBalanceType.fromGatewayId("SafariComPaymentGateway").columnName())
                .isEqualTo("safaricom_balance");
        assertThat(GatewayBalanceType.fromGatewayId("AirtelMoneyPaymentGateway").columnName())
                .isEqualTo("airtelmm_balance");
        assertThat(
                        GatewayBalanceType.fromGatewayId("AirtelMoneyOpenApiPaymentGateway")
                                .columnName())
                .isEqualTo("airtelmm_balance");
    }

    @Test
    void matchesTheLegacyStringArrayValuesExactly() {
        for (GatewayBalanceType type : GatewayBalanceType.values()) {
            if (type == GatewayBalanceType.SMS) {
                continue;
            }
            String[] legacy = Balance.getBalanceTypeByGatewayId(type.gatewayId());
            assertThat(legacy).isNotNull();
            assertThat(legacy[0]).isEqualTo(type.columnName());
            assertThat(legacy[1]).isEqualTo(type.label());
        }
    }

    @Test
    void returnsNullForAnUnknownGateway() {
        assertThat(GatewayBalanceType.fromGatewayId("SomeUnknownGateway")).isNull();
    }

    @Test
    void exposesTheIsoCurrencyCodeEachBalanceIsDenominatedIn() {
        assertThat(GatewayBalanceType.MTN_MOMO.currencyCode()).isEqualTo("UGX");
        assertThat(GatewayBalanceType.AIRTEL_MONEY.currencyCode()).isEqualTo("UGX");
        assertThat(GatewayBalanceType.AIRTEL_OPENAPI.currencyCode()).isEqualTo("UGX");
        assertThat(GatewayBalanceType.SAFARICOM_MPESA.currencyCode()).isEqualTo("KES");
        assertThat(GatewayBalanceType.SMS.currencyCode()).isEqualTo("UGX");
    }

    @Test
    void exposesStableChannelCodesForTheNormalizedBalanceReadModel() {
        assertThat(GatewayBalanceType.MTN_MOMO.channelCode()).isEqualTo("mtn_momo");
        assertThat(GatewayBalanceType.AIRTEL_MONEY.channelCode()).isEqualTo("airtel_money");
        assertThat(GatewayBalanceType.AIRTEL_OPENAPI.channelCode()).isEqualTo("airtel_open_api");
        assertThat(GatewayBalanceType.SAFARICOM_MPESA.channelCode()).isEqualTo("safaricom_mpesa");
        assertThat(GatewayBalanceType.SMS.channelCode()).isEqualTo("sms");
    }

    @Test
    void resolvesLegacyStatementColumnsForReadModelRefreshes() {
        assertThat(GatewayBalanceType.fromColumnName("mtnmm_balance"))
                .isEqualTo(GatewayBalanceType.MTN_MOMO);
        assertThat(GatewayBalanceType.fromColumnName("airtelmm_balance"))
                .isEqualTo(GatewayBalanceType.AIRTEL_MONEY);
        assertThat(GatewayBalanceType.fromColumnName("safaricom_balance"))
                .isEqualTo(GatewayBalanceType.SAFARICOM_MPESA);
        assertThat(GatewayBalanceType.fromColumnName("sms_balance"))
                .isEqualTo(GatewayBalanceType.SMS);
        assertThat(GatewayBalanceType.fromColumnName("unknown_balance")).isNull();
    }
}
