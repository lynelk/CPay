package net.citotech.cito.portal;

import java.util.List;
import javax.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import net.citotech.cito.balance.BalanceView;
import net.citotech.cito.balance.BalanceViewService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/merchant")
public class MerchantSelfServiceController {
    private final V2RequestSecurityService securityService;
    private final BalanceViewService balanceViewService;

    public MerchantSelfServiceController(V2RequestSecurityService securityService, BalanceViewService balanceViewService) {
        this.securityService = securityService;
        this.balanceViewService = balanceViewService;
    }

    @GetMapping(path = "/balances")
    public ResponseEntity<?> balances(@RequestParam("merchantNumber") String merchantNumber, HttpServletRequest request) {
        try {
            Merchant merchant = securityService.verify(request, "", merchantNumber);
            List<BalanceView> balances = balanceViewService.getBalances(merchant);
            return ResponseEntity.ok(balances);
        } catch (V2RequestSecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
