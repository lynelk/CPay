package net.citotech.cito.sandbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.admin.FeatureRegistryService;
import net.citotech.cito.merchant.MerchantEnvironmentService;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Runtime-generated catalog of sandbox-testable API surfaces and feature flags.
 *
 * <p>The catalog intentionally discovers request mappings from the running Spring application. A
 * newly added /api/v2 route therefore appears without anyone maintaining a second endpoint list.
 * Springdoc remains the canonical request/response schema and example source.
 */
@Service
public class SandboxCapabilityCatalogService {
    private final ApplicationContext applicationContext;
    private final FeatureRegistryService featureRegistryService;
    private final MerchantEnvironmentService environmentService;

    public SandboxCapabilityCatalogService(
            ApplicationContext applicationContext,
            FeatureRegistryService featureRegistryService,
            MerchantEnvironmentService environmentService) {
        this.applicationContext = applicationContext;
        this.featureRegistryService = featureRegistryService;
        this.environmentService = environmentService;
    }

    public Map<String, Object> catalog(long merchantId, String merchantNumber) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> guide = environmentService.sandboxGuide(merchantNumber);
        result.put("generatedAt", Instant.now().toString());
        result.put("generatedFromRuntimeMappings", true);
        result.put("environment", MerchantEnvironmentService.SANDBOX);
        result.put("sandboxBaseUrl", guide.get("sandboxBaseUrl"));
        result.put("productionBaseUrl", guide.get("productionBaseUrl"));
        result.put("requestHeader", "X-CPay-Environment: SANDBOX");
        result.put(
                "documentation",
                Map.of(
                        "swaggerUi", "/swagger-ui/index.html",
                        "openApiJson", "/v3/api-docs",
                        "openApiYaml", "/v3/api-docs.yaml"));
        result.put(
                "policy",
                "Every non-admin /api/v2 route is automatically catalogued in sandbox. "
                        + "Environment-aware money movement must use X-CPay-Environment: SANDBOX, "
                        + "and provider-facing operations must remain on sandbox adapters or simulators.");
        result.put("features", featureRegistryService.listEffective(merchantId));
        result.put("endpoints", endpoints());
        result.put("testAccounts", guide.get("testAccounts"));
        return result;
    }

    private List<Map<String, Object>> endpoints() {
        RequestMappingHandlerMapping mapping =
                applicationContext.getBean(
                        "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, org.springframework.web.method.HandlerMethod> entry :
                mapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            for (String path : info.getPatternValues()) {
                if (!path.startsWith("/api/v2") || path.startsWith("/api/v2/admin")) {
                    continue;
                }
                if (methods.isEmpty()) {
                    endpoints.add(endpoint("ANY", path, entry.getValue().getMethod().getName()));
                    continue;
                }
                for (RequestMethod method : methods) {
                    endpoints.add(endpoint(method.name(), path, entry.getValue().getMethod().getName()));
                }
            }
        }
        endpoints.sort(
                Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.get("path")))
                        .thenComparing(item -> String.valueOf(item.get("method"))));
        return List.copyOf(endpoints);
    }

    private Map<String, Object> endpoint(String method, String path, String handler) {
        Map<String, Object> endpoint = new LinkedHashMap<>();
        endpoint.put("method", method);
        endpoint.put("path", path);
        endpoint.put("scope", scope(path));
        endpoint.put("handler", handler);
        endpoint.put("sandboxHeaderRequiredForEnvironmentAwareCalls", true);
        endpoint.put("documentation", "/swagger-ui/index.html");
        return endpoint;
    }

    private String scope(String path) {
        if (path.startsWith("/api/v2/portal/")) {
            return "MERCHANT_PORTAL";
        }
        if (path.startsWith("/api/v2/merchant-self-service/")) {
            return "MERCHANT_SELF_SERVICE";
        }
        return "PUBLIC_API";
    }
}
