package net.citotech.cito.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.HostedCheckoutPayRequest;
import net.citotech.cito.api.v2.dto.InvoiceCreateRequest;
import net.citotech.cito.api.v2.dto.InvoiceResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merchant-facing invoice management (signed {@code /api/v2} requests, same as {@link
 * PaymentLinkController#create}) plus a public, unauthenticated view+pay path for the customer
 * holding an invoice link. The public routes deliberately live under {@code
 * /api/v2/invoices/pay/**} - {@code SecurityConfig} permits all of {@code /api/**} at the
 * filter-chain level and defers to each controller's own signing, so this guarantees the public
 * path is actually reachable (unlike the bare {@code /checkout/{token}} page, which isn't listed in
 * {@code SecurityConfig}'s permitAll matchers and is not something this change touches).
 */
@RestController
public class InvoiceController {
    private final InvoiceService invoiceService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public InvoiceController(
            InvoiceService invoiceService,
            V2RequestSecurityService securityService,
            ObjectMapper objectMapper) {
        this.invoiceService = invoiceService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/v2/invoices")
    public ResponseEntity<?> create(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            InvoiceCreateRequest request = objectMapper.readValue(body, InvoiceCreateRequest.class);
            Merchant merchant =
                    securityService.verify(servletRequest, body, request.getMerchantNumber());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(invoiceService.create(request, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "INVOICE_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid invoice request");
        }
    }

    @GetMapping(path = "/api/v2/invoices")
    public ResponseEntity<?> list(
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            List<InvoiceResponse> invoices = invoiceService.list(merchantNumber, merchant);
            return ResponseEntity.ok(invoices);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "INVOICE_LIST_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/api/v2/invoices/{reference}/actions/send")
    public ResponseEntity<?> send(
            @PathVariable("reference") String reference,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            return ResponseEntity.ok(invoiceService.send(merchantNumber, reference, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "INVOICE_SEND_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/api/v2/invoices/{reference}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable("reference") String reference,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            return ResponseEntity.ok(invoiceService.cancel(merchantNumber, reference, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "INVOICE_CANCEL_REJECTED", e.getMessage());
        }
    }

    @GetMapping(path = "/api/v2/invoices/pay/{token}")
    public ResponseEntity<?> view(@PathVariable("token") String token) {
        try {
            return ResponseEntity.ok(invoiceService.viewByToken(token));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping(path = "/api/v2/invoices/pay/{token}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pay(
            @PathVariable("token") String token,
            @RequestBody(required = false) HostedCheckoutPayRequest request,
            HttpServletRequest servletRequest) {
        try {
            return ResponseEntity.accepted()
                    .body(invoiceService.pay(token, request, servletRequest.getRemoteAddr()));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "INVOICE_PAYMENT_REJECTED", e.getMessage());
        }
    }

    @PostMapping(
            path = "/api/v2/invoices/pay/{token}",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> payForm(
            @PathVariable("token") String token,
            @RequestParam("payerAccount") String payerAccount,
            @RequestParam(value = "channel", required = false) String channel,
            HttpServletRequest servletRequest) {
        HostedCheckoutPayRequest request = new HostedCheckoutPayRequest();
        request.setPayerAccount(payerAccount);
        request.setChannel(channel);
        return pay(token, request, servletRequest);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
