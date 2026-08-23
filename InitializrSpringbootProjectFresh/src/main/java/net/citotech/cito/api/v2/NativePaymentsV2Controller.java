package net.citotech.cito.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.sandbox.SandboxProductionGuardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/native/payments")
public class NativePaymentsV2Controller {
    private final AdapterNativePaymentService adapterNativePaymentService;
    private final V2RequestSecurityService securityService;
    private final IdempotencyService idempotencyService;
    private final SandboxProductionGuardService productionGuard;
    private final ObjectMapper objectMapper;

    public NativePaymentsV2Controller(
            AdapterNativePaymentService adapterNativePaymentService,
            V2RequestSecurityService securityService,
            IdempotencyService idempotencyService,
            SandboxProductionGuardService productionGuard,
            ObjectMapper objectMapper) {
        this.adapterNativePaymentService = adapterNativePaymentService;
        this.securityService = securityService;
        this.idempotencyService = idempotencyService;
        this.productionGuard = productionGuard;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/collect")
    public ResponseEntity<?> collect(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            String environment =
                    adapterNativePaymentService.resolveEnvironment(
                            servletRequest.getHeader("X-CPay-Environment"), request);
            Merchant merchant =
                    securityService.verify(servletRequest, body, request.getMerchantNumber());
            productionGuard.enforcePayment(merchant, environment, "COLLECT");
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            String idempotencyBody = bodyWithEnvironment(body, environment);
            Optional<PaymentResult> existing =
                    idempotencyService.findExisting(
                            request.getMerchantNumber(), idempotencyKey, idempotencyBody);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            PaymentResult result =
                    adapterNativePaymentService.collect(request, merchant, environment);
            idempotencyService.record(
                    request.getMerchantNumber(), idempotencyKey, idempotencyBody, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.FORBIDDEN, "PRODUCTION_CAPABILITY_NOT_ENABLED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid native collect request");
        }
    }

    @PostMapping(path = "/payout")
    public ResponseEntity<?> payout(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            String environment =
                    adapterNativePaymentService.resolveEnvironment(
                            servletRequest.getHeader("X-CPay-Environment"), request);
            Merchant merchant =
                    securityService.verify(servletRequest, body, request.getMerchantNumber());
            productionGuard.enforcePayment(merchant, environment, "PAYOUT");
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            String idempotencyBody = bodyWithEnvironment(body, environment);
            Optional<PaymentResult> existing =
                    idempotencyService.findExisting(
                            request.getMerchantNumber(), idempotencyKey, idempotencyBody);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            PaymentResult result =
                    adapterNativePaymentService.payout(request, merchant, environment);
            idempotencyService.record(
                    request.getMerchantNumber(), idempotencyKey, idempotencyBody, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (IllegalStateException e) {
            return error(HttpStatus.FORBIDDEN, "PRODUCTION_CAPABILITY_NOT_ENABLED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(
                    HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid native payout request");
        }
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }

    private String bodyWithEnvironment(String body, String environment) {
        return (body == null ? "" : body) + "\n#cpay-environment=" + environment;
    }
}
