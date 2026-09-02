package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.api.v2.MerchantStatementExportService;
import net.citotech.cito.api.v2.dto.StatementExportResponse;
import net.citotech.cito.export.TabularExportService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.reconciliation.MerchantSettlementPreference;
import net.citotech.cito.reconciliation.MerchantSettlementPreferenceService;
import net.citotech.cito.security.SimpleRateLimitService;
import net.citotech.cito.sharedprovider.SharedProviderDefaultEntitlementService;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/merchant-self-service")
public class MerchantSelfServiceController {
    private final MerchantSelfServiceSignupService signupService;
    private final MerchantChannelCredentialService channelService;
    private final MerchantEnvironmentService environmentService;
    private final MerchantSettlementPreferenceService settlementPreferenceService;
    private final SimpleRateLimitService rateLimitService;
    private final MerchantStatementExportService statementExportService;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantNotificationPreferenceService notificationPreferenceService;
    private final MerchantEmailVerificationService emailVerificationService;
    private final MerchantWebhookService webhookService;
    private final SharedProviderDefaultEntitlementService sharedProviderDefaults;

    public MerchantSelfServiceController(
            MerchantSelfServiceSignupService signupService,
            MerchantChannelCredentialService channelService,
            MerchantEnvironmentService environmentService,
            MerchantSettlementPreferenceService settlementPreferenceService,
            SimpleRateLimitService rateLimitService,
            MerchantStatementExportService statementExportService,
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantNotificationPreferenceService notificationPreferenceService,
            MerchantEmailVerificationService emailVerificationService,
            MerchantWebhookService webhookService,
            SharedProviderDefaultEntitlementService sharedProviderDefaults) {
        this.signupService = signupService;
        this.channelService = channelService;
        this.environmentService = environmentService;
        this.settlementPreferenceService = settlementPreferenceService;
        this.rateLimitService = rateLimitService;
        this.statementExportService = statementExportService;
        this.jdbcTemplate = jdbcTemplate;
        this.notificationPreferenceService = notificationPreferenceService;
        this.emailVerificationService = emailVerificationService;
        this.webhookService = webhookService;
        this.sharedProviderDefaults = sharedProviderDefaults;
    }

    @PostMapping(path = "/signup")
    public ResponseEntity<?> signup(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            if (!rateLimitService.allow("merchant-signup:" + clientIp(request), 5)) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(
                                error(
                                        "RATE_LIMITED",
                                        "Too many registration attempts. Please try again later."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(signupService.signup(body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("SIGNUP_REJECTED", e.getMessage()));
        } catch (Exception e) {
            Logger.getLogger(MerchantSelfServiceController.class.getName())
                    .log(Level.SEVERE, "Signup failed", e);
            return ResponseEntity.badRequest()
                    .body(error("SIGNUP_FAILED", "Unable to complete merchant signup"));
        }
    }

    /**
     * Confirms a merchant user's email address (audit P4). Reachable pre-login (no session exists
     * yet), so the merchant is identified by merchantNumber+email rather than the session.
     */
    @PostMapping(path = "/verify-email")
    public ResponseEntity<?> verifyEmail(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String merchantNumber = text(body == null ? null : body.get("merchantNumber"));
            String email = text(body == null ? null : body.get("email"));
            String code = text(body == null ? null : body.get("code"));
            boolean verified =
                    emailVerificationService.verifyByMerchantNumberAndEmail(
                            merchantNumber, email, code);
            if (!verified) {
                return ResponseEntity.badRequest()
                        .body(
                                error(
                                        "VERIFICATION_CODE_INVALID",
                                        "That verification code is invalid or has expired."));
            }
            return ResponseEntity.ok(
                    Map.of(
                            "code",
                            "000",
                            "message",
                            "Email address verified. You can now log in."));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("VERIFICATION_REJECTED", e.getMessage()));
        }
    }

    /**
     * Always responds with the same generic, 200 message regardless of whether merchantNumber+email
     * actually resolves to an account - an error message that varied ("no such user" vs "sent")
     * would let an attacker enumerate registered email addresses.
     */
    @PostMapping(path = "/verify-email/resend")
    public ResponseEntity<?> resendVerificationEmail(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        String merchantNumber = text(body == null ? null : body.get("merchantNumber"));
        String email = text(body == null ? null : body.get("email"));
        if (!rateLimitService.allow(
                "merchant-verify-email-resend:" + merchantNumber + ":" + email, 3)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(error("RATE_LIMITED", "Too many requests. Please try again later."));
        }
        try {
            emailVerificationService.resendVerificationEmail(merchantNumber, email);
        } catch (PaymentGatewayException ignored) {
            // Fall through to the same generic response - see javadoc above.
        }
        return ResponseEntity.ok(
                Map.of(
                        "code",
                        "000",
                        "message",
                        "A new verification code has been sent if the account exists."));
    }

    @GetMapping(path = "/channels")
    public ResponseEntity<?> channels(HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            String environment =
                    String.valueOf(environmentService.getPreference(user).get("environment"));
            List<Map<String, Object>> channels = new ArrayList<>();
            channels.add(sharedProviderDefaults.merchantAccess(user.getMerchant_id(), environment));
            channels.addAll(channelService.list(user));
            return ResponseEntity.ok(channels);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @GetMapping(path = "/environment")
    public ResponseEntity<?> environment(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    environmentService.getPreference(currentMerchantUser(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/environment")
    public ResponseEntity<?> saveEnvironment(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    environmentService.savePreference(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(error("ENVIRONMENT_UPDATE_REJECTED", e.getMessage()));
        }
    }

    @GetMapping(path = "/settlement-preference")
    public ResponseEntity<?> settlementPreference(HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            return ResponseEntity.ok(settlementPreferenceService.getOrDefault(merchantId));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/settlement-preference")
    public ResponseEntity<?> saveSettlementPreference(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            long merchantId = requireMerchantId(user);
            String frequency = text(body == null ? null : body.get("settlementFrequency"));
            String dayOfWeek = text(body == null ? null : body.get("settlementDayOfWeek"));
            BigDecimal minimumAmount =
                    parseAmount(body == null ? null : body.get("minimumSettlementAmount"));
            MerchantSettlementPreference saved =
                    settlementPreferenceService.save(
                            merchantId,
                            frequency,
                            dayOfWeek.isEmpty() ? null : dayOfWeek,
                            minimumAmount,
                            user.getEmail());
            return ResponseEntity.ok(saved);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(error("SETTLEMENT_PREFERENCE_REJECTED", e.getMessage()));
        }
    }

    @GetMapping(path = "/sandbox-guide")
    public ResponseEntity<?> sandboxGuide(HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            return ResponseEntity.ok(environmentService.sandboxGuide(user.getMerchant_number()));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/save")
    public ResponseEntity<?> saveChannel(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.save(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("CHANNEL_SAVE_REJECTED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/test")
    public ResponseEntity<?> testChannel(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(channelService.test(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("CHANNEL_TEST_REJECTED", e.getMessage()));
        }
    }

    @PostMapping(path = "/channels/submit")
    public ResponseEntity<?> submitChannel(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    channelService.submitForApproval(currentMerchantUser(request), body));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(error("CHANNEL_SUBMIT_REJECTED", e.getMessage()));
        }
    }

    /**
     * Self-service statement export (audit N3): the existing GET /api/v2/statements requires v2
     * HMAC request signing, meant for API integrators - a merchant portal user who is already
     * logged in via session has no signing keys and no reason to construct a signed request just to
     * see their own statement. This resolves the merchant from the session instead.
     */
    @GetMapping(path = "/statements")
    public ResponseEntity<?> statements(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam(value = "format", defaultValue = "json") String format,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor,
            HttpServletRequest request) {
        try {
            Merchant merchant = currentMerchant(request);
            StatementExportResponse export =
                    statementExportService.exportForPortal(
                            merchant, startDate, endDate, limit, cursor);
            if ("csv".equalsIgnoreCase(format)) {
                String filename =
                        "cpay-statement-"
                                + merchant.getAccount_number()
                                + "-"
                                + startDate
                                + "-to-"
                                + endDate
                                + ".csv";
                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .contentType(
                                MediaType.parseMediaType(TabularExportService.CSV_CONTENT_TYPE))
                        .body(statementExportService.toCsv(export));
            }
            if ("xlsx".equalsIgnoreCase(format)) {
                String filename =
                        "cpay-statement-"
                                + merchant.getAccount_number()
                                + "-"
                                + startDate
                                + "-to-"
                                + endDate
                                + ".xlsx";
                return ResponseEntity.ok()
                        .header(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .contentType(
                                MediaType.parseMediaType(TabularExportService.XLSX_CONTENT_TYPE))
                        .body(statementExportService.toXlsx(export));
            }
            return ResponseEntity.ok(export);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    private Merchant currentMerchant(HttpServletRequest request) {
        MerchantUser user = currentMerchantUser(request);
        Merchant merchant =
                Common.getMerchantById(String.valueOf(user.getMerchant_id()), jdbcTemplate);
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return merchant;
    }

    /**
     * Lists one row per catalog event type (audit N5) - the merchant's explicit channel/address
     * choice where one exists, otherwise a defaulted EMAIL-to-primary-contact entry, so the portal
     * can show every configurable event even before the merchant has touched any of them.
     */
    @GetMapping(path = "/notification-preferences")
    public ResponseEntity<?> notificationPreferences(HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            return ResponseEntity.ok(notificationPreferenceService.list(user.getMerchant_id()));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    /**
     * Upserts the merchant's preference for a single event type: {@code eventType} (must be one of
     * net.citotech.cito.webhook.WebhookEventCatalog's event types), {@code channel} (EMAIL, SMS, or
     * NONE), and an optional {@code notifyAddress} override.
     */
    @PostMapping(path = "/notification-preferences")
    public ResponseEntity<?> saveNotificationPreference(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            String eventType = text(body.get("eventType"));
            String channel = text(body.get("channel"));
            String notifyAddress = text(body.get("notifyAddress"));
            return ResponseEntity.ok(
                    notificationPreferenceService.save(
                            user.getMerchant_id(), eventType, channel, notifyAddress));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(error("NOTIFICATION_PREFERENCE_REJECTED", e.getMessage()));
        }
    }

    /**
     * Audit N6: the rotate-secret/replay mechanics already existed (admin-only, under
     * /api/v2/admin/webhooks/**) but had no merchant self-service equivalent - a merchant could
     * only get their webhook secret rotated or a failed delivery replayed by asking an admin to do
     * it on their behalf. These five endpoints expose the same capability directly to the merchant,
     * scoped to their own merchant_id so one merchant can never rotate or replay another's webhook.
     */
    @GetMapping(path = "/webhooks")
    public ResponseEntity<?> webhookEndpoints(HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            return ResponseEntity.ok(webhookService.listEndpoints(merchantId));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/webhooks")
    public ResponseEntity<?> registerWebhookEndpoint(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MerchantUser user = currentMerchantUser(request);
            long merchantId = requireMerchantId(user);
            return ResponseEntity.ok(
                    webhookService.registerEndpoint(
                            merchantId,
                            text(body.get("eventType")),
                            text(body.get("endpointUrl")),
                            user.getEmail()));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(error("WEBHOOK_REJECTED", e.getMessage()));
        }
    }

    @PostMapping(path = "/webhooks/{endpointId}/rotate-secret")
    public ResponseEntity<?> rotateWebhookSecret(
            @PathVariable("endpointId") long endpointId, HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            return ResponseEntity.ok(webhookService.rotateSecret(merchantId, endpointId));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(error("WEBHOOK_NOT_FOUND", e.getMessage()));
        }
    }

    @GetMapping(path = "/webhooks/deliveries")
    public ResponseEntity<?> webhookDeliveries(
            @RequestParam(name = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            return ResponseEntity.ok(
                    webhookService.listDeliveries(merchantId, limit == null ? 50 : limit));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    @PostMapping(path = "/webhooks/deliveries/{deliveryId}/replay")
    public ResponseEntity<?> replayWebhookDelivery(
            @PathVariable("deliveryId") long deliveryId, HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            int updated = webhookService.replay(merchantId, deliveryId);
            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(
                                error(
                                        "WEBHOOK_DELIVERY_NOT_FOUND",
                                        "Delivery was not found or is not eligible for replay"));
            }
            return ResponseEntity.ok(Map.of("updated", updated));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", e.getMessage()));
        }
    }

    private MerchantUser currentMerchantUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("merchantUser") == null)
            throw new PaymentGatewayException("Merchant login is required");
        return (MerchantUser) session.getAttribute("merchantUser");
    }

    private long requireMerchantId(MerchantUser user) {
        if (user == null || user.getMerchant_id() == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user.getMerchant_id();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal parseAmount(Object value) {
        String raw = text(value).replace(",", "");
        if (raw.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("minimumSettlementAmount must be a valid number");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.trim().isEmpty()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
