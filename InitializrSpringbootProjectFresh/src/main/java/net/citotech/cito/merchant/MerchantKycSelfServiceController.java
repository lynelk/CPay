package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.compliance.KycService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant-scoped KYC/KYB self-service. Merchant id is always taken from the session. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/kyc")
public class MerchantKycSelfServiceController {
    private final KycService kycService;

    public MerchantKycSelfServiceController(KycService kycService) {
        this.kycService = kycService;
    }

    @GetMapping
    public ResponseEntity<?> summary(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(kycService.merchantKycSummary(merchantId(request)));
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", ex.getMessage()));
        }
    }

    @PostMapping(path = "/beneficial-owners")
    public ResponseEntity<?> addBeneficialOwner(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            long id =
                    kycService.addBeneficialOwner(
                            merchantId(request),
                            text(body.get("fullName")),
                            text(body.get("idType")),
                            text(body.get("idValue")),
                            decimal(body.get("ownershipPercent")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "PENDING"));
        } catch (PaymentGatewayException | IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error("KYC_OWNER_REJECTED", ex.getMessage()));
        }
    }

    @PostMapping(path = "/documents")
    public ResponseEntity<?> addDocument(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            long id =
                    kycService.addDocument(
                            merchantId(request),
                            text(body.get("documentType")),
                            text(body.get("storageRef")),
                            text(body.get("documentHash")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", id, "status", "PENDING"));
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.badRequest().body(error("KYC_DOCUMENT_REJECTED", ex.getMessage()));
        }
    }

    private long merchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        if (user.getMerchant_id() == null || user.getMerchant_id() <= 0) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user.getMerchant_id();
    }

    private BigDecimal decimal(Object value) {
        String raw = text(value).replace(",", "");
        if (raw.isEmpty()) return null;
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("ownershipPercent must be a valid number");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
