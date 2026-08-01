package net.citotech.cito.compliance;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/kyc")
@PreAuthorize("hasRole('ADMIN')")
public class KycController {
    private final KycService kycService;

    public KycController(KycService kycService) {
        this.kycService = kycService;
    }

    @PostMapping(path = "/merchants/{merchantId}/owners")
    public ResponseEntity<?> addOwner(@PathVariable("merchantId") long merchantId,
                                      @RequestBody Map<String, String> body) {
        try {
            long id = kycService.addBeneficialOwner(
                merchantId,
                body.get("fullName"),
                body.get("idType"),
                body.get("idValue"),
                parseDecimal(body.get("ownershipPercent")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "code", "000"));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "KYC_OWNER_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/merchants/{merchantId}/documents")
    public ResponseEntity<?> addDocument(@PathVariable("merchantId") long merchantId,
                                         @RequestBody Map<String, String> body) {
        try {
            long id = kycService.addDocument(
                merchantId,
                body.get("documentType"),
                body.get("storageRef"),
                body.get("documentHash"));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "code", "000"));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "KYC_DOCUMENT_REJECTED", e.getMessage());
        }
    }

    @GetMapping(path = "/merchants/{merchantId}")
    public ResponseEntity<?> summary(@PathVariable("merchantId") long merchantId) {
        return ResponseEntity.ok(kycService.merchantKycSummary(merchantId));
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
