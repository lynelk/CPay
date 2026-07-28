package net.citotech.cito.gateway;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * DB-backed phone-prefix -> gateway routing table (audit B4), replacing the hardcoded
 * {@code String[] prefix} arrays previously duplicated in each {@code *PaymentGateway} class.
 * Prefixes are cached in memory (refreshed periodically) since routing lookups happen on every
 * payin/payout and must not add a DB round-trip to the hot path.
 */
@Service
public class ChannelRoutingService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private volatile Map<String, List<String>> prefixesByGateway = new HashMap<>();

    public ChannelRoutingService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        refresh();
    }

    @Scheduled(fixedDelayString = "${cpay.routing.refresh-delay-ms:300000}")
    public void refresh() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT gateway_id, msisdn_prefix FROM channel_routing_prefixes WHERE active_flag='YES'",
                new MapSqlParameterSource());
            Map<String, List<String>> byGateway = new ConcurrentHashMap<>();
            for (Map<String, Object> row : rows) {
                String gatewayId = (String) row.get("gateway_id");
                String prefix = (String) row.get("msisdn_prefix");
                byGateway.computeIfAbsent(gatewayId, key -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(prefix);
            }
            if (!byGateway.isEmpty()) {
                this.prefixesByGateway = byGateway;
            }
        } catch (Exception ignored) {
            // Table may not exist yet on an unmigrated environment - callers fall back to their
            // hardcoded defaults when this returns no prefixes for a gateway.
        }
    }

    /** Returns the configured prefixes for a gateway, or empty if none are configured in the DB. */
    public List<String> prefixesFor(String gatewayId) {
        return prefixesByGateway.getOrDefault(gatewayId, List.of());
    }

    public boolean matches(String gatewayId, String msisdn) {
        if (msisdn == null) {
            return false;
        }
        for (String prefix : prefixesFor(gatewayId)) {
            if (msisdn.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
