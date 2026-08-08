package net.citotech.cito.checkout;

import java.security.SecureRandom;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.HostedCheckoutPayRequest;
import net.citotech.cito.api.v2.dto.InvoiceCreateRequest;
import net.citotech.cito.api.v2.dto.InvoiceResponse;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.security.CanonicalRequestSigner;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invoices / request-to-pay objects (audit N9): a merchant-created, shareable "please pay this"
 * object with an amount, description, due date, and payer reference - distinct from an ad-hoc
 * collection because it exists (as a DRAFT) before anyone has paid, and can be looked up and paid
 * later by whoever holds the link.
 *
 * <p>This is a deliberate sibling of {@link PaymentLinkService} rather than a parallel
 * implementation: same random-token/hash/expiry-guard shape for the public pay path, and real money
 * movement is triggered the exact same way - through {@link
 * PaymentOrchestrationService#collect(PaymentRequest, Merchant, String)} - so this class never
 * touches {@code merchant_transactions_log} or legacy pay-in logic directly.
 *
 * <p>Unlike a payment link, an invoice starts in {@code DRAFT} and is not payable until the
 * merchant explicitly {@link #send(String, String, Merchant)}s it - modeling the real-world "create
 * the invoice, review it, then send it to the customer" workflow.
 */
@Service
public class InvoiceService {
    static final String STATUS_DRAFT = "DRAFT";
    static final String STATUS_SENT = "SENT";
    static final String STATUS_PAID = "PAID";
    static final String STATUS_EXPIRED = "EXPIRED";
    static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * Internal-only reservation state while a real collection is in flight against this invoice,
     * mirroring {@code payment_links.link_status='PROCESSING'} in {@link PaymentLinkService}. It
     * guards against a customer double-submitting payment while one attempt is still resolving and
     * is never exposed to callers - it is always resolved back to SENT or PAID before the
     * transaction returns.
     */
    private static final String STATUS_PROCESSING = "PROCESSING";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PaymentOrchestrationService orchestrationService;
    private final MerchantWebhookService webhookService;
    private final String appBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvoiceService(
            NamedParameterJdbcTemplate jdbcTemplate,
            PaymentOrchestrationService orchestrationService,
            MerchantWebhookService webhookService,
            @Value("${app.base.url:http://localhost:8081}") String appBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.orchestrationService = orchestrationService;
        this.webhookService = webhookService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public InvoiceResponse create(InvoiceCreateRequest request, Merchant merchant) {
        validateCreate(request, merchant);
        String reference =
                blank(request.getReference())
                        ? "inv_" + Common.generateUuid()
                        : request.getReference().trim();
        LocalDate dueDate = parseDueDate(request.getDueDate());
        String token = token();

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchant.getId());
        p.addValue("reference", reference);
        p.addValue("amount", MoneyAmount.of(request.getAmount()).asBigDecimal());
        p.addValue("currency", request.getCurrency().trim().toUpperCase());
        p.addValue("description", request.getDescription());
        p.addValue("payer_name", request.getPayerName());
        p.addValue("payer_contact", request.getPayerContact());
        p.addValue("due_date", dueDate == null ? null : Date.valueOf(dueDate));
        p.addValue("token_hash", hash(token));
        try {
            jdbcTemplate.update(
                    "INSERT INTO invoices "
                            + "(merchant_id, reference, amount, currency, description, payer_name, payer_contact, due_date, status, public_token_hash) "
                            + "VALUES (:merchant_id, :reference, :amount, :currency, :description, :payer_name, :payer_contact, :due_date, '"
                            + STATUS_DRAFT
                            + "', :token_hash)",
                    p);
        } catch (DuplicateKeyException e) {
            throw new PaymentGatewayException(
                    "An invoice with reference " + reference + " already exists for this merchant");
        }

        InvoiceRecord record = requireByMerchantAndReference(merchant.getId(), reference);
        return toResponse(record, token);
    }

    /** DRAFT -> SENT. This is the point at which the invoice becomes payable. */
    @Transactional
    public InvoiceResponse send(String merchantNumber, String reference, Merchant merchant) {
        validateMerchantMatches(merchantNumber, merchant);
        InvoiceRecord record = requireByMerchantAndReference(merchant.getId(), reference);
        if (!STATUS_DRAFT.equals(record.getStatus())) {
            throw new PaymentGatewayException(
                    "Only a DRAFT invoice can be sent (current status: "
                            + record.getStatus()
                            + ")");
        }
        transitionStatus(record.getId(), STATUS_DRAFT, STATUS_SENT);
        record.setStatus(STATUS_SENT);
        queueInvoiceIssuedWebhook(record, merchant);
        return toResponse(record, null);
    }

    /**
     * Best-effort {@code invoice.issued} webhook (billing Slice 25, ADR 0006's first non-
     * transactional catalog entry) - mirrors {@code PaymentOrchestrationService.queueWebhook}'s
     * convention exactly: the invoice send itself remains authoritative and must never fail or roll
     * back because of this notification.
     */
    private void queueInvoiceIssuedWebhook(InvoiceRecord record, Merchant merchant) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("eventType", "invoice.issued");
            payload.put("merchantNumber", merchant.getAccount_number());
            payload.put("invoiceId", record.getReference());
            payload.put("status", record.getStatus());
            payload.put(
                    "amount",
                    record.getAmount() == null ? null : record.getAmount().toPlainString());
            payload.put("currency", record.getCurrency());
            webhookService.enqueue(
                    merchant.getId(), "invoice.issued", record.getReference(), payload.toString());
        } catch (Exception ignored) {
            // Invoice send remains authoritative; webhook delivery is retried separately.
        }
    }

    /**
     * DRAFT or SENT -> CANCELLED. A PAID, EXPIRED, or already-CANCELLED invoice cannot be
     * cancelled.
     */
    @Transactional
    public InvoiceResponse cancel(String merchantNumber, String reference, Merchant merchant) {
        validateMerchantMatches(merchantNumber, merchant);
        InvoiceRecord record = requireByMerchantAndReference(merchant.getId(), reference);
        if (!STATUS_DRAFT.equals(record.getStatus()) && !STATUS_SENT.equals(record.getStatus())) {
            throw new PaymentGatewayException(
                    "Cannot cancel an invoice with status " + record.getStatus());
        }
        transitionStatus(record.getId(), record.getStatus(), STATUS_CANCELLED);
        record.setStatus(STATUS_CANCELLED);
        return toResponse(record, null);
    }

    /**
     * All invoices for the merchant, newest first. The one-time pay link is not reconstructable
     * here - see {@link #create}.
     */
    public List<InvoiceResponse> list(String merchantNumber, Merchant merchant) {
        validateMerchantMatches(merchantNumber, merchant);
        List<InvoiceRecord> rows =
                jdbcTemplate.query(
                        "SELECT * FROM invoices WHERE merchant_id=:merchant_id ORDER BY created_at DESC",
                        new MapSqlParameterSource("merchant_id", merchant.getId()),
                        this::mapRow);
        List<InvoiceResponse> responses = new ArrayList<>();
        for (InvoiceRecord row : rows) {
            responses.add(toResponse(row, null));
        }
        return responses;
    }

    /** Public, unauthenticated lookup by the opaque pay token - for the customer-facing view. */
    public InvoiceResponse viewByToken(String token) {
        InvoiceRecord record = requireByToken(token);
        return toResponse(record, null);
    }

    @Transactional
    public PaymentResult pay(
            String token, HostedCheckoutPayRequest payRequest, String originateIp) {
        InvoiceRecord record = requireByToken(token);
        if (payRequest == null || blank(payRequest.getPayerAccount())) {
            throw new PaymentGatewayException("payerAccount is required");
        }
        guardPayable(record);
        if (!reserveForPayment(record)) {
            throw new PaymentGatewayException("Invoice is already being processed");
        }
        PaymentResult result;
        try {
            Merchant merchant =
                    Common.getMerchantById(String.valueOf(record.getMerchantId()), jdbcTemplate);
            if (merchant == null) {
                throw new PaymentGatewayException("Merchant for invoice was not found");
            }

            PaymentPartyRequest payer = new PaymentPartyRequest();
            payer.setType("MSISDN");
            payer.setValue(payRequest.getPayerAccount());

            PaymentRequest request = new PaymentRequest();
            request.setMerchantNumber(merchant.getAccount_number());
            request.setAmount(record.getAmount().toPlainString());
            request.setCurrency(record.getCurrency());
            // Invoices have no country column of their own (unlike payment_links); default to the
            // same "UG" fallback PaymentLinkService uses when a country isn't otherwise supplied.
            request.setCountry("UG");
            request.setReference(record.getReference());
            request.setDescription(record.getDescription());
            // Invoices, unlike payment links, don't carry a merchant-supplied callback URL - the
            // orchestration layer requires a non-blank one regardless, so a synthetic marker is
            // used purely to satisfy that validation. It is only ever consulted asynchronously by
            // the merchant-callback delivery worker (Common.enqueueMerchantCallback), which already
            // no-ops/soft-fails on an unreachable URL, so this cannot break the synchronous pay
            // flow; real merchant notification for invoice payment still goes out through the
            // existing merchant webhook system that PaymentOrchestrationService.collect(...)
            // queues.
            request.setCallbackUrl("cpay-invoice:" + record.getReference());
            request.setChannel(payRequest.getChannel());
            request.setPayer(payer);

            result = orchestrationService.collect(request, merchant, originateIp);
        } catch (RuntimeException ex) {
            releaseReservation(record);
            throw ex;
        }
        finalizePayment(record, result);
        return result;
    }

    private void guardPayable(InvoiceRecord record) {
        if (isOverdue(record)
                && (STATUS_DRAFT.equals(record.getStatus())
                        || STATUS_SENT.equals(record.getStatus()))) {
            transitionStatus(record.getId(), record.getStatus(), STATUS_EXPIRED);
            throw new PaymentGatewayException("Invoice has expired");
        }
        if (STATUS_DRAFT.equals(record.getStatus())) {
            throw new PaymentGatewayException("Invoice has not been sent yet");
        }
        if (STATUS_PAID.equals(record.getStatus())) {
            throw new PaymentGatewayException("Invoice has already been paid");
        }
        if (STATUS_CANCELLED.equals(record.getStatus())) {
            throw new PaymentGatewayException("Invoice was cancelled");
        }
        if (STATUS_EXPIRED.equals(record.getStatus())) {
            throw new PaymentGatewayException("Invoice has expired");
        }
        if (!STATUS_SENT.equals(record.getStatus())) {
            throw new PaymentGatewayException("Invoice is not payable");
        }
    }

    private boolean isOverdue(InvoiceRecord record) {
        return record.getDueDate() != null && record.getDueDate().isBefore(LocalDate.now());
    }

    private void finalizePayment(InvoiceRecord record, PaymentResult result) {
        String status = statusFor(result);
        if (STATUS_PAID.equals(status)) {
            MapSqlParameterSource p = new MapSqlParameterSource();
            p.addValue("id", record.getId());
            p.addValue("tx_id", result.getTransactionId());
            jdbcTemplate.update(
                    "UPDATE invoices SET status='"
                            + STATUS_PAID
                            + "', created_transaction_id=:tx_id "
                            + "WHERE id=:id AND status='"
                            + STATUS_PROCESSING
                            + "'",
                    p);
        } else {
            // Not (yet) successful: release the reservation so the invoice can be retried.
            jdbcTemplate.update(
                    "UPDATE invoices SET status='"
                            + STATUS_SENT
                            + "' WHERE id=:id AND status='"
                            + STATUS_PROCESSING
                            + "'",
                    new MapSqlParameterSource("id", record.getId()));
        }
    }

    /**
     * Maps an orchestration result onto invoice states, mirroring {@link
     * PaymentLinkService#checkoutStatusFor}.
     */
    String statusFor(PaymentResult result) {
        String status = result == null ? "" : result.getStatus();
        if ("SUCCESSFUL".equalsIgnoreCase(status)) {
            return STATUS_PAID;
        }
        return STATUS_SENT;
    }

    private boolean reserveForPayment(InvoiceRecord record) {
        int updated =
                jdbcTemplate.update(
                        "UPDATE invoices SET status='"
                                + STATUS_PROCESSING
                                + "' WHERE id=:id AND status='"
                                + STATUS_SENT
                                + "'",
                        new MapSqlParameterSource("id", record.getId()));
        return updated > 0;
    }

    private void releaseReservation(InvoiceRecord record) {
        jdbcTemplate.update(
                "UPDATE invoices SET status='"
                        + STATUS_SENT
                        + "' WHERE id=:id AND status='"
                        + STATUS_PROCESSING
                        + "'",
                new MapSqlParameterSource("id", record.getId()));
    }

    private void transitionStatus(long id, String expectedStatus, String newStatus) {
        int updated =
                jdbcTemplate.update(
                        "UPDATE invoices SET status=:new_status WHERE id=:id AND status=:expected_status",
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("new_status", newStatus)
                                .addValue("expected_status", expectedStatus));
        if (updated == 0) {
            throw new PaymentGatewayException("Invoice status changed concurrently; please retry");
        }
    }

    private InvoiceRecord requireByMerchantAndReference(long merchantId, String reference) {
        List<InvoiceRecord> rows =
                jdbcTemplate.query(
                        "SELECT * FROM invoices WHERE merchant_id=:merchant_id AND reference=:reference LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", reference),
                        this::mapRow);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Invoice " + reference + " was not found");
        }
        return rows.get(0);
    }

    private InvoiceRecord requireByToken(String token) {
        List<InvoiceRecord> rows =
                jdbcTemplate.query(
                        "SELECT * FROM invoices WHERE public_token_hash=:hash LIMIT 1",
                        new MapSqlParameterSource("hash", hash(token)),
                        this::mapRow);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Invoice was not found");
        }
        return rows.get(0);
    }

    private InvoiceRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        InvoiceRecord record = new InvoiceRecord();
        record.setId(rs.getLong("id"));
        record.setMerchantId(rs.getLong("merchant_id"));
        record.setReference(rs.getString("reference"));
        record.setAmount(rs.getBigDecimal("amount"));
        record.setCurrency(rs.getString("currency"));
        record.setDescription(rs.getString("description"));
        record.setPayerName(rs.getString("payer_name"));
        record.setPayerContact(rs.getString("payer_contact"));
        Date dueDate = rs.getDate("due_date");
        record.setDueDate(dueDate == null ? null : dueDate.toLocalDate());
        record.setStatus(rs.getString("status"));
        record.setPublicTokenHash(rs.getString("public_token_hash"));
        record.setCreatedTransactionId(rs.getString("created_transaction_id"));
        return record;
    }

    private InvoiceResponse toResponse(InvoiceRecord record, String rawToken) {
        InvoiceResponse response = new InvoiceResponse();
        response.setReference(record.getReference());
        response.setStatus(record.getStatus());
        response.setAmount(record.getAmount() == null ? null : record.getAmount().toPlainString());
        response.setCurrency(record.getCurrency());
        response.setDescription(record.getDescription());
        response.setPayerName(record.getPayerName());
        response.setPayerContact(record.getPayerContact());
        response.setDueDate(record.getDueDate() == null ? null : record.getDueDate().toString());
        response.setCreatedTransactionId(record.getCreatedTransactionId());
        if (rawToken != null) {
            response.setPayUrl(
                    appBaseUrl.replaceAll("/$", "") + "/api/v2/invoices/pay/" + rawToken);
        }
        return response;
    }

    private void validateCreate(InvoiceCreateRequest request, Merchant merchant) {
        validateMerchantMatches(request == null ? null : request.getMerchantNumber(), merchant);
        if (request == null) {
            throw new PaymentGatewayException("Invoice request is required");
        }
        MoneyAmount.of(request.getAmount());
        if (blank(request.getCurrency())) {
            throw new PaymentGatewayException("currency is required");
        }
        if (blank(request.getDescription())) {
            throw new PaymentGatewayException("description is required");
        }
    }

    private void validateMerchantMatches(String merchantNumber, Merchant merchant) {
        if (merchant == null || merchantNumber == null) {
            throw new PaymentGatewayException("Merchant and merchantNumber are required");
        }
        if (!merchant.getAccount_number().equals(merchantNumber)) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
    }

    private LocalDate parseDueDate(String dueDate) {
        if (blank(dueDate)) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new PaymentGatewayException("dueDate must be an ISO date (yyyy-MM-dd)");
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        return CanonicalRequestSigner.sha256Hex(value == null ? "" : value);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
