package net.citotech.cito.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;

@Service
public class CitoFeatureAccessService {
    private static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");

    private final CitoEntitlementService entitlementService;

    public CitoFeatureAccessService(CitoEntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    public void require(long merchantId, String serviceCode, String environment) {
        entitlementService.requireEntitlement(merchantId, serviceCode, normalizeEnvironment(environment));
    }

    public boolean allowed(long merchantId, String serviceCode, String environment) {
        return entitlementService.hasEntitlement(merchantId, serviceCode, normalizeEnvironment(environment));
    }

    public String paymentEnvironment(PaymentRequest request) {
        if (request == null || request.getMetadata() == null) {
            return "SANDBOX";
        }
        return normalizeEnvironment(request.getMetadata().getOrDefault("environment", "SANDBOX"));
    }

    public String normalizeEnvironment(String value) {
        String normalized = value == null || value.isBlank()
                ? "SANDBOX"
                : value.trim().toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    public Map<String, Object> featureDiscovery(long merchantId) {
        List<Map<String, Object>> catalog = entitlementService.serviceCatalog();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", merchantId);
        result.put(
                "services",
                catalog.stream()
                        .map(
                                row -> {
                                    String serviceCode = String.valueOf(row.get("serviceCode"));
                                    Map<String, Object> feature = new LinkedHashMap<>(row);
                                    feature.put(
                                            "sandbox",
                                            allowed(merchantId, serviceCode, "SANDBOX"));
                                    feature.put(
                                            "production",
                                            allowed(merchantId, serviceCode, "PRODUCTION"));
                                    return feature;
                                })
                        .toList());
        return result;
    }
}