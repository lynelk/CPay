package net.citotech.cito.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB-backed operational gauges for queues/backlogs that otherwise only appear inside admin screens.
 *
 * <p>These are intentionally small COUNT(*) probes over indexed status columns so Prometheus can
 * alert on stuck callbacks/webhooks without operators having to query the database by hand.
 */
@Component
public class OperationsGaugeMetrics {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final List<CountProbe> probes = new ArrayList<>();

    public OperationsGaugeMetrics(
            MeterRegistry meterRegistry, NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        gauge(meterRegistry, "cpay.callback.tasks", "PARKED", callbackTasks("PARKED"));
        gauge(
                meterRegistry,
                "cpay.callback.tasks",
                "PENDING_OR_RETRY",
                callbackTasks("PENDING", "RETRY"));
        gauge(meterRegistry, "cpay.webhook.deliveries", "FAILED", webhookDeliveries("FAILED"));
        gauge(meterRegistry, "cpay.webhook.deliveries", "PENDING", webhookDeliveries("PENDING"));
        Gauge.builder(
                        "cpay.operations.alerts.open",
                        this,
                        OperationsGaugeMetrics::openOperationsAlerts)
                .description("Open operations alert rows")
                .register(meterRegistry);
    }

    private void gauge(MeterRegistry registry, String name, String status, CountProbe probe) {
        probes.add(probe);
        Gauge.builder(name, probe, CountProbe::count)
                .tag("status", status)
                .description(name + " backlog by status")
                .register(registry);
    }

    private CountProbe callbackTasks(String... statuses) {
        return () -> countByStatuses("callback_tasks", "task_status", statuses);
    }

    private CountProbe webhookDeliveries(String... statuses) {
        return () -> countByStatuses("merchant_webhook_deliveries", "delivery_status", statuses);
    }

    private double openOperationsAlerts() {
        return count("SELECT COUNT(*) FROM operations_alerts WHERE alert_status='OPEN'");
    }

    private double countByStatuses(String tableName, String statusColumn, String... statuses) {
        MapSqlParameterSource parameters =
                new MapSqlParameterSource("statuses", java.util.List.of(statuses));
        return count(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + statusColumn + " IN (:statuses)",
                parameters);
    }

    private double count(String sql) {
        return count(sql, new MapSqlParameterSource());
    }

    private double count(String sql, MapSqlParameterSource parameters) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, parameters, Integer.class);
            return value == null ? 0 : value.doubleValue();
        } catch (Exception ignored) {
            return 0;
        }
    }

    @FunctionalInterface
    private interface CountProbe {
        double count();
    }
}
