package net.citotech.cito.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fee schedule creation (audit A5). Mutating fees is admin-only, so this lives under {@code
 * /api/v2/admin/**}, which {@code SecurityConfig} already role-guards with {@code hasRole("ADMIN")}
 * at the filter-chain level - unlike the rest of {@code /api/v2/**}, which is {@code permitAll()}
 * at that layer and relies on each controller's own request signing.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/fees")
@PreAuthorize("hasRole('ADMIN')")
public class FeeScheduleAdminController {
    private final FeeScheduleService feeScheduleService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeeScheduleAdminController(
            FeeScheduleService feeScheduleService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.feeScheduleService = feeScheduleService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(path = "/schedules")
    public Map<String, Object> createSchedule(@RequestBody Map<String, Object> body) {
        String gatewayId = requiredString(body, "gatewayId");
        String service = requiredString(body, "service").toUpperCase();
        String chargeType = requiredString(body, "chargeType").toUpperCase();
        String chargingMethod = requiredString(body, "chargingMethod").toUpperCase();
        BigDecimal amount = MoneyAmount.of(requiredString(body, "amount")).asBigDecimal();
        Object merchantNumber = body.get("merchantNumber");
        Long merchantId =
                resolveMerchantId(merchantNumber == null ? null : merchantNumber.toString());
        Object effectiveFromRaw = body.get("effectiveFrom");
        Instant effectiveFrom =
                effectiveFromRaw == null || effectiveFromRaw.toString().isBlank()
                        ? null
                        : Instant.parse(effectiveFromRaw.toString());
        Object createdByRaw = body.get("createdBy");
        String createdBy = createdByRaw == null ? "admin" : createdByRaw.toString();

        FeeSchedule created =
                feeScheduleService.create(
                        gatewayId,
                        merchantId,
                        service,
                        chargeType,
                        chargingMethod,
                        amount,
                        effectiveFrom,
                        createdBy);
        return Map.of(
                "code",
                "000",
                "scheduleId",
                created.id(),
                "effectiveFrom",
                created.effectiveFrom().toString());
    }

    private Long resolveMerchantId(String merchantNumber) {
        if (merchantNumber == null || merchantNumber.isBlank()) {
            return null;
        }
        Merchant merchant = Common.getMerchantByAccountNumber(merchantNumber.trim(), jdbcTemplate);
        if (merchant == null) {
            throw new PaymentGatewayException("Merchant " + merchantNumber + " was not found");
        }
        return merchant.getId();
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.toString();
    }
}
