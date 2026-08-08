package net.citotech.cito.billing.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Covers {@link ChargeShadowComparisonService}'s delta math against a mocked {@code
 * NamedParameterJdbcTemplate}. See {@code ChargeShadowComparisonServiceTestcontainersTest}
 * (docker-tagged) for the real proof that the JOIN across {@code merchant_transactions_log} and
 * {@code billing_rated_charges} actually detects a seeded divergent charge.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class ChargeShadowComparisonServiceTest {

    @Test
    void compareReportsAllMatchingWhenLegacyAndRatedChargesAgree() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubRows(jdbcTemplate, row("TX-1", "120.00", "120.00"));

        ChargeShadowComparisonResult result =
                new ChargeShadowComparisonService(jdbcTemplate)
                        .compare(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        assertThat(result.comparedCount()).isEqualTo(1);
        assertThat(result.matchingCount()).isEqualTo(1);
        assertThat(result.allMatch()).isTrue();
    }

    @Test
    void compareFlagsADivergentCharge() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubRows(jdbcTemplate, row("TX-2", "100.00", "105.50"));

        ChargeShadowComparisonResult result =
                new ChargeShadowComparisonService(jdbcTemplate)
                        .compare(Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());

        assertThat(result.allMatch()).isFalse();
        assertThat(result.diverging()).hasSize(1);
        ChargeShadowDelta delta = result.diverging().get(0);
        assertThat(delta.sourceReference()).isEqualTo("TX-2");
        assertThat(delta.delta()).isEqualByComparingTo("5.50");
    }

    private void stubRows(NamedParameterJdbcTemplate jdbcTemplate, ResultSet row) {
        when(jdbcTemplate.query(
                        any(String.class), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }

    private ResultSet row(String sourceReference, String legacyCharge, String ratedCharge)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("source_reference")).thenReturn(sourceReference);
        when(rs.getBigDecimal("legacy_charge")).thenReturn(new BigDecimal(legacyCharge));
        when(rs.getBigDecimal("rated_charge")).thenReturn(new BigDecimal(ratedCharge));
        return rs;
    }
}
