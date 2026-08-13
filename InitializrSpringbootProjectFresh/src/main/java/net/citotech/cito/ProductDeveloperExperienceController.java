package net.citotech.cito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * P4 product polish and developer-experience foundation endpoints.
 *
 * <p>These endpoints provide durable UX state and API surfaces for onboarding, developer portal,
 * payment links, hosted checkout, invoices, channel journey guides, dashboards, sandbox guides, and
 * go-live checklists. Portal screens can consume these contracts directly.
 */
@RestController
@RequestMapping(path = "/api/v2/product-experience", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductDeveloperExperienceController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ProductDeveloperExperienceController(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/merchant/{merchantId}/onboarding")
    public Map<String, Object> getOnboarding(@PathVariable Long merchantId) {
        List<Map<String, Object>> workflows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM merchant_onboarding_workflows WHERE merchant_id = ?",
                        merchantId);
        if (workflows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Merchant onboarding workflow not found");
        }
        Long workflowId = ((Number) workflows.getFirst().get("id")).longValue();
        List<Map<String, Object>> steps =
                jdbcTemplate.queryForList(
                        "SELECT * FROM merchant_onboarding_steps WHERE workflow_id = ? ORDER BY sort_order, id",
                        workflowId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflow", workflows.getFirst());
        result.put("steps", steps);
        return result;
    }

    @Transactional
    @PostMapping("/merchant/{merchantId}/onboarding")
    public Map<String, Object> openOnboarding(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        String reference = reference("ONBOARD");
        jdbcTemplate.update(
                """
                INSERT INTO merchant_onboarding_workflows (
                    workflow_reference, merchant_id, current_step, status, completion_percentage,
                    blocked_reason, assigned_owner, metadata
                ) VALUES (?, ?, COALESCE(?, 'ACCOUNT_CREATED'), COALESCE(?, 'IN_PROGRESS'), COALESCE(?, 0), ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_step = VALUES(current_step),
                    status = VALUES(status),
                    completion_percentage = VALUES(completion_percentage),
                    blocked_reason = VALUES(blocked_reason),
                    assigned_owner = VALUES(assigned_owner),
                    metadata = VALUES(metadata),
                    updated_at = CURRENT_TIMESTAMP,
                    id = LAST_INSERT_ID(id)
                """,
                reference,
                merchantId,
                optionalString(body, "currentStep"),
                optionalString(body, "status"),
                body.get("completionPercentage"),
                optionalString(body, "blockedReason"),
                optionalString(body, "assignedOwner"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/merchant/{merchantId}/onboarding/steps")
    public Map<String, Object> upsertOnboardingStep(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        Long workflowId = ensureWorkflow(merchantId);
        jdbcTemplate.update(
                """
                INSERT INTO merchant_onboarding_steps (
                    workflow_id, step_code, step_name, status, required_for_go_live, completed_by,
                    completed_at, evidence_url, notes, sort_order, metadata
                ) VALUES (?, ?, ?, COALESCE(?, 'PENDING'), COALESCE(?, TRUE), ?,
                          CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END, ?, ?, COALESCE(?, 0), ?)
                ON DUPLICATE KEY UPDATE
                    step_name = VALUES(step_name),
                    status = VALUES(status),
                    required_for_go_live = VALUES(required_for_go_live),
                    completed_by = VALUES(completed_by),
                    completed_at = CASE WHEN VALUES(status) = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    evidence_url = VALUES(evidence_url),
                    notes = VALUES(notes),
                    sort_order = VALUES(sort_order),
                    metadata = VALUES(metadata),
                    updated_at = CURRENT_TIMESTAMP
                """,
                workflowId,
                requiredString(body, "stepCode"),
                requiredString(body, "stepName"),
                optionalString(body, "status"),
                body.get("requiredForGoLive"),
                optionalString(body, "completedBy"),
                optionalString(body, "status"),
                optionalString(body, "evidenceUrl"),
                optionalString(body, "notes"),
                body.get("sortOrder"),
                json(body));
        return accepted("onboarding_step_recorded", merchantId);
    }

    @Transactional
    @PostMapping("/developer/applications")
    public Map<String, Object> createDeveloperApplication(@RequestBody Map<String, Object> body) {
        String reference = reference("APP");
        jdbcTemplate.update(
                """
                INSERT INTO developer_portal_applications (
                    application_reference, merchant_id, name, environment, status, callback_url,
                    allowed_origins, created_by, metadata
                ) VALUES (?, ?, ?, COALESCE(?, 'SANDBOX'), COALESCE(?, 'ACTIVE'), ?, ?, ?, ?)
                """,
                reference,
                requiredLong(body, "merchantId"),
                requiredString(body, "name"),
                optionalString(body, "environment"),
                optionalString(body, "status"),
                optionalString(body, "callbackUrl"),
                optionalString(body, "allowedOrigins"),
                optionalString(body, "createdBy"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/developer/applications")
    public Map<String, Object> listDeveloperApplications(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql =
                new StringBuilder("SELECT * FROM developer_portal_applications WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("applications", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/developer/applications/{applicationId}/api-keys")
    public Map<String, Object> createApiKeyRecord(
            @PathVariable Long applicationId, @RequestBody Map<String, Object> body) {
        String reference = reference("KEY");
        jdbcTemplate.update(
                """
                INSERT INTO developer_portal_api_keys (
                    application_id, key_reference, key_label, public_key_pem, status, expires_at,
                    rotated_from_key_reference, created_by
                ) VALUES (?, ?, ?, ?, COALESCE(?, 'ACTIVE'), ?, ?, ?)
                """,
                applicationId,
                reference,
                optionalString(body, "keyLabel"),
                optionalString(body, "publicKeyPem"),
                optionalString(body, "status"),
                optionalString(body, "expiresAt"),
                optionalString(body, "rotatedFromKeyReference"),
                optionalString(body, "createdBy"));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/payment-links")
    public Map<String, Object> createPaymentLink(@RequestBody Map<String, Object> body) {
        String reference = reference("PLINK");
        jdbcTemplate.update(
                """
                INSERT INTO payment_links_v2 (
                    payment_link_reference, merchant_id, token_hash, title, description, amount, currency_code,
                    country_code, status, reusable, partial_payment_allowed, max_uses, expires_at, created_by, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'ACTIVE'), COALESCE(?, FALSE), COALESCE(?, FALSE),
                          ?, ?, ?, ?)
                """,
                reference,
                requiredLong(body, "merchantId"),
                requiredString(body, "tokenHash"),
                optionalString(body, "title"),
                optionalString(body, "description"),
                body.get("amount"),
                requiredString(body, "currencyCode"),
                optionalString(body, "countryCode"),
                optionalString(body, "status"),
                body.get("reusable"),
                body.get("partialPaymentAllowed"),
                body.get("maxUses"),
                optionalString(body, "expiresAt"),
                optionalString(body, "createdBy"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/payment-links")
    public Map<String, Object> listPaymentLinks(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM payment_links_v2 WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("paymentLinks", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/checkout/sessions")
    public Map<String, Object> createCheckoutSession(@RequestBody Map<String, Object> body) {
        String reference = reference("CHECKOUT");
        jdbcTemplate.update(
                """
                INSERT INTO hosted_checkout_sessions (
                    checkout_reference, merchant_id, payment_link_id, invoice_id, token_hash, customer_msisdn,
                    customer_email, amount, currency_code, country_code, selected_channel, status, expires_at,
                    paid_transaction_reference, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'CREATED'), ?, ?, ?)
                """,
                reference,
                requiredLong(body, "merchantId"),
                optionalLong(body, "paymentLinkId"),
                optionalLong(body, "invoiceId"),
                requiredString(body, "tokenHash"),
                optionalString(body, "customerMsisdn"),
                optionalString(body, "customerEmail"),
                body.get("amount"),
                requiredString(body, "currencyCode"),
                optionalString(body, "countryCode"),
                optionalString(body, "selectedChannel"),
                optionalString(body, "status"),
                requiredString(body, "expiresAt"),
                optionalString(body, "paidTransactionReference"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/invoices")
    public Map<String, Object> createInvoice(@RequestBody Map<String, Object> body) {
        String reference = reference("INV");
        jdbcTemplate.update(
                """
                INSERT INTO merchant_invoices_v2 (
                    invoice_reference, merchant_id, invoice_number, customer_name, customer_email, customer_msisdn,
                    currency_code, subtotal_amount, tax_amount, total_amount, amount_paid, status, due_date,
                    partial_payment_allowed, payment_token_hash, created_by, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0),
                          COALESCE(?, 'DRAFT'), ?, COALESCE(?, FALSE), ?, ?, ?)
                """,
                reference,
                requiredLong(body, "merchantId"),
                requiredString(body, "invoiceNumber"),
                optionalString(body, "customerName"),
                optionalString(body, "customerEmail"),
                optionalString(body, "customerMsisdn"),
                requiredString(body, "currencyCode"),
                body.get("subtotalAmount"),
                body.get("taxAmount"),
                body.get("totalAmount"),
                body.get("amountPaid"),
                optionalString(body, "status"),
                optionalString(body, "dueDate"),
                body.get("partialPaymentAllowed"),
                optionalString(body, "paymentTokenHash"),
                optionalString(body, "createdBy"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/invoices/{invoiceId}/line-items")
    public Map<String, Object> addInvoiceLineItem(
            @PathVariable Long invoiceId, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO merchant_invoice_line_items (
                    invoice_id, description, quantity, unit_amount, tax_amount, line_total, sort_order, metadata
                ) VALUES (?, ?, COALESCE(?, 1), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), ?)
                """,
                invoiceId,
                requiredString(body, "description"),
                body.get("quantity"),
                body.get("unitAmount"),
                body.get("taxAmount"),
                body.get("lineTotal"),
                body.get("sortOrder"),
                json(body));
        return accepted("invoice_line_item_recorded", invoiceId);
    }

    @GetMapping("/invoices")
    public Map<String, Object> listInvoices(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM merchant_invoices_v2 WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("invoices", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/channel-journeys")
    public Map<String, Object> publishChannelJourney(@RequestBody Map<String, Object> body) {
        String reference = reference("JOURNEY");
        jdbcTemplate.update(
                """
                INSERT INTO channel_journey_guides (
                    guide_reference, channel_code, country_code, environment, title, status, journey_json, published_by,
                    published_at
                ) VALUES (?, ?, ?, COALESCE(?, 'SANDBOX'), ?, COALESCE(?, 'DRAFT'), ?, ?,
                          CASE WHEN COALESCE(?, 'DRAFT') = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """,
                reference,
                requiredString(body, "channelCode"),
                optionalString(body, "countryCode"),
                optionalString(body, "environment"),
                requiredString(body, "title"),
                optionalString(body, "status"),
                jsonBodyField(body, "journey"),
                optionalString(body, "publishedBy"),
                optionalString(body, "status"));
        return created(reference, lastInsertId());
    }

    @GetMapping("/channel-journeys")
    public Map<String, Object> listChannelJourneys(
            @RequestParam(name = "channelCode", required = false) String channelCode,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM channel_journey_guides WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (channelCode != null && !channelCode.isBlank()) {
            sql.append(" AND channel_code = ?");
            params.add(channelCode);
        }
        sql.append(" ORDER BY updated_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("channelJourneys", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/dashboard/widgets")
    public Map<String, Object> upsertDashboardWidget(@RequestBody Map<String, Object> body) {
        String reference = reference("WIDGET");
        jdbcTemplate.update(
                """
                INSERT INTO dashboard_widgets (widget_reference, audience, widget_code, title, status, config_json, sort_order)
                VALUES (?, ?, ?, ?, COALESCE(?, 'ACTIVE'), ?, COALESCE(?, 0))
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    status = VALUES(status),
                    config_json = VALUES(config_json),
                    sort_order = VALUES(sort_order),
                    updated_at = CURRENT_TIMESTAMP
                """,
                reference,
                requiredString(body, "audience"),
                requiredString(body, "widgetCode"),
                requiredString(body, "title"),
                optionalString(body, "status"),
                jsonBodyField(body, "config"),
                body.get("sortOrder"));
        return accepted("dashboard_widget_recorded", requiredString(body, "widgetCode"));
    }

    @GetMapping("/dashboard/widgets")
    public Map<String, Object> listDashboardWidgets(
            @RequestParam(name = "audience", required = false) String audience) {
        if (audience == null || audience.isBlank()) {
            return ok(
                    "widgets",
                    jdbcTemplate.queryForList(
                            "SELECT * FROM dashboard_widgets ORDER BY audience, sort_order, widget_code"));
        }
        return ok(
                "widgets",
                jdbcTemplate.queryForList(
                        "SELECT * FROM dashboard_widgets WHERE audience = ? ORDER BY sort_order, widget_code",
                        audience));
    }

    @Transactional
    @PostMapping("/sandbox-guides")
    public Map<String, Object> createSandboxGuide(@RequestBody Map<String, Object> body) {
        String reference = reference("SANDBOX");
        jdbcTemplate.update(
                """
                INSERT INTO sandbox_guides (
                    guide_reference, title, audience, status, content_markdown, sample_payload_json, published_by,
                    published_at
                ) VALUES (?, ?, COALESCE(?, 'DEVELOPER'), COALESCE(?, 'DRAFT'), ?, ?, ?,
                          CASE WHEN COALESCE(?, 'DRAFT') = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """,
                reference,
                requiredString(body, "title"),
                optionalString(body, "audience"),
                optionalString(body, "status"),
                requiredString(body, "contentMarkdown"),
                jsonBodyField(body, "samplePayload"),
                optionalString(body, "publishedBy"),
                optionalString(body, "status"));
        return created(reference, lastInsertId());
    }

    @GetMapping("/sandbox-guides")
    public Map<String, Object> listSandboxGuides(
            @RequestParam(name = "status", required = false) String status) {
        if (status == null || status.isBlank()) {
            return ok(
                    "sandboxGuides",
                    jdbcTemplate.queryForList(
                            "SELECT * FROM sandbox_guides ORDER BY updated_at DESC"));
        }
        return ok(
                "sandboxGuides",
                jdbcTemplate.queryForList(
                        "SELECT * FROM sandbox_guides WHERE status = ? ORDER BY updated_at DESC",
                        status));
    }

    @Transactional
    @PostMapping("/go-live/{merchantId}")
    public Map<String, Object> openGoLiveChecklist(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        String reference = reference("GOLIVE");
        jdbcTemplate.update(
                """
                INSERT INTO go_live_checklists (
                    checklist_reference, merchant_id, status, requested_by, reviewed_by, approved_by, blocked_reason, metadata
                ) VALUES (?, ?, COALESCE(?, 'OPEN'), ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    requested_by = VALUES(requested_by),
                    reviewed_by = VALUES(reviewed_by),
                    approved_by = VALUES(approved_by),
                    blocked_reason = VALUES(blocked_reason),
                    metadata = VALUES(metadata),
                    updated_at = CURRENT_TIMESTAMP,
                    id = LAST_INSERT_ID(id)
                """,
                reference,
                merchantId,
                optionalString(body, "status"),
                optionalString(body, "requestedBy"),
                optionalString(body, "reviewedBy"),
                optionalString(body, "approvedBy"),
                optionalString(body, "blockedReason"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/go-live/{merchantId}/items")
    public Map<String, Object> upsertGoLiveItem(
            @PathVariable Long merchantId, @RequestBody Map<String, Object> body) {
        Long checklistId = ensureGoLiveChecklist(merchantId);
        jdbcTemplate.update(
                """
                INSERT INTO go_live_checklist_items (
                    checklist_id, item_code, item_name, status, required, evidence_url, completed_by,
                    completed_at, notes, sort_order, metadata
                ) VALUES (?, ?, ?, COALESCE(?, 'PENDING'), COALESCE(?, TRUE), ?, ?,
                          CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END, ?, COALESCE(?, 0), ?)
                ON DUPLICATE KEY UPDATE
                    item_name = VALUES(item_name),
                    status = VALUES(status),
                    required = VALUES(required),
                    evidence_url = VALUES(evidence_url),
                    completed_by = VALUES(completed_by),
                    completed_at = CASE WHEN VALUES(status) = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    notes = VALUES(notes),
                    sort_order = VALUES(sort_order),
                    metadata = VALUES(metadata)
                """,
                checklistId,
                requiredString(body, "itemCode"),
                requiredString(body, "itemName"),
                optionalString(body, "status"),
                body.get("required"),
                optionalString(body, "evidenceUrl"),
                optionalString(body, "completedBy"),
                optionalString(body, "status"),
                optionalString(body, "notes"),
                body.get("sortOrder"),
                json(body));
        return accepted("go_live_item_recorded", merchantId);
    }

    @GetMapping("/go-live/{merchantId}")
    public Map<String, Object> getGoLiveChecklist(@PathVariable Long merchantId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM go_live_checklists WHERE merchant_id = ?", merchantId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Go-live checklist not found");
        }
        Long checklistId = ((Number) rows.getFirst().get("id")).longValue();
        List<Map<String, Object>> items =
                jdbcTemplate.queryForList(
                        "SELECT * FROM go_live_checklist_items WHERE checklist_id = ? ORDER BY sort_order, id",
                        checklistId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checklist", rows.getFirst());
        result.put("items", items);
        return result;
    }

    private Long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private Long ensureWorkflow(Long merchantId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id FROM merchant_onboarding_workflows WHERE merchant_id = ?",
                        merchantId);
        if (!rows.isEmpty()) {
            return ((Number) rows.getFirst().get("id")).longValue();
        }
        String reference = reference("ONBOARD");
        jdbcTemplate.update(
                """
                INSERT INTO merchant_onboarding_workflows (workflow_reference, merchant_id)
                VALUES (?, ?)
                """,
                reference,
                merchantId);
        return lastInsertId();
    }

    private Long ensureGoLiveChecklist(Long merchantId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id FROM go_live_checklists WHERE merchant_id = ?", merchantId);
        if (!rows.isEmpty()) {
            return ((Number) rows.getFirst().get("id")).longValue();
        }
        String reference = reference("GOLIVE");
        jdbcTemplate.update(
                """
                INSERT INTO go_live_checklists (checklist_reference, merchant_id)
                VALUES (?, ?)
                """,
                reference,
                merchantId);
        return lastInsertId();
    }

    private Map<String, Object> ok(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> created(String reference, Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("reference", reference);
        result.put("status", "created");
        return result;
    }

    private Map<String, Object> accepted(String action, Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("id", id);
        result.put("status", "accepted");
        return result;
    }

    private String reference(String prefix) {
        return prefix
                + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 250));
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required field: " + field);
        }
        return value.toString();
    }

    private String optionalString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private Long requiredLong(Map<String, Object> body, String field) {
        Long value = optionalLong(body, field);
        if (value == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required field: " + field);
        }
        return value;
    }

    private Long optionalLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", e);
        }
    }

    private String jsonBodyField(Map<String, Object> body, String field) {
        Object nested = body.get(field);
        try {
            return objectMapper.writeValueAsString(nested == null ? Map.of() : nested);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid JSON payload for field: " + field, e);
        }
    }
}
