package net.citotech.cito.communication.campaign;

import java.util.List;
import java.util.Map;
import net.citotech.cito.communication.delivery.CommunicationDeliveryDispatcher;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Campaign batches for {@code communication_campaigns}/{@code communication_campaign_items} (V52,
 * track B4, ISO domain mapping: communication/campaign). One campaign is one merchant + channel +
 * template + recipient list under a single scheduling intent. {@link #start} flips a DRAFT campaign
 * to QUEUED and stages one item per recipient; the ShedLock-guarded {@link #sweepDue} then
 * dispatches each PENDING item through {@link CommunicationDeliveryDispatcher} and advances the
 * campaign progress counters until every item is terminal.
 *
 * <p>Deliberately single-recipient-per-item: per-item delivery status, retry and billing visibility
 * (via the V53 delivery log) are first-class instead of being hidden inside one batched send, which
 * is what makes the communication meter relay (B5b) per-message accurate.
 */
@Service
public class CampaignService {

    private static final int DEFAULT_SWEEP_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CommunicationDeliveryDispatcher dispatcher;

    public CampaignService(
            NamedParameterJdbcTemplate jdbcTemplate, CommunicationDeliveryDispatcher dispatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
    }

    public long create(
            long merchantId,
            String name,
            String channel,
            String templateKey,
            List<String> recipients,
            String createdBy) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId is required");
        }
        if (name == null || name.isBlank()) {
            throw new PaymentGatewayException("name is required");
        }
        if (recipients == null || recipients.isEmpty()) {
            throw new PaymentGatewayException("at least one recipient is required");
        }
        if (recipients.size() > 5000) {
            throw new PaymentGatewayException("campaign recipients must be 5000 or fewer");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("name", name.trim());
        p.addValue("channel", normalizeChannel(channel));
        p.addValue("template_key", templateKey);
        p.addValue("total_recipients", recipients.size());
        p.addValue("created_by", createdBy == null ? "system" : createdBy.trim());
        jdbcTemplate.update(
                "INSERT INTO communication_campaigns (merchant_id, name, channel, template_key,"
                        + " total_recipients, status, created_by) VALUES (:merchant_id, :name,"
                        + " :channel, :template_key, :total_recipients, 'DRAFT', :created_by)",
                p);
        long campaignId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM communication_campaigns WHERE merchant_id=:merchant_id"
                                + " AND name=:name ORDER BY id DESC LIMIT 1",
                        p,
                        Long.class);
        // Stage items in one batch (no recipients hard-coded beyond the campaign row itself).
        for (String recipient : recipients) {
            MapSqlParameterSource item = new MapSqlParameterSource();
            item.addValue("campaign_id", campaignId);
            item.addValue("recipient", recipient.trim());
            jdbcTemplate.update(
                    "INSERT INTO communication_campaign_items (campaign_id, recipient, status)"
                            + " VALUES (:campaign_id, :recipient, 'PENDING')",
                    item);
        }
        return campaignId;
    }

    /** Flips a DRAFT campaign to QUEUED for the next sweep. Returns rows affected. */
    public int queue(long campaignId) {
        return jdbcTemplate.update(
                "UPDATE communication_campaigns SET status='QUEUED' WHERE id=:id AND status='DRAFT'",
                new MapSqlParameterSource("id", campaignId));
    }

    /** Dispatches up to {@code limit} PENDING campaign items across due campaigns. */
    public int sweepDue(int limit) {
        int cap = Math.max(1, Math.min(limit <= 0 ? DEFAULT_SWEEP_LIMIT : limit, 1000));
        List<ItemRow> due = dueItems(cap);
        int processed = 0;
        for (ItemRow item : due) {
            try {
                processItem(item);
                processed++;
            } catch (Exception ex) {
                jdbcTemplate.update(
                        "UPDATE communication_campaign_items SET status='FAILED', trace=:trace"
                                + " WHERE id=:id AND status='PENDING'",
                        new MapSqlParameterSource("id", item.id())
                                .addValue("trace", truncate(ex.getMessage(), 500)));
            }
        }
        return processed;
    }

    private void processItem(ItemRow item) {
        CampaignRow campaign = campaign(item.campaignId());
        if (campaign == null) {
            return;
        }
        CommunicationDeliveryDispatcher.DeliveryOutcome outcome =
                dispatcher.dispatch(
                        campaign.merchantId(),
                        campaign.channel(),
                        item.recipient(),
                        null,
                        item.messageBody() == null ? "" : item.messageBody(),
                        null,
                        item.id());
        jdbcTemplate.update(
                "UPDATE communication_campaign_items SET status=:status, trace=:trace WHERE id=:id"
                        + " AND status='PENDING'",
                new MapSqlParameterSource("id", item.id())
                        .addValue("status", outcome.status().name())
                        .addValue("trace", "delivery " + outcome.deliveryId()));
        advanceProgress(campaign.id());
    }

    private void advanceProgress(long campaignId) {
        jdbcTemplate.update(
                "UPDATE communication_campaigns SET processed_recipients=(SELECT COUNT(*) FROM"
                        + " communication_campaign_items WHERE campaign_id=:campaign_id AND status"
                        + " IN ('SENT','REJECTED','FAILED')) WHERE id=:campaign_id",
                new MapSqlParameterSource("campaign_id", campaignId));
    }

    private List<ItemRow> dueItems(int limit) {
        return jdbcTemplate.query(
                "SELECT i.id, i.campaign_id, i.recipient, i.message_body FROM"
                        + " communication_campaign_items i JOIN communication_campaigns c"
                        + " ON c.id=i.campaign_id WHERE i.status='PENDING' AND c.status IN"
                        + " ('QUEUED','RUNNING') ORDER BY i.id ASC LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                (rs, rowNum) ->
                        new ItemRow(
                                rs.getLong("id"),
                                rs.getLong("campaign_id"),
                                rs.getString("recipient"),
                                rs.getString("message_body")));
    }

    private CampaignRow campaign(long campaignId) {
        List<CampaignRow> rows =
                jdbcTemplate.query(
                        "SELECT id, merchant_id, name, channel, template_key, status FROM"
                                + " communication_campaigns WHERE id=:id LIMIT 1",
                        new MapSqlParameterSource("id", campaignId),
                        (rs, rowNum) ->
                                new CampaignRow(
                                        rs.getLong("id"),
                                        rs.getLong("merchant_id"),
                                        rs.getString("name"),
                                        rs.getString("channel"),
                                        rs.getString("template_key"),
                                        rs.getString("status")));
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<Map<String, Object>> list(long merchantId, int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("limit", Math.max(1, Math.min(limit, 200)));
        return jdbcTemplate.queryForList(
                "SELECT id, merchant_id, name, channel, template_key, total_recipients,"
                        + " processed_recipients, status, scheduled_at, started_at, completed_at,"
                        + " created_by, created_at FROM communication_campaigns"
                        + " WHERE merchant_id=:merchant_id ORDER BY id DESC LIMIT :limit",
                p);
    }

    private String normalizeChannel(String channel) {
        return channel == null || channel.isBlank() ? "SMS" : channel.trim().toUpperCase();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    // Package-visible so the same-package unit test can build real row types for the sweep/campaign
    // lookups. Nested records are implicitly static; no enclosing-instance coupling.
    record ItemRow(long id, long campaignId, String recipient, String messageBody) {}

    record CampaignRow(
            long id,
            long merchantId,
            String name,
            String channel,
            String templateKey,
            String status) {}
}
