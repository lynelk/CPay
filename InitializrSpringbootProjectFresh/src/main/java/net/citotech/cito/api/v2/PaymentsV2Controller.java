package net.citotech.cito.api.v2;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.PaymentChannelResponse;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.api.v2.dto.PaymentStatusResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/api/v2")
public class PaymentsV2Controller {
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final PaymentStatusService paymentStatusService;
    private final V2RequestSecurityService securityService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public PaymentsV2Controller(PaymentOrchestrationService paymentOrchestrationService,
                                PaymentStatusService paymentStatusService,
                                V2RequestSecurityService securityService,
                                IdempotencyService idempotencyService,
                                ObjectMapper objectMapper) {
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.paymentStatusService = paymentStatusService;
        this.securityService = securityService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/payments/collect")
    public ResponseEntity<?> collect(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            Optional<PaymentResult> existing = idempotencyService.findExisting(request.getMerchantNumber(), idempotencyKey, body);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            PaymentResult result = paymentOrchestrationService.collect(request, merchant, servletRequest.getRemoteAddr());
            idempotencyService.record(request.getMerchantNumber(), idempotencyKey, body, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid collect request");
        }
    }

    @PostMapping(path = "/payments/payout")
    public ResponseEntity<?> payout(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            String idempotencyKey = servletRequest.getHeader("X-CPay-Idempotency-Key");
            Optional<PaymentResult> existing = idempotencyService.findExisting(request.getMerchantNumber(), idempotencyKey, body);
            if (existing.isPresent()) {
                return ResponseEntity.ok(existing.get());
            }
            PaymentResult result = paymentOrchestrationService.payout(request, merchant, servletRequest.getRemoteAddr());
            idempotencyService.record(request.getMerchantNumber(), idempotencyKey, body, result);
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid payout request");
        }
    }

    @GetMapping(path = "/channels")
    public List<PaymentChannelResponse> channels() {
        return paymentOrchestrationService.listChannels();
    }

    @GetMapping(path = "/balances")
    public ResponseEntity<?> balances(@RequestParam("merchantNumber") String merchantNumber,
                                      HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            List<Balance> balances = paymentOrchestrationService.balances(merchantNumber, merchant);
            return ResponseEntity.ok(balances);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "BALANCE_REJECTED", e.getMessage());
        }
    }

    @GetMapping(path = "/payments/{reference}")
    public ResponseEntity<?> status(@PathVariable("reference") String reference,
                                    @RequestParam("merchantNumber") String merchantNumber,
                                    HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            PaymentStatusResponse status = paymentStatusService.getStatus(merchant, reference);
            return ResponseEntity.ok(status);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "STATUS_REJECTED", e.getMessage());
        }
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}

