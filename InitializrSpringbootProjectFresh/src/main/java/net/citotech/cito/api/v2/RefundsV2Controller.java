package net.citotech.cito.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.RefundRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantEnvironmentService;
import net.citotech.cito.money.MoneyAmount;
import net.citotech.cito.refund.RefundRecord;
import net.citotech.cito.refund.RefundService;
import net.citotech.cito.sandbox.SandboxFinancialSimulationService;
import net.citotech.cito.sandbox.SandboxProductionGuardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** v2 partial refunds with an explicit sandbox/production execution boundary. */
@RestController
@RequestMapping(path = "/api/v2/refunds")
public class RefundsV2Controller {
    private final RefundService refundService;
    private final V2RequestSecurityService securityService;
    private final MerchantEnvironmentService environmentService;
    private final SandboxProductionGuardService productionGuard;
    private final SandboxFinancialSimulationService sandboxSimulation;
    private final ObjectMapper objectMapper;

    public RefundsV2Controller(
            RefundService refundService,
            V2RequestSecurityService securityService,
            MerchantEnvironmentService environmentService,
            SandboxProductionGuardService productionGuard,
            SandboxFinancialSimulationService sandboxSimulation,
            ObjectMapper objectMapper) {
        this.refundService = refundService;
        this.securityService = securityService;
        this.environmentService = environmentService;
        this.productionGuard = productionGuard;
        this.sandboxSimulation = sandboxSimulation;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> requestRefund(
            @RequestBody String body, HttpServletRequest servletRequest) {
        try {
            RefundRequest request = objectMapper.readValue(body, RefundRequest.class);
            Merchant merchant =
                    securityService.verify(servletRequest, body, request.getMerchantNumber());
            String environment = compatibilityEnvironment(servletRequest);
            BigDecimal amount =
                    request.getAmount() == null || request.getAmount().isBlank()
                            ? null
                            : MoneyAmount.of(request.getAmount()).asBigDecimal();
            if (MerchantEnvironmentService.SANDBOX.equals(environment)) {
                return ResponseEntity.accepted()
                        .body(
                                sandboxSimulation.refund(
                                        merchant.getId(),
                                        request.getOriginalReference(),
                                        request.getReference(),
                                        amount,
                                        request.getReason()));
            }
            productionGuard.reserveProductionExecution(
                    merchant, environment, "REFUND", request.getReference());
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
        } catch (IllegalStateException e) {
            return error(
                    HttpStatus.FORBIDDEN,
                    "PRODUCTION_CAPABILITY_NOT_ENABLED",
                    e.getMessage());
        } catch (PaymentGatewayException | IllegalArgumentException e) {
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
            String environment = compatibilityEnvironment(servletRequest);
            if (MerchantEnvironmentService.SANDBOX.equals(environment)) {
                return sandboxSimulation
                        .findRefund(merchant.getId(), reference)
                        .<ResponseEntity<?>>map(ResponseEntity::ok)
                        .orElseGet(
                                () ->
                                        error(
                                                HttpStatus.NOT_FOUND,
                                                "REFUND_NOT_FOUND",
                                                "No sandbox refund found for reference "
                                                        + reference));
            }
            return refundService
                    .findByReference(merchant.getId(), reference)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(
                            () ->
                                    error(
                                            HttpStatus.NOT_FOUND,
                                            "REFUND_NOT_FOUND",
                                            "No production refund found for reference "
                                                    + reference));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        }
    }

    /** Existing refund callers remain production unless they explicitly select SANDBOX. */
    private String compatibilityEnvironment(HttpServletRequest request) {
        String header = request.getHeader("X-CPay-Environment");
        if (header == null || header.isBlank()) {
            return MerchantEnvironmentService.PRODUCTION;
        }
        return environmentService.resolveRequestEnvironment(header, null);
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(java.util.Map.of("code", code, "message", message == null ? "" : message));
    }
}
