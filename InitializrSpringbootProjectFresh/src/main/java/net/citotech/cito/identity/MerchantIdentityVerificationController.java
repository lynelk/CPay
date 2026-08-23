package net.citotech.cito.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merchant/service identity-verification surface for API v2 consumers such as NOLI Vendaz.
 *
 * <p>The merchant id is always derived from the RSA-authenticated v2 request. Callers cannot select
 * another merchant by supplying an internal id. Raw identity numbers and their hashes are never
 * returned from this surface.
 */
@RestController
@RequestMapping(path = "/api/v2/identity")
public class MerchantIdentityVerificationController {

    private final IdentityVerificationService verificationService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public MerchantIdentityVerificationController(
            IdentityVerificationService verificationService,
            V2RequestSecurityService securityService,
            ObjectMapper objectMapper) {
        this.verificationService = verificationService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(
            path = "/verify",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> verify(@RequestBody String body, HttpServletRequest request) {
        try {
            VerifyRequest input = objectMapper.readValue(body, VerifyRequest.class);
            if (blank(input.merchantNumber())) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_IDENTITY_REQUEST", "merchantNumber is required.");
            }
            Merchant merchant = securityService.verify(request, body, input.merchantNumber());
            if (merchant.getId() == null || merchant.getId() <= 0) {
                return error(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT", "Merchant identity is unavailable.");
            }
            Map<String, Object> result =
                    verificationService.verify(
                            merchant.getId(),
                            input.nin(),
                            input.fullName(),
                            input.msisdn(),
                            input.consentGranted(),
                            blank(input.requestedBy()) ? "MERCHANT_API" : input.requestedBy().trim());
            return ResponseEntity.ok(safeView(result));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request authentication failed.");
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "IDENTITY_VERIFICATION_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_IDENTITY_REQUEST", "Identity verification request could not be processed.");
        }
    }

    @GetMapping(path = "/requests/{reference}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(
            @PathVariable("reference") String reference,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest request) {
        try {
            Merchant merchant = securityService.verify(request, "", merchantNumber);
            if (merchant.getId() == null || merchant.getId() <= 0) {
                return error(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT", "Merchant identity is unavailable.");
            }
            Map<String, Object> row = verificationService.findRequestByReference(reference);
            if (row == null || !sameMerchant(row.get("merchant_id"), merchant.getId())) {
                return error(HttpStatus.NOT_FOUND, "IDENTITY_REQUEST_NOT_FOUND", "Identity verification request was not found.");
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("requestReference", row.get("request_reference"));
            view.put("subjectNameMasked", row.get("subject_name"));
            view.put("subjectMsisdnMasked", row.get("subject_msisdn"));
            view.put("identityNumberMask", row.get("identity_number_mask"));
            view.put("consentGranted", row.get("consent_granted"));
            view.put("status", row.get("request_status"));
            view.put("providerReference", row.get("provider_reference"));
            view.put("requestedBy", row.get("requested_by"));
            view.put("createdAt", row.get("created_at"));
            view.put("updatedAt", row.get("updated_at"));
            return ResponseEntity.ok(view);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request authentication failed.");
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_IDENTITY_REQUEST", "Identity verification status could not be read.");
        }
    }

    private Map<String, Object> safeView(Map<String, Object> result) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requestReference", result.get("requestReference"));
        view.put("subjectNameMasked", result.get("subjectNameMasked"));
        view.put("subjectMsisdnMasked", result.get("subjectMsisdnMasked"));
        view.put("identityNumberMask", result.get("identityNumberMask"));
        view.put("status", result.get("status"));
        view.put("providerReference", result.get("providerReference"));
        view.put("requestedBy", result.get("requestedBy"));
        return view;
    }

    private boolean sameMerchant(Object value, long merchantId) {
        if (value instanceof Number number) return number.longValue() == merchantId;
        try {
            return value != null && Long.parseLong(String.valueOf(value)) == merchantId;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("code", code, "message", message == null ? "Request rejected" : message));
    }

    public record VerifyRequest(
            String merchantNumber,
            String nin,
            String fullName,
            String msisdn,
            boolean consentGranted,
            String requestedBy) {}
}
