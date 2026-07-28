package net.citotech.cito.webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versioned catalog of merchant webhook event types (audit D6). Event types were previously
 * free-form strings accepted by {@link MerchantWebhookService#registerEndpoint}: a typo'd
 * subscription would silently never fire, and there was no documented, machine-checkable shape
 * for what a delivered payload actually contains. Each entry pins a stable type name, an integer
 * schema version, and a JSON Schema (draft 2020-12) describing the envelope merchants receive -
 * new fields can be added to a version's envelope (additive, non-breaking) but a breaking change
 * to an existing event must land as a new version rather than mutating one in place.
 */
public final class WebhookEventCatalog {

    public record EventDefinition(String type, int version, String description, String jsonSchema) {
        public String qualifiedType() {
            return type + ".v" + version;
        }
    }

    private static final Map<String, EventDefinition> BY_TYPE = new LinkedHashMap<>();

    static {
        register("payment.pending", 1, "A collection request was submitted to the provider and is awaiting confirmation.");
        register("payment.completed", 1, "A collection was confirmed successful by the provider.");
        register("payment.failed", 1, "A collection was confirmed failed by the provider.");
        register("payout.pending", 1, "A payout request was submitted to the provider and is awaiting confirmation.");
        register("payout.completed", 1, "A payout was confirmed successful by the provider.");
        register("payout.failed", 1, "A payout was confirmed failed by the provider.");
        register("refund.completed", 1, "A refund was confirmed successful by the provider.");
        register("refund.failed", 1, "A refund was confirmed failed by the provider.");
    }

    private WebhookEventCatalog() {
    }

    private static void register(String type, int version, String description) {
        BY_TYPE.put(type, new EventDefinition(type, version, description, envelopeSchema()));
    }

    public static Optional<EventDefinition> lookup(String eventType) {
        if (eventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_TYPE.get(eventType.trim().toLowerCase()));
    }

    public static boolean isKnown(String eventType) {
        return lookup(eventType).isPresent();
    }

    public static List<EventDefinition> all() {
        return List.copyOf(BY_TYPE.values());
    }

    private static String envelopeSchema() {
        return "{"
            + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
            + "\"type\":\"object\","
            + "\"required\":[\"eventId\",\"eventType\",\"eventVersion\",\"createdAt\",\"merchantNumber\",\"reference\",\"transactionId\",\"status\"],"
            + "\"properties\":{"
            + "\"eventId\":{\"type\":\"string\",\"description\":\"Unique id of this event, stable across delivery retries.\"},"
            + "\"eventType\":{\"type\":\"string\",\"description\":\"One of the types listed in the webhook event catalog.\"},"
            + "\"eventVersion\":{\"type\":\"integer\",\"description\":\"Envelope schema version for eventType.\"},"
            + "\"createdAt\":{\"type\":\"string\",\"format\":\"date-time\"},"
            + "\"merchantNumber\":{\"type\":\"string\"},"
            + "\"reference\":{\"type\":\"string\",\"description\":\"Merchant-supplied request reference.\"},"
            + "\"transactionId\":{\"type\":\"string\",\"description\":\"CPay-assigned unique transaction id.\"},"
            + "\"status\":{\"type\":\"string\"},"
            + "\"amount\":{\"type\":\"string\"},"
            + "\"currency\":{\"type\":\"string\"}"
            + "}"
            + "}";
    }
}
