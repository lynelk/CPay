package net.citotech.cito.api.v2;

import javax.servlet.http.HttpServletRequest;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
import net.citotech.cito.security.ReplayProtectionService;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Security helper for /api/v2 request verification. */
@Service
public class V2RequestSecurityService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ReplayProtectionService replayProtectionService;

    public V2RequestSecurityService(NamedParameterJdbcTemplate jdbcTemplate,
                                    ReplayProtectionService replayProtectionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.replayProtectionService = replayProtectionService;
    }

    public Merchant verify(HttpServletRequest request, String body, String merchantNumber) {
        String timestamp = request.getHeader("X-CPay-Timestamp");
        String nonce = request.getHeader("X-CPay-Nonce");
        String version = request.getHeader("X-CPay-Signature-Version");
        String requestSignature = request.getHeader("X-CPay-Signature");

        if (!CanonicalRequestSigner.SIGNATURE_VERSION.equalsIgnoreCase(version)) {
            throw new PaymentGatewayException("Unsupported or missing signature version");
        }
        if (!replayProtectionService.accept(merchantNumber, timestamp, nonce)) {
            throw new PaymentGatewayException("Timestamp or nonce was rejected");
        }
        Merchant merchant = Common.getMerchantByAccountNumber(merchantNumber, jdbcTemplate);
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant was not found");
        }
        String canonical = CanonicalRequestSigner.canonicalize(request.getMethod(), request.getRequestURI(), timestamp, nonce, body);
        if (!CanonicalRequestSigner.verify(merchant, canonical, requestSignature)) {
            throw new PaymentGatewayException("Invalid request signature");
        }
        return merchant;
    }
}
