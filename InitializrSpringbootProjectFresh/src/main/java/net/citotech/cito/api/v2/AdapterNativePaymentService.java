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
import net.citotech.cito.gateway.GatewayExecutionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.gateway.PaymentGatewayRequest;
import net.citotech.cito.merchant.MerchantChannelCredentialService;
import net.citotech.cito.merchant.MerchantEnvironmentService;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdapterNativePaymentService {
    private final PaymentChannelRegistry registry;
    private final MerchantChannelCredentialService channelCredentialService;
    private final MerchantEnvironmentService environmentService;
    private final GatewayExecutionService gatewayExecutionService;
    private final String gatewayState;

    public AdapterNativePaymentService(PaymentChannelRegistry registry,
                                       MerchantChannelCredentialService channelCredentialService,
                                       MerchantEnvironmentService environmentService,
                                       GatewayExecutionService gatewayExecutionService,
                                       @Value("${custom.gatewaystate:SANDBOX}") String gatewayState) {
        this.registry = registry;
        this.channelCredentialService = channelCredentialService;
        this.environmentService = environmentService;
        this.gatewayExecutionService = gatewayExecutionService;
        this.gatewayState = gatewayState;
    }

    public String resolveEnvironment(String headerEnvironment, PaymentRequest request) {
        String requestEnvironment = request == null || request.getMetadata() == null ? null : request.getMetadata().get("environment");
        return environmentService.resolveRequestEnvironment(headerEnvironment, requestEnvironment);
    }

    public PaymentResult collect(PaymentRequest request, Merchant merchant, String environment) {
        validate(request, merchant, true, Common.API_MOBILE_MONEY_PAYIN);
        String account = request.getPayer().getValue();
        PaymentChannelAdapter adapter = adapterFor(request, account);
        String resolvedEnvironment = environmentService.normalizedEnvironment(environment);
        environmentService.enforceProductionLimit(merchant, resolvedEnvironment);
        channelCredentialService.ensureChannelReady(merchant, adapter.channelCode(), resolvedEnvironment);
        PaymentGatewayRequest gatewayRequest = adapterRequest(request, account, merchant, adapter, resolvedEnvironment);
        GateWayResponse response = gatewayExecutionService.execute(() -> adapter.collect(gatewayRequest));
        return result(request, adapter, resolvedEnvironment, response);
    }

    public PaymentResult payout(PaymentRequest request, Merchant merchant, String environment) {
        validate(request, merchant, false, Common.API_MOBILE_MONEY_PAYOUT);
        String account = request.getPayee().getValue();
        PaymentChannelAdapter adapter = adapterFor(request, account);
        String resolvedEnvironment = environmentService.normalizedEnvironment(environment);
        environmentService.enforceProductionLimit(merchant, resolvedEnvironment);
        channelCredentialService.ensureChannelReady(merchant, adapter.channelCode(), resolvedEnvironment);
        PaymentGatewayRequest gatewayRequest = adapterRequest(request, account, merchant, adapter, resolvedEnvironment);
        GateWayResponse response = gatewayExecutionService.execute(() -> adapter.payout(gatewayRequest));
        return result(request, adapter, resolvedEnvironment, response);
    }

    private PaymentGatewayRequest adapterRequest(PaymentRequest request, String account, Merchant merchant, PaymentChannelAdapter adapter, String environment) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("currency", request.getCurrency());
        metadata.put("country", request.getCountry());
        metadata.put("gatewayState", environment);
        metadata.put("credentialEnvironment", environment);
        metadata.put("applicationGatewayState", gatewayState);
        Map<String, Object> setup = channelCredentialService.loadDecrypted(merchant, adapter.channelCode(), environment);
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

    private PaymentResult result(PaymentRequest request, PaymentChannelAdapter adapter, String environment, GateWayResponse gatewayResponse) {
        PaymentResult result = new PaymentResult();
        result.setReference(request.getReference());
        result.setTransactionId(gatewayResponse == null ? request.getReference() : gatewayResponse.getNetworkId());
        result.setStatus(gatewayResponse == null ? "SUBMITTED" : gatewayResponse.getTransactionStatus());
        result.setChannel(adapter.channelCode());
        result.setEnvironment(environment);
        result.setCurrency(request.getCurrency());
        // Audit C6: gatewayResponse.getMessage() is expected to already be merchant-safe by the time it
        // reaches this funnel - every channel this service drives (MtnMomoAdapter, AirtelOpenApiAdapter,
        // YoPaymentsAdapter via ProviderEndpointExecutionService; AirtelMoneyAdapter via
        // ProviderEndpointClient) now translates raw provider text before returning its GateWayResponse.
        // If a future/other adapter is added without doing that, it should follow the same pattern
        // (see ProviderErrorTranslator) rather than this funnel silently re-guessing at a message it has
        // no reliable raw signal for.
        result.setMessage(gatewayResponse == null ? "Submitted" : gatewayResponse.getMessage());
        // Audit C6: this used to be gatewayResponse.toString(), which concatenates status/httpStatus/
        // message/transactionStatus into one opaque, uncontrolled string handed to the merchant - a
        // second, redundant exposure surface for whatever the message field carried (previously
        // including raw provider text). Expose only the one additional, already-safe, already-structured
        // fact (the provider HTTP status) that isn't captured by the other typed fields above.
        result.setProviderResponse(gatewayResponse == null ? "" : "httpStatus=" + gatewayResponse.getHttpStatus());
        return result;
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

