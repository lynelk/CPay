package net.citotech.cito.communication.activation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Merchant capability activation for the Communications Gateway (Track A P4, guide Steps 5/41).
 * Merchants activate CPay capabilities ({@code channel + capability} pairs such as
 * WHATSAPP/SEND), never providers: routing stays an internal CPay concern and the provider
 * abstraction never leaks into the merchant contract.
 *
 * <p>Lifecycle (guide Step 5): REQUESTED → CONFIGURING → TESTING → ACTIVE, with SUSPENDED and
 * DISABLED as operator states. Only {@code ACTIVE} rows entitle a merchant to dispatch on that
 * channel; the outbox worker's callers must check {@link #requireActive} before accepting traffic.
 * Quotas (daily/monthly) are enforced at activation time by the API layer against the delivery
 * log; this service owns the state machine and uniqueness ((merchant_id, channel, capability)).
 */
@Service
public class MerchantCommunicationCapabilityService {

    private static final RowMapper<MerchantCapability> ROW_MAPPER =
            (ResultSet rs, int rowNum) ->
                    new MerchantCapability(
                            rs.getLong("id"),
                            rs.getLong("merchant_id"),
                            rs.getString("channel"),
                            rs.getString("capability"),
                            rs.getString("status"),
                            rs.getString("routing_mode"),
                            rs.getString("preferred_provider_code"),
                            rs.getString("sender_identity"),
                            (Integer) rs.getObject("daily_limit"),
                            (Integer) rs.getObject("monthly_limit"),
                            rs.getTimestamp("activated_at") == null
                                    ? null
                                    : rs.getTimestamp("activated_at").toInstant(),
                            rs.getTimestamp("suspended_at") == null
                                    ? null
                                    : rs.getTimestamp("suspended_at").toInstant());

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MerchantCommunicationCapabilityService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** All capability rows for one merchant. */
    public List<MerchantCapability> listForMerchant(long merchantId) {
        return jdbcTemplate.query(
                "SELECT id, merchant_id, channel, capability, status, routing_mode,"
                        + " preferred_provider_code, sender_identity, daily_limit, monthly_limit,"
                        + " activated_at, suspended_at FROM communication_merchant_capabilities"
                        + " WHERE merchant_id=:merchant_id ORDER BY channel ASC, capability ASC",
                new MapSqlParameterSource("merchant_id", merchantId),
                ROW_MAPPER);
    }

    /** One capability row, or empty when the merchant has none for that pair. */
    public Optional<MerchantCapability> find(long merchantId, String channel, String capability) {
        List<MerchantCapability> rows =
                jdbcTemplate.query(
                        "SELECT id, merchant_id, channel, capability, status, routing_mode,"
                                + " preferred_provider_code, sender_identity, daily_limit, monthly_limit,"
                                + " activated_at, suspended_at FROM communication_merchant_capabilities"
                                + " WHERE merchant_id=:merchant_id AND channel=:channel"
                                + " AND capability=:capability LIMIT 1",
                        params(merchantId, channel, capability),
                        ROW_MAPPER);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * The ACTIVE row for {@code channel}/{@code capability}, or throws — the entitlement gate
     * every dispatch path calls before accepting merchant traffic.
     */
    public MerchantCapability requireActive(long merchantId, String channel, String capability) {
        return find(merchantId, channel, capability)
                .filter(row -> "ACTIVE".equals(row.status()))
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "Communication capability "
                                                + channel
                                                + "/"
                                                + capability
                                                + " is not active for this merchant"));
    }

    /**
     * Requests or re-requests a capability. New rows start REQUESTED; re-requesting a DISABLED or
     * SUSPENDED row returns it to REQUESTED for operator review rather than self-reactivating.
     */
    public MerchantCapability request(long merchantId, String channel, String capability) {
        validatePair(channel, capability);
        jdbcTemplate.update(
                "INSERT INTO communication_merchant_capabilities"
                        + " (merchant_id, channel, capability, status) VALUES"
                        + " (:merchant_id, :channel, :capability, 'REQUESTED')"
                        + " ON DUPLICATE KEY UPDATE status='REQUESTED', activated_at=NULL,"
                        + " suspended_at=NULL",
                params(merchantId, channel, capability));
        return find(merchantId, channel, capability).orElseThrow();
    }

    /** Operator transition to any lifecycle state. Terminal states are guarded by SQL. */
    public MerchantCapability setStatus(
            long merchantId, String channel, String capability, String newStatus) {
        validatePair(channel, capability);
        String normalized = normalizeStatus(newStatus);
        MapSqlParameterSource p = params(merchantId, channel, capability).addValue("status", normalized);
        switch (normalized) {
            case "ACTIVE" -> p.addValue("now", java.time.Instant.now());
            case "SUSPENDED" -> p.addValue("now", java.time.Instant.now());
            default -> p.addValue("now", null);
        }
        int updated =
                jdbcTemplate.update(
                        "UPDATE communication_merchant_capabilities SET status=:status,"
                                + " activated_at=IF(:status='ACTIVE', NOW(), activated_at),"
                                + " suspended_at=IF(:status='SUSPENDED', NOW(), suspended_at)"
                                + " WHERE merchant_id=:merchant_id AND channel=:channel"
                                + " AND capability=:capability",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "No capability row to update for "
                            + channel
                            + "/"
                            + capability
                            + " - request it first");
        }
        return find(merchantId, channel, capability).orElseThrow();
    }

    /** Sets routing preference (AUTOMATIC/PREFERRED) and optional preferred provider code. */
    public MerchantCapability setRouting(
            long merchantId,
            String channel,
            String capability,
            String routingMode,
            String preferredProviderCode) {
        validatePair(channel, capability);
        String mode =
                routingMode == null || routingMode.isBlank()
                        ? "AUTOMATIC"
                        : routingMode.trim().toUpperCase();
        if (!"AUTOMATIC".equals(mode) && !"PREFERRED".equals(mode)) {
            throw new PaymentGatewayException("routingMode must be AUTOMATIC or PREFERRED");
        }
        jdbcTemplate.update(
                "UPDATE communication_merchant_capabilities SET routing_mode=:mode,"
                        + " preferred_provider_code=:preferred WHERE merchant_id=:merchant_id"
                        + " AND channel=:channel AND capability=:capability",
                params(merchantId, channel, capability)
                        .addValue("mode", mode)
                        .addValue("preferred", blankToNull(preferredProviderCode)));
        return find(merchantId, channel, capability).orElseThrow();
    }

    /** Sets daily/monthly send quotas (null clears the limit). */
    public MerchantCapability setQuotas(
            long merchantId,
            String channel,
            String capability,
            Integer dailyLimit,
            Integer monthlyLimit) {
        validatePair(channel, capability);
        if (dailyLimit != null && dailyLimit < 0) {
            throw new PaymentGatewayException("dailyLimit must be non-negative");
        }
        if (monthlyLimit != null && monthlyLimit < 0) {
            throw new PaymentGatewayException("monthlyLimit must be non-negative");
        }
        jdbcTemplate.update(
                "UPDATE communication_merchant_capabilities SET daily_limit=:daily,"
                        + " monthly_limit=:monthly WHERE merchant_id=:merchant_id"
                        + " AND channel=:channel AND capability=:capability",
                params(merchantId, channel, capability)
                        .addValue("daily", dailyLimit)
                        .addValue("monthly", monthlyLimit));
        return find(merchantId, channel, capability).orElseThrow();
    }

    private void validatePair(String channel, String capability) {
        if (channel == null || channel.isBlank()) {
            throw new PaymentGatewayException("channel is required");
        }
        if (capability == null || capability.isBlank()) {
            throw new PaymentGatewayException("capability is required");
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        return switch (normalized) {
            case "REQUESTED" -> "REQUESTED";
            case "CONFIGURING" -> "CONFIGURING";
            case "TESTING" -> "TESTING";
            case "ACTIVE" -> "ACTIVE";
            case "SUSPENDED" -> "SUSPENDED";
            case "DISABLED" -> "DISABLED";
            default -> throw new PaymentGatewayException("Unknown capability status: " + status);
        };
    }

    private MapSqlParameterSource params(long merchantId, String channel, String capability) {
        return new MapSqlParameterSource()
                .addValue("merchant_id", merchantId)
                .addValue("channel", channel.trim().toUpperCase())
                .addValue("capability", capability.trim().toUpperCase());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    /** One merchant capability activation row (V78). */
    public record MerchantCapability(
            long id,
            long merchantId,
            String channel,
            String capability,
            String status,
            String routingMode,
            String preferredProviderCode,
            String senderIdentity,
            Integer dailyLimit,
            Integer monthlyLimit,
            java.time.Instant activatedAt,
            java.time.Instant suspendedAt) {}
}
