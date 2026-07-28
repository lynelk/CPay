package net.citotech.cito.crossborder;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.api.v2.dto.FxQuoteRequest;
import net.citotech.cito.api.v2.dto.TransferIntentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping(path = "/api/v2")
public class CrossBorderController {
    private final FxQuoteService fxQuoteService;
    private final CrossBorderTransferService transferService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public CrossBorderController(FxQuoteService fxQuoteService,
                                 CrossBorderTransferService transferService,
                                 V2RequestSecurityService securityService,
                                 ObjectMapper objectMapper) {
        this.fxQuoteService = fxQuoteService;
        this.transferService = transferService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(path = "/fx/quotes")
    public ResponseEntity<?> createQuote(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            FxQuoteRequest request = objectMapper.readValue(body, FxQuoteRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            return ResponseEntity.status(HttpStatus.CREATED).body(fxQuoteService.createQuote(request, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "FX_QUOTE_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid FX quote request");
        }
    }

    @PostMapping(path = "/cross-border/transfers")
    public ResponseEntity<?> createTransfer(@RequestBody String body, HttpServletRequest servletRequest) {
        try {
            TransferIntentRequest request = objectMapper.readValue(body, TransferIntentRequest.class);
            Merchant merchant = securityService.verify(servletRequest, body, request.getMerchantNumber());
            return ResponseEntity.status(HttpStatus.CREATED).body(transferService.createIntent(request, merchant));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "TRANSFER_INTENT_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid cross-border transfer request");
        }
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
