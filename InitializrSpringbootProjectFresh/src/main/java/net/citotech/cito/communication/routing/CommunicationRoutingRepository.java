package net.citotech.cito.communication.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read/write access to the V50 routing registry (ISO domain mapping: communication/routing). The
 * V50 tables intentionally have no unique key on {@code (channel, merchant_id)} so a merchant can
 * hold multiple priority-tiers of rules for the same channel; {@link #upsertRule} therefore
 * implements the operator-expected "one rule per channel+merchant" semantics in code — it updates
 * the existing row for that scope (when no explicit id is given) instead of appending duplicates.
 */
@Repository
public class CommunicationRoutingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CommunicationRoutingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProviderRow> providers() {
        return jdbcTemplate.query(
                "SELECT id, provider_code, provider_name, channel, adapter_class, base_url,"
                        + " credentials_ref, enabled_flag, created_at, updated_at"
                        + " FROM communication_providers ORDER BY provider_code ASC",
                (rs, rowNum) ->
                        new ProviderRow(
                                rs.getLong("id"),
                                rs.getString("provider_code"),
                                rs.getString("provider_name"),
                                rs.getString("channel"),
                                rs.getString("adapter_class"),
                                rs.getString("base_url"),
                                rs.getString("credentials_ref"),
                                rs.getString("enabled_flag"),
                                rs.getString("created_at"),
                                rs.getString("updated_at")));
    }

    public Optional<ProviderRow> provider(String providerCode, String channel) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("provider_code", providerCode);
        params.addValue("channel", channel);
        List<ProviderRow> rows =
                jdbcTemplate.query(
                        "SELECT id, provider_code, provider_name, channel, adapter_class, base_url,"
                                + " credentials_ref, enabled_flag, created_at, updated_at"
                                + " FROM communication_providers"
                                + " WHERE provider_code = :provider_code AND channel = :channel"
                                + " LIMIT 1",
                        params,
                        (rs, rowNum) ->
                                new ProviderRow(
                                        rs.getLong("id"),
                                        rs.getString("provider_code"),
                                        rs.getString("provider_name"),
                                        rs.getString("channel"),
                                        rs.getString("adapter_class"),
                                        rs.getString("base_url"),
                                        rs.getString("credentials_ref"),
                                        rs.getString("enabled_flag"),
                                        rs.getString("created_at"),
                                        rs.getString("updated_at")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public List<RuleRow> rules() {
        return jdbcTemplate.query(
                "SELECT id, channel, merchant_id, priority, provider_code, enabled_flag,"
                        + " created_at, updated_at"
                        + " FROM communication_routing_rules ORDER BY channel ASC, merchant_id ASC,"
                        + " priority ASC, id ASC",
                (rs, rowNum) ->
                        new RuleRow(
                                rs.getLong("id"),
                                rs.getString("channel"),
                                (Long) rs.getObject("merchant_id"),
                                rs.getInt("priority"),
                                rs.getString("provider_code"),
                                rs.getString("enabled_flag"),
                                rs.getString("created_at"),
                                rs.getString("updated_at")));
    }

    /**
     * Inserts or updates one rule and returns the persisted row. When {@code id} is null the
     * existing row for the same channel+merchant scope is updated (one rule per scope in the admin
     * UI); when none exists a new row is inserted.
     */
    public RuleRow upsertRule(
            Long id,
            String channel,
            Long merchantId,
            Integer priority,
            String providerCode,
            String enabledFlag) {
        Long targetId = id;
        if (targetId == null) {
            targetId = findRuleId(channel, merchantId);
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("id", targetId);
        params.addValue("channel", channel);
        params.addValue("merchant_id", merchantId);
        params.addValue("priority", priority);
        params.addValue("provider_code", providerCode);
        params.addValue("enabled_flag", enabledFlag);

        if (targetId != null) {
            jdbcTemplate.update(
                    "UPDATE communication_routing_rules SET channel = :channel,"
                            + " merchant_id = :merchant_id, priority = :priority,"
                            + " provider_code = :provider_code, enabled_flag = :enabled_flag"
                            + " WHERE id = :id",
                    params);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO communication_routing_rules"
                            + " (channel, merchant_id, priority, provider_code, enabled_flag)"
                            + " VALUES (:channel, :merchant_id, :priority, :provider_code,"
                            + " :enabled_flag)",
                    params);
        }

        return findRule(channel, merchantId)
                .orElseThrow(() -> new IllegalStateException("Failed to persist routing rule"));
    }

    public void deleteRule(long ruleId) {
        jdbcTemplate.update(
                "DELETE FROM communication_routing_rules WHERE id = :id",
                new MapSqlParameterSource("id", ruleId));
    }

    /** The single rule the router lookup would pick for a merchant+channel (debug/effective view). */
    public Optional<RuleRow> effectiveRule(String channel, Long merchantId) {
        List<RuleRow> rows = ruleCandidates(channel, merchantId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Every candidate the router considers for a merchant+channel, in router order. */
    public List<RuleRow> effectiveCandidates(String channel, Long merchantId) {
        return ruleCandidates(channel, merchantId);
    }

    private Long findRuleId(String channel, Long merchantId) {
        List<Long> ids =
                jdbcTemplate.query(
                        "SELECT id FROM communication_routing_rules"
                                + " WHERE channel = :channel"
                                + "   AND ((merchant_id = :merchant_id AND :merchant_id IS NOT NULL)"
                                + "        OR (merchant_id IS NULL AND :merchant_id IS NULL))"
                                + " ORDER BY id ASC LIMIT 1",
                        ruleParams(channel, merchantId),
                        (rs, rowNum) -> rs.getLong("id"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<RuleRow> ruleCandidates(String channel, Long merchantId) {
        return jdbcTemplate.query(
                "SELECT r.id, r.channel, r.merchant_id, r.priority, r.provider_code,"
                        + " r.enabled_flag, r.created_at, r.updated_at"
                        + " FROM communication_routing_rules r"
                        + " WHERE r.channel = :channel"
                        + "   AND (r.merchant_id = :merchant_id OR r.merchant_id IS NULL)"
                        + " ORDER BY (r.merchant_id = :merchant_id) DESC, r.priority ASC, r.id ASC",
                ruleParams(channel, merchantId),
                (rs, rowNum) ->
                        new RuleRow(
                                rs.getLong("id"),
                                rs.getString("channel"),
                                (Long) rs.getObject("merchant_id"),
                                rs.getInt("priority"),
                                rs.getString("provider_code"),
                                rs.getString("enabled_flag"),
                                rs.getString("created_at"),
                                rs.getString("updated_at")));
    }

    private Optional<RuleRow> findRule(String channel, Long merchantId) {
        List<RuleRow> rows =
                jdbcTemplate.query(
                        "SELECT id, channel, merchant_id, priority, provider_code, enabled_flag,"
                                + " created_at, updated_at"
                                + " FROM communication_routing_rules"
                                + " WHERE channel = :channel"
                                + "   AND ((merchant_id = :merchant_id AND :merchant_id IS NOT NULL)"
                                + "        OR (merchant_id IS NULL AND :merchant_id IS NULL))"
                                + " ORDER BY id ASC LIMIT 1",
                        ruleParams(channel, merchantId),
                        (rs, rowNum) ->
                                new RuleRow(
                                        rs.getLong("id"),
                                        rs.getString("channel"),
                                        (Long) rs.getObject("merchant_id"),
                                        rs.getInt("priority"),
                                        rs.getString("provider_code"),
                                        rs.getString("enabled_flag"),
                                        rs.getString("created_at"),
                                        rs.getString("updated_at")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private MapSqlParameterSource ruleParams(String channel, Long merchantId) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("channel", channel);
        params.addValue("merchant_id", merchantId);
        return params;
    }

    /** Provider catalog row. */
    public record ProviderRow(
            long id,
            String providerCode,
            String providerName,
            String channel,
            String adapterClass,
            String baseUrl,
            String credentialsRef,
            String enabledFlag,
            String createdAt,
            String updatedAt) {}

    /** Routing rule row. merchantId is null for the platform default. */
    public record RuleRow(
            long id,
            String channel,
            Long merchantId,
            int priority,
            String providerCode,
            String enabledFlag,
            String createdAt,
            String updatedAt) {}
}
