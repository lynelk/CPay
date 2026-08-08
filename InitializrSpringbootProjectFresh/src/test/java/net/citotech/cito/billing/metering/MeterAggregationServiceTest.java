package net.citotech.cito.billing.metering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/** Covers {@link MeterAggregationService}'s COUNT/SUM dispatch and dimension-filter correctness. */
@SuppressWarnings({"rawtypes", "unchecked"})
class MeterAggregationServiceTest {

    @Test
    void aggregateSumsQuantityForASumMeter() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubAggregationType(jdbcTemplate, "SUM");
        stubAggregateResult(jdbcTemplate, new BigDecimal("42.5000"));

        BigDecimal result =
                new MeterAggregationService(jdbcTemplate)
                        .aggregate(
                                7L,
                                "PAYMENT",
                                "payment_volume",
                                Instant.EPOCH,
                                Instant.now(),
                                null,
                                null);

        assertThat(result).isEqualByComparingTo("42.5000");
        verify(jdbcTemplate)
                .queryForObject(
                        contains("SUM(quantity)"),
                        any(SqlParameterSource.class),
                        any(RowMapper.class));
    }

    @Test
    void aggregateCountsRowsForACountMeter() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubAggregationType(jdbcTemplate, "COUNT");
        stubAggregateResult(jdbcTemplate, new BigDecimal("3"));

        BigDecimal result =
                new MeterAggregationService(jdbcTemplate)
                        .aggregate(
                                7L,
                                "PAYMENT",
                                "payment_event_count",
                                Instant.EPOCH,
                                Instant.now(),
                                null,
                                null);

        assertThat(result).isEqualByComparingTo("3");
        verify(jdbcTemplate)
                .queryForObject(
                        contains("COUNT(*)"), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void aggregateAppliesADimensionFilterWhenProvided() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubAggregationType(jdbcTemplate, "COUNT");
        stubAggregateResult(jdbcTemplate, new BigDecimal("1"));

        new MeterAggregationService(jdbcTemplate)
                .aggregate(
                        7L,
                        "PAYMENT",
                        "payment_event_count",
                        Instant.EPOCH,
                        Instant.now(),
                        "provider",
                        "MTN");

        verify(jdbcTemplate)
                .queryForObject(
                        org.mockito.ArgumentMatchers.argThat(
                                (String sql) ->
                                        sql.contains("JSON_EXTRACT(dimensions, :dimension_path)")),
                        org.mockito.ArgumentMatchers.argThat(
                                (MapSqlParameterSource p) ->
                                        "$.\"provider\"".equals(p.getValue("dimension_path"))
                                                && "MTN".equals(p.getValue("dimension_value"))),
                        any(RowMapper.class));
    }

    @Test
    void aggregateReturnsZeroWhenThereAreNoMatchingRows() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubAggregationType(jdbcTemplate, "SUM");
        when(jdbcTemplate.queryForObject(
                        anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(null);

        BigDecimal result =
                new MeterAggregationService(jdbcTemplate)
                        .aggregate(
                                7L,
                                "PAYMENT",
                                "payment_volume",
                                Instant.EPOCH,
                                Instant.now(),
                                null,
                                null);

        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aggregateThrowsForAnUnknownMeterCode() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                new MeterAggregationService(jdbcTemplate)
                                        .aggregate(
                                                7L,
                                                "PAYMENT",
                                                "not_a_real_meter",
                                                Instant.EPOCH,
                                                Instant.now(),
                                                null,
                                                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not_a_real_meter");
    }

    private void stubAggregationType(
            NamedParameterJdbcTemplate jdbcTemplate, String aggregationType) {
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(aggregationType));
    }

    private void stubAggregateResult(NamedParameterJdbcTemplate jdbcTemplate, BigDecimal value)
            throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBigDecimal(1)).thenReturn(value);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return mapper.mapRow(rs, 1);
                        });
    }
}
