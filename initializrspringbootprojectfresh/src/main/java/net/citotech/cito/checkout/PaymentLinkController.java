package net.citotech.cito.checkout;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.HostedCheckoutPayRequest;
import net.citotech.cito.api.v2.dto.PaymentLinkCreateRequest;
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
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class PaymentLinkController {
    private final PaymentLinkService paymentLinkService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public PaymentLinkController(PaymentLinkService paymentLinkService,
                                 V2RequestSecurityService securityService,
                                 ObjectMapper objectMapper) {
        this.paymentLinkService = paymentLinkService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/api/v2/payment-links")
    public ResponseEntity<?> create(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            PaymentLinkCreateRequest request = objectMapper.readValue(body, PaymentLinkCreateRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            return ResponseEntity.status(HttpStatus.CREATED).body(paymentLinkService.create(request, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "PAYMENT_LINK_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid payment link request");
        }
    }

    @GetMapping(path = "/checkout/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> hostedCheckout(@PathVariable("token") String token) {
        try {
            PaymentLinkRecord link = paymentLinkService.getByToken(token);
            String html = "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>CPay Checkout</title><style>body{font-family:Arial,sans-serif;background:#f5f9fc;margin:0;padding:32px;color:#0b2b45}"
                + ".card{max-width:460px;margin:8vh auto;background:white;border:1px solid #d7e3ec;border-radius:18px;padding:28px;box-shadow:0 20px 50px rgba(11,43,69,.12)}"
                + "label,input,button{display:block;width:100%;box-sizing:border-box}input{height:46px;border:1px solid #c9d7e2;border-radius:10px;padding:0 12px;margin:8px 0 16px}"
                + "button{height:48px;border:0;border-radius:10px;background:#0797bd;color:white;font-weight:700}</style></head><body><main class=\"card\">"
                + "<h1>CPay Checkout</h1><p>" + escape(link.getDescription()) + "</p>"
                + "<h2>" + link.getCurrency() + " " + link.getAmount().toPlainString() + "</h2>"
                + "<form method=\"post\" action=\"/api/v2/checkout/" + token + "/pay\"><label>Mobile money number<input name=\"payerAccount\" autocomplete=\"tel\" required></label><button>Pay now</button></form>"
                + "</main></body></html>";
            return ResponseEntity.ok(html);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("<!doctype html><title>Unavailable</title><p>" + escape(e.getMessage()) + "</p>");
        }
    }

    @PostMapping(path = "/api/v2/checkout/{token}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pay(@PathVariable("token") String token,
                                 @RequestBody(required = false) HostedCheckoutPayRequest request,
                                 HttpServletRequest servletRequest) {
        try {
            return ResponseEntity.accepted().body(paymentLinkService.pay(token, request, servletRequest.getRemoteAddr()));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "CHECKOUT_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/api/v2/checkout/{token}/pay", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> payForm(@PathVariable("token") String token,
                                     @RequestParam("payerAccount") String payerAccount,
                                     @RequestParam(value = "channel", required = false) String channel,
                                     HttpServletRequest servletRequest) {
        HostedCheckoutPayRequest request = new HostedCheckoutPayRequest();
        request.setPayerAccount(payerAccount);
        request.setChannel(channel);
        return pay(token, request, servletRequest);
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
