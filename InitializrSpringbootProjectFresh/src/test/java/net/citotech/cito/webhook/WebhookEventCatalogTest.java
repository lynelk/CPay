package net.citotech.cito.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import net.citotech.cito.webhook.WebhookEventCatalog.EventDefinition;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Covers audit D6: the webhook event catalog must be a fixed, versioned set (not free-form
 * strings), and every entry's JSON Schema must actually parse - a typo in one of the hand-written
 * schema strings would otherwise only surface once a merchant tried to use it.
 */
class WebhookEventCatalogTest {

    @Test
    void knownEventTypesAreCaseAndWhitespaceInsensitive() {
        assertThat(WebhookEventCatalog.isKnown("payment.pending")).isTrue();
        assertThat(WebhookEventCatalog.isKnown("  PAYMENT.PENDING  ")).isTrue();
        assertThat(WebhookEventCatalog.isKnown("payout.completed")).isTrue();
    }

    @Test
    void unknownOrNullEventTypesAreRejected() {
        assertThat(WebhookEventCatalog.isKnown("payment.pendingg")).isFalse();
        assertThat(WebhookEventCatalog.isKnown(null)).isFalse();
        assertThat(WebhookEventCatalog.lookup("not.a.real.event")).isEmpty();
    }

    @Test
    void everyCatalogEntryHasAParseableJsonSchemaAndAQualifiedType() {
        for (EventDefinition definition : WebhookEventCatalog.all()) {
            assertThat(definition.type()).isNotBlank();
            assertThat(definition.version()).isGreaterThanOrEqualTo(1);
            assertThat(definition.qualifiedType()).isEqualTo(definition.type() + ".v" + definition.version());
            JSONObject schema = new JSONObject(definition.jsonSchema());
            assertThat(schema.getString("type")).isEqualTo("object");
            assertThat(schema.getJSONArray("required").length()).isGreaterThan(0);
        }
        assertThat(WebhookEventCatalog.all()).isNotEmpty();
    }
}
