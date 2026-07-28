package net.citotech.cito.checkout;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.HostedCheckoutPayRequest;
import net.citotech.cito.api.v2.dto.PaymentLinkCreateRequest;
import net.citotech.cito.api.v2.dto.PaymentLinkResponse;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentLinkService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PaymentOrchestrationService orchestrationService;
    private final String appBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public PaymentLinkService(NamedParameterJdbcTemplate jdbcTemplate,
                              PaymentOrchestrationService orchestrationService,
                              @Value("${app.base.url:http://localhost:8081}") String appBaseUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.orchestrationService = orchestrationService;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public PaymentLinkResponse create(PaymentLinkCreateRequest request, Merchant merchant) {
        validateCreate(request, merchant);
        String token = token();
        String linkReference = blank(request.getReference()) ? "plink_" + Common.generateUuid() : request.getReference();
        Instant expiresAt = blank(request.getExpiresAt())
            ? Instant.now().plus(7, ChronoUnit.DAYS)
            : Instant.parse(request.getExpiresAt());

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("link_reference", linkReference);
        p.addValue("merchant_id", merchant.getId());
        p.addValue("merchant_number", merchant.getAccount_number());
        p.addValue("amount", MoneyAmount.of(request.getAmount()).asBigDecimal());
        p.addValue("currency", request.getCurrency().trim().toUpperCase());
        p.addValue("country", blank(request.getCountry()) ? "UG" : request.getCountry().trim().toUpperCase());
        p.addValue("description", request.getDescription());
        p.addValue("callback_url", request.getCallbackUrl());
        p.addValue("token_hash", hash(token));
        p.addValue("expires_at", Timestamp.from(expiresAt));
        jdbcTemplate.update(
            "INSERT INTO payment_links "
                + "(link_reference, merchant_id, merchant_number, amount, currency, country, description, callback_url, token_hash, expires_at) "
                + "VALUES (:link_reference, :merchant_id, :merchant_number, :amount, :currency, :country, :description, :callback_url, :token_hash, :expires_at)",
            p);

        PaymentLinkResponse response = new PaymentLinkResponse();
        response.setLinkReference(linkReference);
        response.setCheckoutUrl(appBaseUrl.replaceAll("/$", "") + "/checkout/" + token);
        response.setStatus("ACTIVE");
        response.setAmount(MoneyAmount.of(request.getAmount()).toString());
        response.setCurrency(request.getCurrency().trim().toUpperCase());
        response.setDescription(request.getDescription());
        response.setExpiresAt(expiresAt.toString());
        return response;
    }

    public PaymentLinkRecord getByToken(String token) {
        List<PaymentLinkRecord> rows = jdbcTemplate.query(
            "SELECT * FROM payment_links WHERE token_hash=:hash LIMIT 1",
            new MapSqlParameterSource("hash", hash(token)),
            (rs, rowNum) -> {
                PaymentLinkRecord record = new PaymentLinkRecord();
                record.setId(rs.getLong("id"));
                record.setMerchantId(rs.getLong("merchant_id"));
                record.setMerchantNumber(rs.getString("merchant_number"));
                record.setLinkReference(rs.getString("link_reference"));
                record.setAmount(rs.getBigDecimal("amount"));
                record.setCurrency(rs.getString("currency"));
                record.setCountry(rs.getString("country"));
                record.setDescription(rs.getString("description"));
                record.setCallbackUrl(rs.getString("callback_url"));
                record.setStatus(rs.getString("link_status"));
                Timestamp expires = rs.getTimestamp("expires_at");
                record.setExpiresAt(expires == null ? null : expires.toInstant());
                return record;
            });
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Payment link was not found");
        }
        PaymentLinkRecord record = rows.get(0);
        if (!"ACTIVE".equalsIgnoreCase(record.getStatus())) {
            throw new PaymentGatewayException("Payment link is not active");
        }
        if (record.getExpiresAt() != null && record.getExpiresAt().isBefore(Instant.now())) {
            throw new PaymentGatewayException("Payment link has expired");
        }
        return record;
    }

    @Transactional
    public PaymentResult pay(String token, HostedCheckoutPayRequest payRequest, String originateIp) {
        PaymentLinkRecord link = getByToken(token);
        if (payRequest == null || blank(payRequest.getPayerAccount())) {
            throw new PaymentGatewayException("payerAccount is required");
        }
        if (!reserveForPayment(link)) {
            throw new PaymentGatewayException("Payment link is already being processed");
        }
        PaymentResult result;
        try {
            Merchant merchant = Common.getMerchantById(String.valueOf(link.getMerchantId()), jdbcTemplate);
            if (merchant == null) {
                throw new PaymentGatewayException("Merchant for payment link was not found");
            }

            PaymentPartyRequest payer = new PaymentPartyRequest();
            payer.setType("MSISDN");
            payer.setValue(payRequest.getPayerAccount());

            PaymentRequest request = new PaymentRequest();
            request.setMerchantNumber(link.getMerchantNumber());
            request.setAmount(link.getAmount().toPlainString());
            request.setCurrency(link.getCurrency());
            request.setCountry(link.getCountry());
            request.setReference(link.getLinkReference());
            request.setDescription(link.getDescription());
            request.setCallbackUrl(link.getCallbackUrl());
            request.setChannel(payRequest.getChannel());
            request.setPayer(payer);

            result = orchestrationService.collect(request, merchant, originateIp);
        } catch (RuntimeException ex) {
            releaseReservation(link);
            throw ex;
        }
        recordAttempt(link, payRequest.getPayerAccount(), payRequest.getChannel(), result);
        updatePaymentStatus(link, checkoutStatusFor(result), result.getTransactionId());
        return result;
    }

    private void validateCreate(PaymentLinkCreateRequest request, Merchant merchant) {
        if (merchant == null || request == null) {
            throw new PaymentGatewayException("Merchant and payment link request are required");
        }
        if (!merchant.getAccount_number().equals(request.getMerchantNumber())) {
            throw new PaymentGatewayException("Verified merchant does not match request merchant");
        }
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) {
            throw new PaymentGatewayException("Merchant is not active");
        }
        MoneyAmount.of(request.getAmount());
        if (blank(request.getCurrency())) {
            throw new PaymentGatewayException("currency is required");
        }
        if (blank(request.getDescription())) {
            throw new PaymentGatewayException("description is required");
        }
    }

    private void recordAttempt(PaymentLinkRecord link, String payerAccount, String channel, PaymentResult result) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("payment_link_id", link.getId());
        p.addValue("payer_account", payerAccount);
        p.addValue("channel_code", channel);
        p.addValue("status", result.getStatus());
        p.addValue("transaction_id", result.getTransactionId());
        p.addValue("message", result.getMessage());
        jdbcTemplate.update(
            "INSERT INTO hosted_checkout_attempts "
                + "(payment_link_id, payer_account, channel_code, attempt_status, transaction_id, message) "
                + "VALUES (:payment_link_id, :payer_account, :channel_code, :status, :transaction_id, :message)",
            p);
    }

    private boolean reserveForPayment(PaymentLinkRecord link) {
        int updated = jdbcTemplate.update(
            "UPDATE payment_links SET link_status='PROCESSING' WHERE id=:id AND link_status='ACTIVE'",
            new MapSqlParameterSource("id", link.getId()));
        return updated > 0;
    }

    private void updatePaymentStatus(PaymentLinkRecord link, String status, String transactionId) {
        jdbcTemplate.update(
            "UPDATE payment_links SET link_status=:status, paid_transaction_id=:tx_id WHERE id=:id AND link_status='PROCESSING'",
            new MapSqlParameterSource("id", link.getId())
                .addValue("status", status)
                .addValue("tx_id", transactionId));
    }

    private void releaseReservation(PaymentLinkRecord link) {
        jdbcTemplate.update(
            "UPDATE payment_links SET link_status='ACTIVE' WHERE id=:id AND link_status='PROCESSING'",
            new MapSqlParameterSource("id", link.getId()));
    }

    String checkoutStatusFor(PaymentResult result) {
        String status = result == null ? "" : result.getStatus();
        if ("SUCCESSFUL".equalsIgnoreCase(status)) {
            return "PAID";
        }
        if ("FAILED".equalsIgnoreCase(status) || "ERROR".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
            return "ACTIVE";
        }
        return "SUBMITTED";
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
