package net.citotech.cito.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.RefundRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.platform.CitoFeatureAccessService;
import net.citotech.cito.refund.RefundRecord;
import net.citotech.cito.refund.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** v2 partial/full refund lifecycle endpoint. */
@RestController
@RequestMapping(path = "/api/v2/refunds")
public class RefundsV2Controller {
    private final RefundService refundService;
    private final V2RequestSecurityService securityService;
    private final CitoFeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;

    public RefundsV2Controller(
            RefundService refundService,
            V2RequestSecurityService securityService,
            CitoFeatureAccessService featureAccessService,
            ObjectMapper objectMapper) {
        this.refundService = refundService;
        this.securityService = securityService;
        this.featureAccessService = featureAccessService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> requestRefund(
            @RequestBody String body, HttpServletRequest servletRequest) {
        try {
            RefundRequest request = objectMapper.readValue(body, RefundRequest.class);
            Merchant merchant =
                    securityService.verify(servletRequest, body, request.getMerchantNumber());
            String environment = featureAccessService.normalizeEnvironment(
                    servletRequest.getHeader("X-CPay-Environment"));
            featureAccessService.require(merchant.getId(), "REFUND_OPERATIONS", environment);
            BigDecimal amount =
                    request.getAmount() == null || request.getAmount().isBlank()
                            ? null
                            : MoneyAmount.of(request.getAmount()).asBigDecimal();
            RefundRecord refund =
                    refundService.requestRefund(
                            merchant,
                            request.getOriginalReference(),
                            request.getReference(),
                            amount,
                            request.getReason());
            return ResponseEntity.accepted().body(refund);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "REFUND_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid refund request");
        }
    }

    @GetMapping(path = "/{reference}")
    public ResponseEntity<?> getRefund(
            @PathVariable String reference,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            String environment = featureAccessService.normalizeEnvironment(
                    servletRequest.getHeader("X-CPay-Environment"));
            featureAccessService.require(merchant.getId(), "REFUND_OPERATIONS", environment);
            return refundService.findByReference(merchant.getId(), reference)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(
                            () ->
                                    error(
                                            HttpStatus.NOT_FOUND,
                                            "REFUND_NOT_FOUND",
                                            "No refund found for reference " + reference));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.FORBIDDEN, "REFUND_ACCESS_REJECTED", e.getMessage());
        }
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(java.util.Map.of("code", code, "message", message == null ? "" : message));
    }
}
