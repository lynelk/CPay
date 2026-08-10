package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant team administration using MerchantRole as the only authorization model. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/team")
public class MerchantTeamController {
    private final MerchantTeamService teamService;

    public MerchantTeamController(MerchantTeamService teamService) {
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<?> list(HttpServletRequest request) {
        try {
            MerchantUser user = MerchantAuthorization.requireCapability(request, "ADMINISTRATION");
            return ResponseEntity.ok(Map.of("code", "000", "data", teamService.list(user.getMerchant_id())));
        } catch (PaymentGatewayException ex) {
            return forbidden(ex);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MerchantUser user = MerchantAuthorization.requireCapability(request, "ADMINISTRATION");
            long id = teamService.create(user.getMerchant_id(), body);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("code", "000", "message", "Merchant user created", "id", id));
        } catch (PaymentGatewayException ex) {
            return rejected(ex);
        }
    }

    @PutMapping(path = "/{id}")
    public ResponseEntity<?> update(
            @PathVariable("id") long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        try {
            MerchantUser user = MerchantAuthorization.requireCapability(request, "ADMINISTRATION");
            teamService.update(user.getMerchant_id(), user.getId(), id, body);
            return ResponseEntity.ok(Map.of("code", "000", "message", "Merchant user updated"));
        } catch (PaymentGatewayException ex) {
            return rejected(ex);
        }
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        try {
            MerchantUser user = MerchantAuthorization.requireCapability(request, "ADMINISTRATION");
            teamService.delete(user.getMerchant_id(), user.getId(), id);
            return ResponseEntity.ok(Map.of("code", "000", "message", "Merchant user deleted"));
        } catch (PaymentGatewayException ex) {
            return rejected(ex);
        }
    }

    private ResponseEntity<?> forbidden(PaymentGatewayException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("code", "MERCHANT_ADMIN_FORBIDDEN", "message", ex.getMessage()));
    }

    private ResponseEntity<?> rejected(PaymentGatewayException ex) {
        String message = ex.getMessage() == null ? "Merchant team request rejected" : ex.getMessage();
        if (message.contains("does not allow") || message.contains("login is required")) {
            return forbidden(ex);
        }
        return ResponseEntity.badRequest().body(Map.of("code", "MERCHANT_TEAM_REJECTED", "message", message));
    }
}
