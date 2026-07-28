package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit O5: {@link SettingsRegistry}'s typed accessors sit on top of
 * {@link Common#getSettings} and must (1) parse a stored string according to the registered
 * {@link SettingsRegistry.SettingType}, (2) fall back to that entry's own documented default -
 * without throwing - when the stored row is missing or the value fails to parse, and (3) reject a
 * caller asking about a setting name this registry doesn't know about, since a typo'd name must
 * fail loudly rather than silently resolve to some guessed type and default.
 *
 * <p>Mocking follows the same pattern as
 * {@code net.citotech.cito.scheduler.TransactionTimeoutSchedulerTest}: {@link Common#getSettings}
 * issues {@code jdbcTemplate.query("SELECT * FROM settings WHERE name=:name", params, rowMapper)},
 * so the mock intercepts that call and feeds the supplied {@link RowMapper} a stubbed
 * {@link ResultSet} row (or an empty list when no row exists).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class SettingsRegistryAccessorTest {

    @Test
    void getBooleanParsesTrueFalseVariantsCaseInsensitivelyAndTrimmed() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("use_merchant_provider_credentials", "  TRUE  ");
        assertThat(SettingsRegistry.getBoolean("use_merchant_provider_credentials", jdbcTemplate)).isTrue();

        jdbcTemplate = jdbcWithSetting("use_merchant_provider_credentials", "no");
        assertThat(SettingsRegistry.getBoolean("use_merchant_provider_credentials", jdbcTemplate)).isFalse();

        jdbcTemplate = jdbcWithSetting("use_merchant_provider_credentials", "1");
        assertThat(SettingsRegistry.getBoolean("use_merchant_provider_credentials", jdbcTemplate)).isTrue();
    }

    @Test
    void getBooleanFallsBackToTheEntrysOwnDefaultWhenTheRowIsMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithNoRow();

        // use_merchant_provider_credentials defaults to false (fail closed: shared accounts stay in use).
        assertThat(SettingsRegistry.getBoolean("use_merchant_provider_credentials", jdbcTemplate)).isFalse();
        // production_transaction_limit_enabled defaults to true (fail closed: the safety limit stays on).
        assertThat(SettingsRegistry.getBoolean("production_transaction_limit_enabled", jdbcTemplate)).isTrue();
    }

    @Test
    void getBooleanFallsBackToTheEntrysOwnDefaultOnAnUnparseableStoredValueWithoutThrowing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("production_transaction_limit_enabled", "maybe-ish");
        assertThat(SettingsRegistry.getBoolean("production_transaction_limit_enabled", jdbcTemplate)).isTrue();
    }

    @Test
    void getIntParsesAValidStoredInteger() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("production_transaction_limit_count", "25");
        assertThat(SettingsRegistry.getInt("production_transaction_limit_count", jdbcTemplate)).isEqualTo(25);
    }

    @Test
    void getIntFallsBackToTheEntrysOwnDefaultOnGarbageStoredDataWithoutThrowing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("production_transaction_limit_count", "not-a-number");
        assertThat(SettingsRegistry.getInt("production_transaction_limit_count", jdbcTemplate)).isEqualTo(10);
    }

    @Test
    void getIntFallsBackToTheEntrysOwnDefaultWhenTheRowIsMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithNoRow();
        assertThat(SettingsRegistry.getInt("production_transaction_limit_count", jdbcTemplate)).isEqualTo(10);
        assertThat(SettingsRegistry.getInt("developer_sandbox_idempotency_hours", jdbcTemplate)).isEqualTo(24);
    }

    @Test
    void getDecimalParsesAValidStoredDecimalAsBigDecimalNotFloatingPoint() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("float_alert_mtn_min", "1500.50");
        assertThat(SettingsRegistry.getDecimal("float_alert_mtn_min", jdbcTemplate))
            .isEqualByComparingTo(new BigDecimal("1500.50"));
    }

    @Test
    void getDecimalFallsBackToTheEntrysOwnDefaultOnBadStoredDataWithoutThrowing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("float_alert_mtn_min", "a-lot");
        assertThat(SettingsRegistry.getDecimal("float_alert_mtn_min", jdbcTemplate))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getDecimalFallsBackToTheEntrysOwnDefaultWhenTheRowIsMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithNoRow();
        assertThat(SettingsRegistry.getDecimal("float_alert_airtel_min", jdbcTemplate))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getStringReturnsTheTrimmedStoredValueWhenPresentAndTheDefaultWhenBlankOrMissing() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithSetting("gw_mtn_api_env", " mtnuganda ");
        assertThat(SettingsRegistry.getString("gw_mtn_api_env", jdbcTemplate)).isEqualTo("mtnuganda");

        jdbcTemplate = jdbcWithSetting("gw_mtn_api_env", "   ");
        assertThat(SettingsRegistry.getString("gw_mtn_api_env", jdbcTemplate)).isEqualTo("sandbox");

        jdbcTemplate = jdbcWithNoRow();
        assertThat(SettingsRegistry.getString("gw_mtn_api_env", jdbcTemplate)).isEqualTo("sandbox");
    }

    @Test
    void unregisteredSettingNameIsRejectedInsteadOfSilentlyGuessingAType() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithNoRow();
        assertThatThrownBy(() -> SettingsRegistry.getBoolean("totally_made_up_setting_name", jdbcTemplate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unregistered setting");
        assertThatThrownBy(() -> SettingsRegistry.getString("another.typo.d_name", jdbcTemplate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unregistered setting");
    }

    @Test
    void askingForTheWrongTypeOnARegisteredSettingIsRejected() {
        NamedParameterJdbcTemplate jdbcTemplate = jdbcWithNoRow();
        // production_transaction_limit_count is registered as INTEGER, not BOOLEAN or STRING.
        assertThatThrownBy(() -> SettingsRegistry.getBoolean("production_transaction_limit_count", jdbcTemplate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("registered as");
        assertThatThrownBy(() -> SettingsRegistry.getString("production_transaction_limit_count", jdbcTemplate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("registered as");
    }

    private NamedParameterJdbcTemplate jdbcWithSetting(String name, String value) {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("FROM settings"),
                argThat((MapSqlParameterSource p) -> p != null && name.equals(p.getValue("name"))),
                any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(settingRow(name, value), 1));
            });
        return jdbcTemplate;
    }

    private NamedParameterJdbcTemplate jdbcWithNoRow() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(contains("FROM settings"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());
        return jdbcTemplate;
    }

    private ResultSet settingRow(String name, String value) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("name")).thenReturn(name);
        when(row.getString("label")).thenReturn(name);
        when(row.getString("setting_value")).thenReturn(value);
        when(row.getLong("id")).thenReturn(1L);
        when(row.getString("setting_group")).thenReturn("test");
        when(row.getString("description")).thenReturn("");
        return row;
    }
}
