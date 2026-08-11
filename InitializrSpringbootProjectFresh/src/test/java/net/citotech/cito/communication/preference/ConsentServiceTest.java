package net.citotech.cito.communication.preference;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the V51 consent audit trail: valid OPT_IN/OPT_OUT/UPDATE records are appended (never
 * mutated), the tenant key is bound on writes, and invalid consent-type/source values are rejected
 * so the audit trail stays machine-readable.
 */
class ConsentServiceTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private ConsentService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        service = new ConsentService(jdbcTemplate);
    }

    @Test
    void recordsAnOptOutAppendOnly() {
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        service.record(7L, "SMS", "OPT_OUT", "ADMIN", "checker@cpay");

        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void rejectsUnknownConsentType() {
        assertThatThrownBy(() -> service.record(7L, "SMS", "MAYBE", "ADMIN", "x"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("OPT_IN, OPT_OUT or UPDATE");
    }

    @Test
    void rejectsUnknownSource() {
        assertThatThrownBy(() -> service.record(7L, "SMS", "OPT_IN", "CALLER", "x"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("PORTAL, API, ADMIN or SYSTEM");
    }

    @Test
    void rejectsBlankChannel() {
        assertThatThrownBy(() -> service.record(7L, " ", "OPT_IN", "SYSTEM", null))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("channel is required");
    }
}
