package net.citotech.cito.communication.preference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.communication.preference.PreferenceService.PreferenceRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the V51 preference service: the tenant-scoped queries bind the tenant key, an absent row
 * defaults to enabled (consent-preserving), and invalid quiet-hours input is rejected.
 */
class PreferenceServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private PreferenceService service;

    private List<String> enabledFlagRows = List.of();
    private List<PreferenceRow> rowsForMerchant = List.of();

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (sql.contains("SELECT enabled_flag")) {
                                return enabledFlagRows;
                            }
                            if (sql.contains("communication_message_preferences")
                                    && sql.contains("tenant_merchant_id")) {
                                if (rowsForMerchant == null) {
                                    return List.of();
                                }
                                return rowsForMerchant;
                            }
                            return List.of();
                        });
        service = new PreferenceService(jdbcTemplate);
    }

    @Test
    void absentPreferenceDefaultsToEnabled() {
        rowsForMerchant = null;

        assertThat(service.isChannelEnabled(7L, "SMS")).isTrue();
    }

    @Test
    void explicitOptOutIsHonoured() {
        enabledFlagRows = List.of("N");

        assertThat(service.isChannelEnabled(7L, "SMS")).isFalse();
    }

    @Test
    void saveBindsTheTenantKeyInTheStatement() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        rowsForMerchant =
                List.of(
                        new PreferenceRow(
                                1L,
                                7L,
                                "EMAIL",
                                true,
                                null,
                                null,
                                "admin@cpay",
                                "2026-01-01",
                                "2026-01-01"));

        PreferenceRow saved = service.save(7L, "email", true, null, null, "admin@cpay");

        assertThat(saved.channel()).isEqualTo("EMAIL");
        // assertTenantBound would have thrown if :tenant_merchant_id were missing from the SQL.
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void malformedQuietHoursAreRejected() {
        assertThatThrownBy(() -> service.save(7L, "SMS", true, "25:99", null, "admin"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void blankChannelIsRejected() {
        assertThatThrownBy(() -> service.isChannelEnabled(7L, "  "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("channel is required");
    }
}
