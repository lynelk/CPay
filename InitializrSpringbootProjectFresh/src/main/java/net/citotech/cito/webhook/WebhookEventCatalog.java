package net.citotech.cito.webhook;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Versioned catalog of merchant webhook event types (audit D6). Event types were previously
 * free-form strings accepted by {@link MerchantWebhookService#registerEndpoint}: a typo'd
 * subscription would silently never fire, and there was no documented, machine-checkable shape for
 * what a delivered payload actually contains. Each entry pins a stable type name, an integer schema
 * version, and a JSON Schema (draft 2020-12) describing the envelope merchants receive - new fields
 * can be added to a version's envelope (additive, non-breaking) but a breaking change to an
 * existing event must land as a new version rather than mutating one in place.
 */
public final class WebhookEventCatalog {

    public record EventDefinition(String type, int version, String description, String jsonSchema) {
        public String qualifiedType() {
            return type + ".v" + version;
        }
    }

    private static final Map<String, EventDefinition> BY_TYPE = new LinkedHashMap<>();

    /**
     * The envelope shared by every current event type - all are genuine transaction events, so
     * {@code transactionId}/{@code amount}/{@code currency} are required. ADR 0006: non-
     * transactional billing event types (a balance threshold, a usage meter, a subscription change)
     * will register their own, differently-shaped schema via {@link #register(String, int, String,
     * String)} instead of this one.
     */
    private static final String TRANSACTIONAL_ENVELOPE_SCHEMA = transactionalEnvelopeSchema();

    /**
     * The first non-transactional envelope (ADR 0006, billing Slice 24): a billing invoice is not a
     * single provider transaction, so it has no {@code transactionId} and carries {@code invoiceId}
     * instead of {@code reference}. {@code amount}/{@code currency} stay present since an invoice
     * genuinely has both, but neither is required - a draft/proforma invoice event may not have a
     * final amount yet.
     */
    private static final String INVOICE_ENVELOPE_SCHEMA = invoiceEnvelopeSchema();

    /** Validation events are workflow events, not merchant transactions — they carry a
     * {@code caseId} instead of {@code transactionId} (same non-transactional precedent as the
     * invoice envelope, ADR 0006). */
    private static final String VALIDATION_ENVELOPE_SCHEMA = validationEnvelopeSchema();

    static {
        // Original transactional/invoice events keep their registration order - the catalog
        // endpoint exposes insertion order and consumers/tests rely on the stable listing.
        register(
                "payment.pending",
                1,
                "A collection request was submitted to the provider and is awaiting confirmation.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "payment.completed",
                1,
                "A collection was confirmed successful by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "payment.failed",
                1,
                "A collection was confirmed failed by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "payout.pending",
                1,
                "A payout request was submitted to the provider and is awaiting confirmation.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "payout.completed",
                1,
                "A payout was confirmed successful by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "payout.failed",
                1,
                "A payout was confirmed failed by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "refund.completed",
                1,
                "A refund was confirmed successful by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "refund.failed",
                1,
                "A refund was confirmed failed by the provider.",
                TRANSACTIONAL_ENVELOPE_SCHEMA);
        register(
                "invoice.issued",
                1,
                "A billing invoice was issued to the merchant.",
                INVOICE_ENVELOPE_SCHEMA);
        // Validation workflow events (Track B Phase 6) appended after the established entries.
        register(
                "validation.case.created",
                1,
                "A validation case was created for a merchant.",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.processing",
                1,
                "A validation case moved into processing.",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.check.updated",
                1,
                "A validation check changed status (passed, failed, pending, error).",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.review_required",
                1,
                "A validation case requires manual review.",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.verified",
                1,
                "A validation case completed with a verified decision.",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.rejected",
                1,
                "A validation case completed with a rejected decision.",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.inconclusive",
                1,
                "A validation case completed inconclusively (evidence insufficient or technical error).",
                VALIDATION_ENVELOPE_SCHEMA);
        register(
                "validation.case.expired",
                1,
                "A validation case expired before completion.",
                VALIDATION_ENVELOPE_SCHEMA);
    }

    private WebhookEventCatalog() {}

    /** ADR 0006: every registered type supplies its own {@code jsonSchema} explicitly. */
    private static void register(String type, int version, String description, String jsonSchema) {
        BY_TYPE.put(type, new EventDefinition(type, version, description, jsonSchema));
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

    private static String transactionalEnvelopeSchema() {
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

    private static String invoiceEnvelopeSchema() {
        return "{"
                + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"type\":\"object\","
                + "\"required\":[\"eventId\",\"eventType\",\"eventVersion\",\"createdAt\",\"merchantNumber\",\"invoiceId\",\"status\"],"
                + "\"properties\":{"
                + "\"eventId\":{\"type\":\"string\",\"description\":\"Unique id of this event, stable across delivery retries.\"},"
                + "\"eventType\":{\"type\":\"string\",\"description\":\"One of the types listed in the webhook event catalog.\"},"
                + "\"eventVersion\":{\"type\":\"integer\",\"description\":\"Envelope schema version for eventType.\"},"
                + "\"createdAt\":{\"type\":\"string\",\"format\":\"date-time\"},"
                + "\"merchantNumber\":{\"type\":\"string\"},"
                + "\"invoiceId\":{\"type\":\"string\",\"description\":\"CPay-assigned billing invoice id.\"},"
                + "\"status\":{\"type\":\"string\"},"
                + "\"amount\":{\"type\":\"string\"},"
                + "\"currency\":{\"type\":\"string\"},"
                + "\"dueAt\":{\"type\":\"string\",\"format\":\"date-time\"}"
                + "}"
                + "}";
    }

    private static String validationEnvelopeSchema() {
        return "{"
                + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"type\":\"object\","
                + "\"required\":[\"eventId\",\"eventType\",\"eventVersion\",\"createdAt\",\"merchantNumber\",\"caseId\",\"status\"],"
                + "\"properties\":{"
                + "\"eventId\":{\"type\":\"string\",\"description\":\"Unique id of this event, stable across delivery retries.\"},"
                + "\"eventType\":{\"type\":\"string\",\"description\":\"One of the types listed in the webhook event catalog.\"},"
                + "\"eventVersion\":{\"type\":\"integer\",\"description\":\"Envelope schema version for eventType.\"},"
                + "\"createdAt\":{\"type\":\"string\",\"format\":\"date-time\"},"
                + "\"merchantNumber\":{\"type\":\"string\"},"
                + "\"caseId\":{\"type\":\"string\",\"description\":\"CPay-assigned validation case id.\"},"
                + "\"merchantReference\":{\"type\":\"string\",\"description\":\"Merchant-supplied case reference.\"},"
                + "\"status\":{\"type\":\"string\"},"
                + "\"capability\":{\"type\":\"string\",\"description\":\"Validation capability the event relates to.\"},"
                + "\"reasonCode\":{\"type\":\"string\",\"description\":\"Normalized decision/reason code (e.g. NIN_MATCH, VERIFICATION_INCONCLUSIVE).\"}"
                + "}"
                + "}";
    }
}
