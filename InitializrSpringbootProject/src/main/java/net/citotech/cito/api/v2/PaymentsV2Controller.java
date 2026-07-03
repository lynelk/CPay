package net.citotech.cito.api.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.PaymentOrchestrationService;
import net.citotech.cito.api.v2.dto.ErrorResponse;
import net.citotech.cito.api.v2.dto.PaymentChannelResponse;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
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

@RestController
@RequestMapping(path = "/api/v2")
public class PaymentsV2Controller {
    private final PaymentOrchestrationService paymentOrchestrationService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public PaymentsV2Controller(PaymentOrchestrationService paymentOrchestrationService,
                                V2RequestSecurityService securityService,
                                ObjectMapper objectMapper) {
        this.paymentOrchestrationService = paymentOrchestrationService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/payments/collect")
    public ResponseEntity<?> collect(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentRequest request = objectMapper.readValue(body, PaymentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            PaymentResult result = paymentOrchestrationService.collect(request, merchant, Common.getIpAddress(servletRequest));
            return ResponseEntity.accepted().body(result);
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
            PaymentResult result = paymentOrchestrationService.payout(request, merchant, Common.getIpAddress(servletRequest));
            return ResponseEntity.accepted().body(result);
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
                                      @RequestBody(required = false) String body,
                                      HttpServletRequest servletRequest) {
        try {
            String canonicalBody = body == null ? "" : body;
            Merchant merchant = securityService.verify(servletRequest, canonicalBody, merchantNumber);
            List<Balance> balances = paymentOrchestrationService.balances(merchantNumber, merchant);
            return ResponseEntity.ok(balances);
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "BALANCE_REJECTED", e.getMessage());
        }
    }

    @GetMapping(path = "/payments/{reference}")
    public ResponseEntity<?> status(@PathVariable("reference") String reference) {
        return error(HttpStatus.NOT_IMPLEMENTED, "STATUS_NOT_MIGRATED", "Use /api/v1/doTransactionCheckStatus until the v2 status service is wired to the transaction repository");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }
}
