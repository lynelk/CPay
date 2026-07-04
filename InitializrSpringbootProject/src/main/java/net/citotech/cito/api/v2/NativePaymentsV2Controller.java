package net.citotech.cito.api.v2;

import java.util.Optional;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/api/v2/native/payments")
public class NativePaymentsV2Controller {
    private final AdapterNativePaymentService adapterNativePaymentService;
    private final V2RequestSecurityService securityService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public NativePaymentsV2Controller(AdapterNativePaymentService adapterNativePaymentService,
                                      V2RequestSecurityService securityService,
                                      IdempotencyService idempotencyService,
                                      ObjectMapper objectMapper) {
        this.adapterNativePaymentService = adapterNativePaymentService;
        this.securityService = securityService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/collect")
    public ResponseEntity<?> collect(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            Optional<PaymentResult> existing = idempotencyService.findExisting(request.getMerchantNumber(), idempotencyKey, body);
            if (existing.isPresent()) return ResponseEntity.ok(existing.get());
            PaymentResult result = adapterNativePaymentService.collect(request, merchant);
            idempotencyService.record(request.getMerchantNumber(), idempotencyKey, body, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid native collect request");
        }
    }

    @PostMapping(path = "/payout")
    public ResponseEntity<?> payout(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            Optional<PaymentResult> existing = idempotencyService.findExisting(request.getMerchantNumber(), idempotencyKey, body);
            if (existing.isPresent()) return ResponseEntity.ok(existing.get());
            PaymentResult result = adapterNativePaymentService.payout(request, merchant);
            idempotencyService.record(request.getMerchantNumber(), idempotencyKey, body, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid native payout request");
        }
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
