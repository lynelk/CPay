package net.citotech.cito.webhook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.citotech.cito.webhook.WebhookEventCatalog.EventDefinition;
import org.json.JSONObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only listing of the versioned webhook event catalog (audit D6), so a merchant integrating
 * against webhooks can discover valid eventType values and each one's payload JSON Schema without
 * needing admin credentials - unlike endpoint registration itself, this is non-sensitive reference
 * data, so it lives under the un-authenticated /api/v2 path rather than /api/v2/admin.
 *
 * The schema is exposed as a plain Map (via org.json, not com.fasterxml.jackson.databind.JsonNode)
 * because this project's Spring MVC response serialization runs on Jackson 3 (tools.jackson.databind);
 * a Jackson-2 JsonNode isn't a type Jackson 3 recognizes, so it would get bean-introspected into
 * garbage (isArray/isObject/... boolean fields) instead of serialized as the nested JSON it is.
 */
@RestController
@RequestMapping(path = "/api/v2/webhooks")
public class WebhookEventCatalogController {

    @GetMapping(path = "/events")
    public List<CatalogEntryResponse> events() {
        List<CatalogEntryResponse> response = new ArrayList<>();
        for (EventDefinition definition : WebhookEventCatalog.all()) {
            response.add(toResponse(definition));
        }
        return response;
    }

    private CatalogEntryResponse toResponse(EventDefinition definition) {
        Map<String, Object> schema = new JSONObject(definition.jsonSchema()).toMap();
        return new CatalogEntryResponse(definition.type(), definition.version(), definition.qualifiedType(),
                definition.description(), schema);
    }

    public record CatalogEntryResponse(String eventType, int version, String qualifiedType, String description,
            Map<String, Object> schema) {
    }
}
