package net.citotech.cito.api.v2;

import jakarta.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.batch.BatchPayoutStatusService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantEnvironmentService;
import net.citotech.cito.sandbox.SandboxFinancialSimulationService;
import net.citotech.cito.sandbox.SandboxProductionGuardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Batch payout status and retry with an explicit sandbox/production execution boundary. */
@RestController
@RequestMapping(path = "/api/v2/batch-payouts")
public class BatchPayoutsV2Controller {
    private final BatchPayoutStatusService batchPayoutStatusService;
    private final V2RequestSecurityService securityService;
    private final MerchantEnvironmentService environmentService;
    private final SandboxProductionGuardService productionGuard;
    private final SandboxFinancialSimulationService sandboxSimulation;

    public BatchPayoutsV2Controller(
            BatchPayoutStatusService batchPayoutStatusService,
            V2RequestSecurityService securityService,
            MerchantEnvironmentService environmentService,
            SandboxProductionGuardService productionGuard,
            SandboxFinancialSimulationService sandboxSimulation) {
        this.batchPayoutStatusService = batchPayoutStatusService;
        this.securityService = securityService;
        this.environmentService = environmentService;
        this.productionGuard = productionGuard;
        this.sandboxSimulation = sandboxSimulation;
    }

    @GetMapping(path = "/{batchId}/status")
    public ResponseEntity<?> status(
            @PathVariable long batchId,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            String environment = compatibilityEnvironment(servletRequest);
            if (MerchantEnvironmentService.SANDBOX.equals(environment)) {
                return ResponseEntity.ok(sandboxSimulation.batchStatus(merchant.getId(), batchId));
            }
            return ResponseEntity.ok(batchPayoutStatusService.status(batchId, merchant.getId()));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping(path = "/{batchId}/retry-failed")
    public ResponseEntity<?> retryFailed(
            @PathVariable long batchId,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            String environment = compatibilityEnvironment(servletRequest);
            if (MerchantEnvironmentService.SANDBOX.equals(environment)) {
                return ResponseEntity.ok(
                        sandboxSimulation.retryFailedBatch(merchant.getId(), batchId));
            }
            productionGuard.reserveProductionExecution(
                    merchant, environment, "BATCH_PAYOUT", "batch-retry:" + batchId);
            int retried = batchPayoutStatusService.retryFailed(batchId, merchant.getId());
            return ResponseEntity.ok(java.util.Map.of("code", "000", "retriedCount", retried));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (IllegalStateException e) {
            return error(
                    HttpStatus.FORBIDDEN,
                    "PRODUCTION_CAPABILITY_NOT_ENABLED",
                    e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
        }
    }

    /** Existing batch callers remain production unless they explicitly select SANDBOX. */
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
