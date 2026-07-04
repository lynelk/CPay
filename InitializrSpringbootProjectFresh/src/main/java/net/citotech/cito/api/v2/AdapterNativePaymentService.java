package net.citotech.cito.api.v2;

import java.util.HashMap;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.GateWayResponse;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentChannelAdapter;
import net.citotech.cito.gateway.PaymentChannelRegistry;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.gateway.PaymentGatewayRequest;
import net.citotech.cito.merchant.MerchantChannelCredentialService;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdapterNativePaymentService {
    private final PaymentChannelRegistry registry;
    private final MerchantChannelCredentialService channelCredentialService;
    private final String gatewayState;

    public AdapterNativePaymentService(PaymentChannelRegistry registry,
                                       MerchantChannelCredentialService channelCredentialService,
                                       @Value("${custom.gatewaystate:SANDBOX}") String gatewayState) {
        this.registry = registry;
        this.channelCredentialService = channelCredentialService;
        this.gatewayState = gatewayState;
    }

    public PaymentResult collect(PaymentRequest request, Merchant merchant) {
        validate(request, merchant, true, Common.API_MOBILE_MONEY_PAYIN);
        String account = request.getPayer().getValue();
        PaymentChannelAdapter adapter = adapterFor(request, account);
        channelCredentialService.ensureChannelReady(merchant, adapter.channelCode());
        GateWayResponse response = adapter.collect(adapterRequest(request, account, merchant, adapter));
        return result(request, adapter, response);
    }

    public PaymentResult payout(PaymentRequest request, Merchant merchant) {
        validate(request, merchant, false, Common.API_MOBILE_MONEY_PAYOUT);
        String account = request.getPayee().getValue();
        PaymentChannelAdapter adapter = adapterFor(request, account);
        channelCredentialService.ensureChannelReady(merchant, adapter.channelCode());
        GateWayResponse response = adapter.payout(adapterRequest(request, account, merchant, adapter));
        return result(request, adapter, response);
    }

    private PaymentGatewayRequest adapterRequest(PaymentRequest request, String account, Merchant merchant, PaymentChannelAdapter adapter) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("currency", request.getCurrency());
        metadata.put("country", request.getCountry());
        metadata.put("gatewayState", gatewayState);
        Map<String, Object> setup = channelCredentialService.loadDecrypted(merchant, adapter.channelCode(), "SANDBOX");
        for (Map.Entry<String, Object> entry : setup.entrySet()) {
            if (entry.getValue() != null) metadata.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return new PaymentGatewayRequest(request.getMerchantNumber(), account, MoneyAmount.of(request.getAmount()).asLegacyDouble(), request.getReference(), request.getDescription(), request.getCallbackUrl(), metadata);
    }

    private PaymentChannelAdapter adapterFor(PaymentRequest request, String account) {
        if (request.getChannel() != null && !request.getChannel().trim().isEmpty()) {
            return registry.findByChannelCode(request.getChannel()).orElseThrow(() -> new PaymentGatewayException("Unsupported channel: " + request.getChannel()));
        }
        return registry.findByAccountIdentifier(account).orElseThrow(() -> new PaymentGatewayException("Unable to resolve adapter for account"));
    }

    private void validate(PaymentRequest request, Merchant merchant, boolean collect, String api) {
        if (request == null) throw new PaymentGatewayException("Request body is required");
        if (merchant == null || !merchant.getAccount_number().equals(request.getMerchantNumber())) throw new PaymentGatewayException("Verified merchant does not match request merchant");
        if (!"ACTIVE".equalsIgnoreCase(merchant.getStatus())) throw new PaymentGatewayException("Merchant is not active");
        if (collect && (request.getPayer() == null || blank(request.getPayer().getValue()))) throw new PaymentGatewayException("payer.value is required");
        if (!collect && (request.getPayee() == null || blank(request.getPayee().getValue()))) throw new PaymentGatewayException("payee.value is required");
        if (!allowed(merchant, api)) throw new PaymentGatewayException("Merchant is not allowed to access " + api);
        MoneyAmount.of(request.getAmount());
    }

    private boolean allowed(Merchant merchant, String api) {
        String[] allowedApis = merchant.getAllowed_apis();
        if (allowedApis == null) return false;
        for (String allowed : allowedApis) if (api.equals(allowed)) return true;
        return false;
    }

    private PaymentResult result(PaymentRequest request, PaymentChannelAdapter adapter, GateWayResponse gatewayResponse) {
        PaymentResult result = new PaymentResult();
        result.setReference(request.getReference());
        result.setTransactionId(gatewayResponse == null ? request.getReference() : gatewayResponse.getNetworkId());
        result.setStatus(gatewayResponse == null ? "SUBMITTED" : gatewayResponse.getTransactionStatus());
        result.setChannel(adapter.channelCode());
        result.setCurrency(request.getCurrency());
        result.setMessage(gatewayResponse == null ? "Submitted" : gatewayResponse.getMessage());
        result.setProviderResponse(gatewayResponse == null ? "" : gatewayResponse.toString());
        return result;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

