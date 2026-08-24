package net.citotech.cito.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Merchant-authenticated, PII-free identity-provider capability discovery. */
@RestController
@RequestMapping(path = "/api/v2/identity")
public class MerchantIdentityCapabilitiesController {

    private final V2RequestSecurityService securityService;
    private final List<IdentityVerificationConnector> connectors;

    public MerchantIdentityCapabilitiesController(
            V2RequestSecurityService securityService,
            List<IdentityVerificationConnector> connectors) {
        this.securityService = securityService;
        this.connectors = connectors;
    }

    @GetMapping(path = "/capabilities", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> capabilities(
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest request) {
        try {
            Merchant merchant = securityService.verify(request, "", merchantNumber);
            if (merchant.getId() == null || merchant.getId() <= 0) {
                return error(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT", "Merchant identity is unavailable.");
            }

            List<Map<String, Object>> providers = connectors.stream()
                    .sorted(Comparator.comparing(IdentityVerificationConnector::providerCode))
                    .map(connector -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("providerCode", connector.providerCode());
                        row.put("supportsSync", connector.supportsSync());
                        row.put("supportsAsync", connector.supportsAsync());
                        row.put("supportedIdentityTypes", connector.supportedIdentityTypes().stream().sorted().toList());
                        row.put("supportedCountries", connector.supportedCountries().stream().sorted().toList());
                        return row;
                    })
                    .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("merchantNumber", merchantNumber);
            body.put("providers", providers);
            return ResponseEntity.ok(body);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request authentication failed.");
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "IDENTITY_CAPABILITIES_UNAVAILABLE", "Identity provider capabilities could not be read.");
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
