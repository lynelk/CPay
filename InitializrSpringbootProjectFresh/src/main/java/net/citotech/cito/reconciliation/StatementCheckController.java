package net.citotech.cito.reconciliation;

import net.citotech.cito.gateway.PaymentGatewayException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

// Audit E3 (extended by O2): method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN")
// rule already enforced by SecurityConfig's filterChain (defense in depth, not a replacement for
// it). Validating an uploaded provider statement is an equally sensitive admin action as the
// other reconciliation controllers already hardened this way.
@RestController
@RequestMapping(path = "/api/v2/admin/statements")
@PreAuthorize("hasRole('ADMIN')")
public class StatementCheckController {
    private final ProviderStatementValidator validator;

    public StatementCheckController(ProviderStatementValidator validator) {
        this.validator = validator;
    }

    /**
     * Audit O1: accepts either a CSV or an XLSX statement file (dispatched by file extension - see
     * {@link AbstractTabularStatementParser}); previously this only accepted a raw CSV text body.
     */
    @PostMapping(path = "/check")
    public long check(
            @RequestParam("provider") String provider, @RequestPart("file") MultipartFile file) {
        try {
            return validator.validate(provider, file.getOriginalFilename(), file.getBytes());
        } catch (IOException e) {
            throw new PaymentGatewayException(
                    "Unable to read uploaded statement file: " + e.getMessage());
        }
    }
}
