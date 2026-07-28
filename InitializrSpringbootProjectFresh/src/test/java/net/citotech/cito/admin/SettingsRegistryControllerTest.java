package net.citotech.cito.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.ResultSet;
import java.util.List;
import net.citotech.cito.SettingsRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Covers audit O5: the read-only {@code GET /api/v2/admin/settings/registry} endpoint must list
 * every entry in {@link SettingsRegistry} with its metadata (name/type/group/description/default)
 * plus its current stored value and effective (parsed + defaulted) value side by side, so an
 * admin can see at a glance what's configurable and what's actually in effect right now - and it
 * must fall back cleanly (not error) for a setting that has no row yet in the {@code settings}
 * table.
 *
 * <p>This endpoint sits under {@code /api/v2/admin/**}, which {@code SecurityConfig} already
 * restricts to ROLE_ADMIN, so there's no separate authorization test here (matching how
 * {@code FeatureFlagController} - the closest existing admin listing endpoint - is tested).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class SettingsRegistryControllerTest {

    @Test
    void listsEveryRegisteredSettingWithMetadataAndItsStoredAndEffectiveValue() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);

        // Default: no row in the settings table for any setting name - everything falls back to
        // its documented default.
        when(jdbcTemplate.query(contains("FROM settings"), any(MapSqlParameterSource.class), any(RowMapper.class)))
            .thenReturn(List.of());

        // Except production_transaction_limit_count, which an admin has actually configured.
        when(jdbcTemplate.query(contains("FROM settings"),
                argThat((MapSqlParameterSource p) -> p != null
                        && "production_transaction_limit_count".equals(p.getValue("name"))),
                any(RowMapper.class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(2);
                return List.of(mapper.mapRow(settingRow("production_transaction_limit_count", "25"), 1));
            });

        SettingsRegistryController controller = new SettingsRegistryController(jdbcTemplate);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        String body = mockMvc.perform(get("/api/v2/admin/settings/registry"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JSONArray json = new JSONArray(body);
        assertThat(json.length()).isEqualTo(SettingsRegistry.all().size());

        JSONObject configured = findByName(json, "production_transaction_limit_count");
        assertThat(configured.getString("type")).isEqualTo("INTEGER");
        assertThat(configured.getString("group")).isEqualTo("merchant");
        assertThat(configured.getString("description")).isNotBlank();
        assertThat(configured.getString("defaultValue")).isEqualTo("10");
        assertThat(configured.getString("storedValue")).isEqualTo("25");
        assertThat(configured.getString("effectiveValue")).isEqualTo("25");
        assertThat(configured.getBoolean("usingDefault")).isFalse();

        JSONObject unconfigured = findByName(json, "use_merchant_provider_credentials");
        assertThat(unconfigured.getString("type")).isEqualTo("BOOLEAN");
        assertThat(unconfigured.isNull("storedValue")).isTrue();
        assertThat(unconfigured.getString("effectiveValue")).isEqualTo("false");
        assertThat(unconfigured.getBoolean("usingDefault")).isTrue();
    }

    private JSONObject findByName(JSONArray json, String name) {
        for (int i = 0; i < json.length(); i++) {
            JSONObject entry = json.getJSONObject(i);
            if (name.equals(entry.getString("name"))) {
                return entry;
            }
        }
        throw new AssertionError("No registry entry named '" + name + "' in response: " + json);
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
