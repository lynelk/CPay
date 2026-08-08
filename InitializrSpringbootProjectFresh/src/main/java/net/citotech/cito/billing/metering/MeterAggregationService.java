package net.citotech.cito.billing.metering;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * MVP meter aggregation over {@code billing_usage_events} - {@code COUNT}/{@code SUM} only, no
 * time-windowing or rounding policy yet (deferred to Phase 2). The aggregation function is looked
 * up from the meter's own {@code billing_meters.aggregation_type} (V39) rather than trusted from
 * the caller, so a caller can never mismatch a {@code SUM} meter with a {@code COUNT} query or vice
 * versa.
 */
@Service
public class MeterAggregationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MeterAggregationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param dimensionKey optional {@code billing_usage_events.dimensions} key to filter by (e.g.
     *     {@code "provider"}); {@code null}/blank means no dimension filter.
     */
    public BigDecimal aggregate(
            long billingTenantId,
            String serviceCode,
            String meterCode,
            Instant from,
            Instant to,
            String dimensionKey,
            String dimensionValue) {
        String aggregationType = lookupAggregationType(meterCode);
        String selectExpr =
                switch (aggregationType) {
                    case "COUNT" -> "COUNT(*)";
                    case "SUM" -> "SUM(quantity)";
                    default ->
                            throw new IllegalStateException(
                                    "Unsupported aggregation_type "
                                            + aggregationType
                                            + " for meter "
                                            + meterCode
                                            + " - only COUNT/SUM are implemented in this phase");
                };

        StringBuilder sql =
                new StringBuilder("SELECT ")
                        .append(selectExpr)
                        .append(
                                " FROM billing_usage_events WHERE billing_tenant_id=:tenant_id "
                                        + "AND service_code=:service_code AND meter_code=:meter_code "
                                        + "AND event_time >= :from_time AND event_time < :to_time");
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenant_id", billingTenantId);
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("from_time", Timestamp.from(from));
        p.addValue("to_time", Timestamp.from(to));
        if (dimensionKey != null && !dimensionKey.isBlank()) {
            sql.append(
                    " AND JSON_UNQUOTE(JSON_EXTRACT(dimensions, :dimension_path)) = :dimension_value");
            p.addValue("dimension_path", "$.\"" + dimensionKey + "\"");
            p.addValue("dimension_value", dimensionValue);
        }

        BigDecimal result =
                jdbcTemplate.queryForObject(sql.toString(), p, (rs, rowNum) -> rs.getBigDecimal(1));
        return result == null ? BigDecimal.ZERO : result;
    }

    private String lookupAggregationType(String meterCode) {
        List<String> rows =
                jdbcTemplate.query(
                        "SELECT aggregation_type FROM billing_meters WHERE meter_code=:meter_code",
                        new MapSqlParameterSource("meter_code", meterCode),
                        (rs, rowNum) -> rs.getString("aggregation_type"));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Unknown meter_code " + meterCode);
        }
        return rows.get(0);
    }
}
