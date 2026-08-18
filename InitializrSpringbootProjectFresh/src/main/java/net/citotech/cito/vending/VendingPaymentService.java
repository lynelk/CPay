package net.citotech.cito.vending;

import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.PaymentPartyRequest;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Keeps vending money movement inside CPay's established orchestration path. That preserves
 * provider routing, risk checks, idempotent transaction references, webhooks, billing usage and the
 * core double-entry ledger instead of inventing a second payment engine for machines.
 */
@Service
public class VendingPaymentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentOrchestrationService payments;

    public VendingPaymentService(
            NamedParameterJdbcTemplate jdbc, PaymentOrchestrationService payments) {
        this.jdbc = jdbc;
        this.payments = payments;
    }

    public PaymentResult collectDeposit(
            long merchantId,
            String msisdn,
            String amount,
            String currency,
            String channel,
            String reference,
            VendingPaymentContext context) {
        Merchant merchant = merchant(merchantId);
        PaymentRequest request = request(merchant, amount, currency, channel, reference);
        PaymentPartyRequest payer = new PaymentPartyRequest();
        payer.setType("MSISDN");
        payer.setValue(msisdn);
        request.setPayer(payer);
        request.setDescription("Vending deposit " + reference);
        applyVendingMetadata(request, "DEPOSIT", context);
        return payments.collect(request, merchant, "vending-internal");
    }

    public PaymentResult refund(
            long merchantId,
            String msisdn,
            String amount,
            String currency,
            String channel,
            String reference,
            VendingPaymentContext context) {
        Merchant merchant = merchant(merchantId);
        PaymentRequest request = request(merchant, amount, currency, channel, reference);
        PaymentPartyRequest payee = new PaymentPartyRequest();
        payee.setType("MSISDN");
        payee.setValue(msisdn);
        request.setPayee(payee);
        request.setDescription("Vending refund " + reference);
        applyVendingMetadata(request, "REFUND", context);
        return payments.payout(request, merchant, "vending-internal");
    }

    /**
     * Tags every vending financial movement with the domain/vendor/operation metadata required by
     * the vending directive, so reports can always explain which vendor, connector, device and OEM
     * reference produced a ledger entry. Blank correlation values are omitted rather than stored as
     * empty metadata.
     */
    private void applyVendingMetadata(
            PaymentRequest request, String operation, VendingPaymentContext context) {
        request.getMetadata().put("cpayDomain", "VENDING");
        request.getMetadata()
                .put("vendingVendor", VendingVendorCode.require(context.vendorCode()).value());
        request.getMetadata().put("vendingOperation", operation);
        putIfPresent(request, "vendingConnectorId", context.connectorId());
        putIfPresent(request, "vendingRentalReference", context.rentalReference());
        putIfPresent(request, "vendingDeviceId", context.deviceId());
        putIfPresent(request, "vendingOemReference", context.oemReference());
    }

    private void putIfPresent(PaymentRequest request, String key, String value) {
        if (value == null || value.isBlank()) return;
        request.getMetadata().put(key, value.trim());
    }

    private PaymentRequest request(
            Merchant merchant, String amount, String currency, String channel, String reference) {
        PaymentRequest request = new PaymentRequest();
        request.setMerchantNumber(merchant.getAccount_number());
        request.setAmount(amount);
        request.setCurrency(currency);
        request.setCountry(countryFor(currency));
        request.setChannel(channel == null || channel.isBlank() ? null : channel.trim());
        request.setReference(reference);
        return request;
    }

    private Merchant merchant(long merchantId) {
        Merchant merchant = Common.getMerchantById(String.valueOf(merchantId), jdbc);
        if (merchant == null) throw new PaymentGatewayException("Merchant was not found");
        return merchant;
    }

    private String countryFor(String currency) {
        if (currency == null) return "UG";
        return switch (currency.trim().toUpperCase()) {
            case "KES" -> "KE";
            case "TZS" -> "TZ";
            case "RWF" -> "RW";
            default -> "UG";
        };
    }
}
