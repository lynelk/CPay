package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class SettingsControllerTest {

    @Test
    void defaultSettingsCatalogContainsRequiredGatewayAndSmsSettings() throws Exception {
        JSONArray settings = readSettingsCatalog(Common.CLASS_PATH_DEFAULT_SETTINGS);

        assertThat(names(settings)).contains(
            "gw_mtn_api_url",
            "gw_mtn_api_url_sandbox",
            "gw_mtn_api_env",
            "gw_mtn_api_base_currency",
            "gw_mtn_api_collections_user_id",
            "gw_mtn_api_collections_user_key",
            "gw_mtn_api_collections_subscription_key",
            "gw_mtn_api_disbursements_user_id",
            "gw_mtn_api_disbursements_user_key",
            "gw_mtn_api_disbursements_subscription_key",
            "gw_mtn_api_cost_of_inbound_payment",
            "gw_mtn_api_cost_of_inbound_payment_method",
            "gw_mtn_api_cost_of_outbound_payment",
            "gw_mtn_api_cost_of_outbound_payment_method",
            "gw_mtn_api_customer_charge_inbound",
            "gw_mtn_api_customer_charge_inbound_method",
            "gw_mtn_api_customer_charge_outbound",
            "gw_mtn_api_customer_charge_outbound_method",
            "gw_mtn_api_customer_charge_method",
            "gw_mtn_api_min_amount",
            "gw_mtn_api_max_amount",
            "gw_airtelmoney_use_open_api",
            "gw_airtelmoney_api_url",
            "gw_airtelmoney_token_url",
            "gw_airtelmoney_collections_url",
            "gw_airtelmoney_disbursements_url",
            "gw_airtelmoney_balance_url",
            "gw_airtelmoney_collections_status_url",
            "gw_airtelmoney_disbursements_status_url",
            "gw_airtelmoney_api_username",
            "gw_airtelmoney_api_password",
            "gw_airtelmoney_api_pin",
            "gw_airtelmoney_api_public_key",
            "gw_airtelmoney_api_cost_of_inbound_payment",
            "gw_airtelmoney_api_cost_of_inbound_payment_method",
            "gw_airtelmoney_api_cost_of_outbound_payment",
            "gw_airtelmoney_api_cost_of_outbound_payment_method",
            "gw_airtelmoney_api_customer_charge_inbound",
            "gw_airtelmoney_api_customer_charge_inbound_method",
            "gw_airtelmoney_api_customer_charge_outbound",
            "gw_airtelmoney_api_customer_charge_outbound_method",
            "gw_airtelmoney_api_customer_charge_method",
            "gw_airtelmoney_api_min_amount",
            "gw_airtelmoney_api_max_amount",
            "sms_api_url",
            "sms_api_parameters",
            "sms_api_http_method",
            "sms_gateway_name",
            "sms_gateway_cost",
            "sms_customer_charge",
            "sms_revenue_account");

        JSONObject byName = byName(settings);
        assertThat(byName.getJSONObject("gw_airtelmoney_use_open_api").getString("setting_value")).isEqualTo("yes");
        assertThat(byName.getJSONObject("gw_airtelmoney_api_url").getString("setting_value"))
            .isEqualTo("https://openapiuat.airtel.africa");
        assertThat(byName.getJSONObject("gw_airtelmoney_collections_url").getString("setting_value"))
            .isEqualTo("/merchant/v2/payments/");
        assertThat(byName.getJSONObject("gw_airtelmoney_disbursements_url").getString("setting_value"))
            .isEqualTo("/standard/v2/disbursements/");
    }

    @Test
    void defaultMerchantSettingsCatalogContainsMerchantOverrideSettings() throws Exception {
        JSONArray settings = readSettingsCatalog(Common.CLASS_PATH_DEFAULT_MERCHANT_SETTINGS);

        assertThat(names(settings)).contains(
            "api_allowed_ips",
            "daily_volume_limit",
            "monthly_volume_limit",
            "gw_airtelmoney_collections_url",
            "gw_airtelmoney_disbursements_url",
            "sms_customer_charge");
    }

    @Test
    void settingsCatalogRowsHaveFieldsRequiredBySettingsGrid() throws Exception {
        assertCatalogRowsHaveRequiredFields(readSettingsCatalog(Common.CLASS_PATH_DEFAULT_SETTINGS));
        assertCatalogRowsHaveRequiredFields(readSettingsCatalog(Common.CLASS_PATH_DEFAULT_MERCHANT_SETTINGS));
    }

    @Test
    void defaultSettingsCatalogContainsAdminLoginAppearanceSettings() throws Exception {
        JSONArray settings = readSettingsCatalog(Common.CLASS_PATH_DEFAULT_SETTINGS);

        assertThat(names(settings)).contains(
            "merchant_login_hero_image_url",
            "merchant_login_benefit_insights_title",
            "merchant_login_control_title",
            "merchant_login_automation_title",
            "admin_login_hero_image_url",
            "admin_login_approvals_title",
            "admin_login_users_title",
            "admin_login_reliable_copy");

        JSONObject byName = byName(settings);
        assertThat(byName.getJSONObject("merchant_login_hero_image_url").getString("setting_value"))
            .contains("photo-1573496359142-b8d87734a5a2");
        assertThat(byName.getJSONObject("admin_login_hero_image_url").getString("setting_value"))
            .contains("photo-1551288049-bebda4e38f71");
    }

    @Test
    void legacyObjectSettingsCanBeConvertedToGridRows() {
        JSONArray settings = SettingsController.parseSettingsCatalog(
            "{\"mail.smtp.host\":\"localhost\"}",
            "Mail");

        assertThat(settings).hasSize(1);
        JSONObject setting = settings.getJSONObject(0);
        assertThat(setting.getString("name")).isEqualTo("mail.smtp.host");
        assertThat(setting.getString("label")).isEqualTo("Mail Smtp Host");
        assertThat(setting.getString("setting_value")).isEqualTo("localhost");
        assertThat(setting.getString("setting_group")).isEqualTo("Mail");
        assertThat(setting.getString("description")).isEqualTo("mail.smtp.host");
    }

    @Test
    void sensitiveSettingsAreMarkedAndMaskedForResponses() {
        assertThat(SettingsController.isSensitiveSettingName("gw_mtn_api_collections_user_key")).isTrue();
        assertThat(SettingsController.isSensitiveSettingName("gw_airtelmoney_api_password")).isTrue();
        assertThat(SettingsController.isSensitiveSettingName("sms_gateway_name")).isFalse();

        assertThat(SettingsController.maskSettingValueForResponse("gw_mtn_api_collections_user_key", "actual-secret"))
            .isEqualTo(SettingsController.MASKED_SETTING_VALUE);
        assertThat(SettingsController.maskSettingValueForResponse("sms_gateway_name", "Infobip"))
            .isEqualTo("Infobip");
    }

    @Test
    void maskedSensitiveSettingsPreserveCurrentStoredValueOnUpdate() {
        assertThat(SettingsController.settingValueForUpdate(
            "gw_airtelmoney_api_password",
            SettingsController.MASKED_SETTING_VALUE,
            "current-secret"))
            .isEqualTo("current-secret");

        assertThat(SettingsController.settingValueForUpdate(
            "gw_airtelmoney_api_password",
            "new-secret",
            "current-secret"))
            .isEqualTo("new-secret");

        assertThat(SettingsController.settingValueForUpdate(
            "sms_gateway_name",
            SettingsController.MASKED_SETTING_VALUE,
            "Infobip"))
            .isEqualTo(SettingsController.MASKED_SETTING_VALUE);
    }
    private JSONArray readSettingsCatalog(String path) throws IOException {
        String json = StreamUtils.copyToString(
            new ClassPathResource(path).getInputStream(),
            Charset.defaultCharset());
        return SettingsController.parseSettingsCatalog(json, "Application");
    }

    private Set<String> names(JSONArray settings) {
        Set<String> names = new java.util.HashSet<>();
        for (int i = 0; i < settings.length(); i++) {
            names.add(settings.getJSONObject(i).getString("name"));
        }
        return names;
    }

    private JSONObject byName(JSONArray settings) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < settings.length(); i++) {
            JSONObject setting = settings.getJSONObject(i);
            result.put(setting.getString("name"), setting);
        }
        return result;
    }

    private void assertCatalogRowsHaveRequiredFields(JSONArray settings) {
        for (int i = 0; i < settings.length(); i++) {
            JSONObject setting = settings.getJSONObject(i);
            assertThat(setting.optString("name")).isNotBlank();
            assertThat(setting.optString("label")).isNotBlank();
            assertThat(setting.has("setting_value")).isTrue();
            assertThat(setting.optString("setting_group")).isNotBlank();
            assertThat(setting.optString("description")).isNotBlank();
        }
    }
}
