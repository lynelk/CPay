package net.citotech.cito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P3 regional expansion and cross-border foundation endpoints.
 *
 * The controller exposes first-class corridor, beneficiary, FX quote, transfer,
 * settlement and treasury evidence surfaces over the Flyway-backed regional
 * schema. Payment execution remains owned by existing payment/gateway services;
 * these endpoints establish the regulated workflow envelope around that money
 * movement.
 */
@RestController
@RequestMapping(path = "/api/v2", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class CrossBorderController {

    private final JdbcTemplate jdbc;

    public CrossBorderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/cross-border/corridors")
    public ResponseEntity<Map<String, Object>> listCorridors(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(ok("corridors", jdbc.queryForList(
                    "select * from corridors where status = ? order by corridor_code asc", status)));
        }
        return ResponseEntity.ok(ok("corridors", jdbc.queryForList("select * from corridors order by corridor_code asc")));
    }

    @PostMapping("/admin/cross-border/corridors")
    public ResponseEntity<Map<String, Object>> createOrUpdateCorridor(@RequestBody Map<String, Object> body) {
        require(body, "corridorCode");
        require(body, "sourceCountryCode");
        require(body, "destinationCountryCode");
        require(body, "sourceCurrencyCode");
        require(body, "destinationCurrencyCode");
        require(body, "displayName");
        jdbc.update("insert into corridors (corridor_code, source_country_code, destination_country_code, "
                        + "source_currency_code, destination_currency_code, display_name, status, risk_level, "
                        + "settlement_model, compliance_policy_code) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update source_country_code = values(source_country_code), "
                        + "destination_country_code = values(destination_country_code), "
                        + "source_currency_code = values(source_currency_code), destination_currency_code = values(destination_currency_code), "
                        + "display_name = values(display_name), status = values(status), risk_level = values(risk_level), "
                        + "settlement_model = values(settlement_model), compliance_policy_code = values(compliance_policy_code)",
                body.get("corridorCode"), body.get("sourceCountryCode"), body.get("destinationCountryCode"),
                body.get("sourceCurrencyCode"), body.get("destinationCurrencyCode"), body.get("displayName"),
                valueOr(body, "status", "DRAFT"), valueOr(body, "riskLevel", "MEDIUM"),
                valueOr(body, "settlementModel", "PARTNER_LED"), body.get("compliancePolicyCode"));
        return ResponseEntity.ok(ok("corridorCode", body.get("corridorCode")));
    }

    @PostMapping("/admin/cross-border/corridor-routes")
    public ResponseEntity<Map<String, Object>> createOrUpdateCorridorRoute(@RequestBody Map<String, Object> body) {
        require(body, "corridorCode");
        require(body, "routeCode");
        require(body, "providerCode");
        require(body, "deliveryMethod");
        Long corridorId = lookupId("select id from corridors where corridor_code = ?", body.get("corridorCode"));
        jdbc.update("insert into corridor_routes (corridor_id, route_code, provider_code, partner_code, delivery_method, "
                        + "priority, enabled, supports_individuals, supports_organisations, min_amount, max_amount, "
                        + "expected_delivery_minutes) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update provider_code = values(provider_code), partner_code = values(partner_code), "
                        + "delivery_method = values(delivery_method), priority = values(priority), enabled = values(enabled), "
                        + "supports_individuals = values(supports_individuals), supports_organisations = values(supports_organisations), "
                        + "min_amount = values(min_amount), max_amount = values(max_amount), "
                        + "expected_delivery_minutes = values(expected_delivery_minutes)",
                corridorId, body.get("routeCode"), body.get("providerCode"), body.get("partnerCode"),
                body.get("deliveryMethod"), valueOr(body, "priority", 100), booleanValue(valueOr(body, "enabled", true)),
                booleanValue(valueOr(body, "supportsIndividuals", true)),
                booleanValue(valueOr(body, "supportsOrganisations", true)), body.get("minAmount"), body.get("maxAmount"),
                body.get("expectedDeliveryMinutes"));
        return ResponseEntity.ok(ok("routeCode", body.get("routeCode")));
    }

    @PostMapping("/beneficiaries")
    public ResponseEntity<Map<String, Object>> createBeneficiary(@RequestBody Map<String, Object> body) {
        require(body, "beneficiaryType");
        require(body, "displayName");
        require(body, "countryCode");
        String beneficiaryReference = asString(valueOr(body, "beneficiaryReference", "BEN-" + shortUuid()));
        jdbc.update("insert into beneficiaries (beneficiary_reference, merchant_id, beneficiary_type, display_name, "
                        + "legal_name, country_code, phone_hash, email_hash, address_text, risk_rating, status, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update display_name = values(display_name), legal_name = values(legal_name), "
                        + "country_code = values(country_code), address_text = values(address_text), risk_rating = values(risk_rating), "
                        + "status = values(status)",
                beneficiaryReference, body.get("merchantId"), body.get("beneficiaryType"), body.get("displayName"),
                body.get("legalName"), body.get("countryCode"), body.get("phoneHash"), body.get("emailHash"),
                body.get("addressText"), valueOr(body, "riskRating", "UNRATED"), valueOr(body, "status", "DRAFT"),
                body.get("createdBy"));

        Map<String, Object> response = ok("beneficiaryReference", beneficiaryReference);
        if (body.get("instrumentType") != null) {
            String instrumentReference = createBeneficiaryInstrument(beneficiaryReference, body);
            response.put("instrumentReference", instrumentReference);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/beneficiaries/{beneficiaryReference}/instruments")
    public ResponseEntity<Map<String, Object>> addBeneficiaryInstrument(@PathVariable String beneficiaryReference,
            @RequestBody Map<String, Object> body) {
        String instrumentReference = createBeneficiaryInstrument(beneficiaryReference, body);
        return ResponseEntity.ok(ok("beneficiaryReference", beneficiaryReference, "instrumentReference", instrumentReference));
    }

    @GetMapping("/beneficiaries/{beneficiaryReference}")
    public ResponseEntity<Map<String, Object>> getBeneficiary(@PathVariable String beneficiaryReference) {
        Map<String, Object> beneficiary = jdbc.queryForMap("select * from beneficiaries where beneficiary_reference = ?",
                beneficiaryReference);
        List<Map<String, Object>> instruments = jdbc.queryForList(
                "select * from beneficiary_instruments where beneficiary_id = ? order by created_at desc", beneficiary.get("id"));
        Map<String, Object> response = ok("beneficiary", beneficiary);
        response.put("instruments", instruments);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/fx/quotes")
    public ResponseEntity<Map<String, Object>> createFxQuote(@RequestBody Map<String, Object> body) {
        require(body, "corridorCode");
        require(body, "sourceAmount");
        require(body, "destinationAmount");
        require(body, "rate");
        require(body, "expiresAt");
        Map<String, Object> corridor = jdbc.queryForMap("select * from corridors where corridor_code = ?",
                body.get("corridorCode"));
        String quoteReference = asString(valueOr(body, "quoteReference", "FXQ-" + shortUuid()));
        jdbc.update("insert into fx_quotes (quote_reference, corridor_id, merchant_id, source_currency_code, "
                        + "destination_currency_code, source_amount, destination_amount, rate, spread_amount, fee_amount, "
                        + "rate_source, status, expires_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                quoteReference, corridor.get("id"), body.get("merchantId"), corridor.get("source_currency_code"),
                corridor.get("destination_currency_code"), body.get("sourceAmount"), body.get("destinationAmount"),
                body.get("rate"), valueOr(body, "spreadAmount", 0), valueOr(body, "feeAmount", 0),
                body.get("rateSource"), valueOr(body, "status", "ACTIVE"), body.get("expiresAt"));
        return ResponseEntity.ok(ok("quoteReference", quoteReference, "status", valueOr(body, "status", "ACTIVE")));
    }

    @GetMapping("/fx/quotes/{quoteReference}")
    public ResponseEntity<Map<String, Object>> getFxQuote(@PathVariable String quoteReference) {
        return ResponseEntity.ok(ok("quote", jdbc.queryForMap("select * from fx_quotes where quote_reference = ?",
                quoteReference)));
    }

    @PostMapping("/cross-border/transfers")
    public ResponseEntity<Map<String, Object>> createCrossBorderTransfer(@RequestBody Map<String, Object> body) {
        require(body, "corridorCode");
        require(body, "beneficiaryReference");
        require(body, "instrumentReference");
        require(body, "sourceAmount");
        require(body, "destinationAmount");
        require(body, "purposeCode");
        Map<String, Object> corridor = jdbc.queryForMap("select * from corridors where corridor_code = ?",
                body.get("corridorCode"));
        Long routeId = lookupOptionalId("select id from corridor_routes where route_code = ?", body.get("routeCode"));
        Map<String, Object> beneficiary = jdbc.queryForMap(
                "select * from beneficiaries where beneficiary_reference = ?", body.get("beneficiaryReference"));
        Map<String, Object> instrument = jdbc.queryForMap(
                "select * from beneficiary_instruments where instrument_reference = ?", body.get("instrumentReference"));
        Long quoteId = lookupOptionalId("select id from fx_quotes where quote_reference = ?", body.get("quoteReference"));
        Long caseId = lookupOptionalId("select id from compliance_cases where case_reference = ?",
                body.get("complianceCaseReference"));
        boolean holdActive = body.get("complianceCaseReference") != null || booleanValue(body.get("complianceHoldActive"));
        String transferReference = asString(valueOr(body, "transferReference", "XBT-" + shortUuid()));
        String status = asString(valueOr(body, "status", holdActive ? "COMPLIANCE_HOLD" : "CREATED"));
        jdbc.update("insert into cross_border_transfers (transfer_reference, merchant_id, merchant_number, corridor_id, "
                        + "route_id, beneficiary_id, beneficiary_instrument_id, fx_quote_id, source_amount, source_currency_code, "
                        + "destination_amount, destination_currency_code, purpose_code, status, compliance_case_id, "
                        + "compliance_hold_active, treasury_reservation_reference, metadata, created_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?)",
                transferReference, body.get("merchantId"), body.get("merchantNumber"), corridor.get("id"), routeId,
                beneficiary.get("id"), instrument.get("id"), quoteId, body.get("sourceAmount"),
                corridor.get("source_currency_code"), body.get("destinationAmount"), corridor.get("destination_currency_code"),
                body.get("purposeCode"), status, caseId, holdActive, body.get("treasuryReservationReference"),
                body.get("createdBy"));
        Long transferId = lookupId("select id from cross_border_transfers where transfer_reference = ?", transferReference);
        insertTransferEvent(transferId, "TRANSFER_CREATED", null, status, asString(body.get("createdBy")),
                asString(body.get("notes")));
        return ResponseEntity.ok(ok("transferReference", transferReference, "status", status));
    }

    @GetMapping("/cross-border/transfers/{transferReference}")
    public ResponseEntity<Map<String, Object>> getCrossBorderTransfer(@PathVariable String transferReference) {
        Map<String, Object> transfer = jdbc.queryForMap("select * from cross_border_transfers where transfer_reference = ?",
                transferReference);
        List<Map<String, Object>> events = jdbc.queryForList(
                "select event_type, from_status, to_status, actor, notes, created_at from cross_border_transfer_events "
                        + "where transfer_id = ? order by created_at asc",
                transfer.get("id"));
        Map<String, Object> response = ok("transfer", transfer);
        response.put("events", events);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/cross-border/transfers/{transferReference}/transition")
    public ResponseEntity<Map<String, Object>> transitionCrossBorderTransfer(@PathVariable String transferReference,
            @RequestBody Map<String, Object> body) {
        require(body, "status");
        Map<String, Object> current = jdbc.queryForMap(
                "select id, status from cross_border_transfers where transfer_reference = ?", transferReference);
        jdbc.update("update cross_border_transfers set status = ?, provider_reference = coalesce(?, provider_reference), "
                        + "partner_reference = coalesce(?, partner_reference), failure_code = ?, failure_message = ?, "
                        + "compliance_hold_active = ?, updated_at = current_timestamp, submitted_at = case when ? = 'SUBMITTED_TO_PARTNER' then current_timestamp else submitted_at end, "
                        + "delivered_at = case when ? = 'DELIVERED' then current_timestamp else delivered_at end, "
                        + "settled_at = case when ? = 'SETTLED' then current_timestamp else settled_at end "
                        + "where transfer_reference = ?",
                body.get("status"), body.get("providerReference"), body.get("partnerReference"), body.get("failureCode"),
                body.get("failureMessage"), booleanValue(valueOr(body, "complianceHoldActive", false)), body.get("status"),
                body.get("status"), body.get("status"), transferReference);
        insertTransferEvent(((Number) current.get("id")).longValue(), "STATUS_CHANGED", asString(current.get("status")),
                asString(body.get("status")), asString(body.get("actor")), asString(body.get("notes")));
        return ResponseEntity.ok(ok("transferReference", transferReference, "status", body.get("status")));
    }

    @PostMapping("/admin/cross-border/settlement-batches")
    public ResponseEntity<Map<String, Object>> createSettlementBatch(@RequestBody Map<String, Object> body) {
        require(body, "corridorCode");
        require(body, "settlementCurrencyCode");
        require(body, "businessDate");
        Long corridorId = lookupId("select id from corridors where corridor_code = ?", body.get("corridorCode"));
        String settlementReference = asString(valueOr(body, "settlementReference", "XBS-" + shortUuid()));
        jdbc.update("insert into corridor_settlement_batches (settlement_reference, corridor_id, partner_code, "
                        + "settlement_currency_code, business_date, status, gross_amount, fee_amount, net_amount, variance_amount) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                settlementReference, corridorId, body.get("partnerCode"), body.get("settlementCurrencyCode"),
                body.get("businessDate"), valueOr(body, "status", "OPEN"), valueOr(body, "grossAmount", 0),
                valueOr(body, "feeAmount", 0), valueOr(body, "netAmount", 0), valueOr(body, "varianceAmount", 0));
        return ResponseEntity.ok(ok("settlementReference", settlementReference));
    }

    @PostMapping("/admin/cross-border/treasury-exposure")
    public ResponseEntity<Map<String, Object>> recordTreasuryExposure(@RequestBody Map<String, Object> body) {
        require(body, "currencyCode");
        Long corridorId = lookupOptionalId("select id from corridors where corridor_code = ?", body.get("corridorCode"));
        String snapshotReference = asString(valueOr(body, "snapshotReference", "TRE-" + shortUuid()));
        jdbc.update("insert into treasury_exposure_snapshots (snapshot_reference, corridor_id, partner_code, currency_code, "
                        + "available_balance, reserved_balance, pending_delivery_exposure, unsettled_exposure, "
                        + "variance_exposure, captured_by) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                snapshotReference, corridorId, body.get("partnerCode"), body.get("currencyCode"),
                valueOr(body, "availableBalance", 0), valueOr(body, "reservedBalance", 0),
                valueOr(body, "pendingDeliveryExposure", 0), valueOr(body, "unsettledExposure", 0),
                valueOr(body, "varianceExposure", 0), body.get("capturedBy"));
        return ResponseEntity.ok(ok("snapshotReference", snapshotReference));
    }

    @PostMapping("/admin/cross-border/reports")
    public ResponseEntity<Map<String, Object>> requestCrossBorderReport(@RequestBody Map<String, Object> body) {
        require(body, "reportType");
        require(body, "periodStart");
        require(body, "periodEnd");
        Long corridorId = lookupOptionalId("select id from corridors where corridor_code = ?", body.get("corridorCode"));
        String reportReference = asString(valueOr(body, "reportReference", "XBR-" + shortUuid()));
        jdbc.update("insert into cross_border_report_runs (report_reference, report_type, corridor_id, period_start, "
                        + "period_end, status, requested_by) values (?, ?, ?, ?, ?, ?, ?)",
                reportReference, body.get("reportType"), corridorId, body.get("periodStart"), body.get("periodEnd"),
                valueOr(body, "status", "REQUESTED"), body.get("requestedBy"));
        return ResponseEntity.ok(ok("reportReference", reportReference));
    }

    private String createBeneficiaryInstrument(String beneficiaryReference, Map<String, Object> body) {
        require(body, "instrumentType");
        require(body, "countryCode");
        require(body, "currencyCode");
        require(body, "accountIdentifierHash");
        require(body, "accountIdentifierMask");
        Long beneficiaryId = lookupId("select id from beneficiaries where beneficiary_reference = ?", beneficiaryReference);
        String instrumentReference = asString(valueOr(body, "instrumentReference", "BIN-" + shortUuid()));
        jdbc.update("insert into beneficiary_instruments (beneficiary_id, instrument_reference, instrument_type, provider_code, "
                        + "country_code, currency_code, account_identifier_hash, account_identifier_mask, account_name, "
                        + "validation_status, status) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update provider_code = values(provider_code), account_name = values(account_name), "
                        + "validation_status = values(validation_status), status = values(status)",
                beneficiaryId, instrumentReference, body.get("instrumentType"), body.get("providerCode"),
                body.get("countryCode"), body.get("currencyCode"), body.get("accountIdentifierHash"),
                body.get("accountIdentifierMask"), body.get("accountName"),
                valueOr(body, "validationStatus", "NOT_VALIDATED"), valueOr(body, "instrumentStatus", "ACTIVE"));
        return instrumentReference;
    }

    private Long lookupId(String sql, Object value) {
        return jdbc.queryForObject(sql, Long.class, value);
    }

    private Long lookupOptionalId(String sql, Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return jdbc.queryForObject(sql, Long.class, value);
    }

    private void insertTransferEvent(Long transferId, String eventType, String fromStatus, String toStatus, String actor,
            String notes) {
        jdbc.update("insert into cross_border_transfer_events (transfer_id, event_type, from_status, to_status, actor, notes) "
                        + "values (?, ?, ?, ?, ?, ?)",
                transferId, eventType, fromStatus, toStatus, actor, notes);
    }

    private static void require(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
    }

    private static Object valueOr(Map<String, Object> body, String key, Object fallback) {
        Object value = body.get(key);
        return value == null ? fallback : value;
    }

    private static String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean booleanValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Map<String, Object> ok(Object... pairs) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "OK");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            response.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return response;
    }
}
