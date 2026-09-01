package net.citotech.cito.fees;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Versioned fee schedules + a preview/simulation endpoint (audit A5). Read-only lookups are
 * unauthenticated by design (they reveal no merchant data, only what a fee *would* be), matching
 * the read-only "simulate" intent; schedule creation is admin-only work done elsewhere and this
 * controller only exposes the reader + a simulate calculation.
 */
@RestController
@RequestMapping(path = "/api/v2/fees")
public class FeeSimulationController {
    private final FeeScheduleService feeScheduleService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeeSimulationController(FeeScheduleService feeScheduleService, NamedParameterJdbcTemplate jdbcTemplate) {
        this.feeScheduleService = feeScheduleService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(path = "/simulate")
    public Map<String, Object> simulate(@RequestBody Map<String, String> body) {
        String gatewayId = required(body, "gatewayId");
        String service = required(body, "service").toUpperCase();
        String chargeType = required(body, "chargeType").toUpperCase();
        BigDecimal amount = MoneyAmount.of(required(body, "amount")).asBigDecimal();
        Long merchantId = resolveMerchantId(body.get("merchantNumber"));

        Optional<FeeSchedule> schedule = feeScheduleService.currentSchedule(gatewayId, merchantId, service, chargeType);
        if (schedule.isEmpty()) {
            return Map.of(
                "code", "000",
                "configured", false,
                "message", "No fee schedule is configured for this gateway/service/chargeType yet");
        }

        BigDecimal fee = schedule.get().apply(amount);
        return Map.of(
            "code", "000",
            "configured", true,
            "gatewayId", gatewayId,
            "service", service,
            "chargeType", chargeType,
            "chargingMethod", schedule.get().chargingMethod(),
            "scheduleId", schedule.get().id(),
            "merchantSpecific", schedule.get().merchantId() != null,
            "amount", amount,
            "fee", fee.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @GetMapping(path = "/schedules/current")
    public Map<String, Object> current(@RequestParam String gatewayId,
            @RequestParam String service,
            @RequestParam String chargeType,
            @RequestParam(required = false) String merchantNumber) {
        Long merchantId = resolveMerchantId(merchantNumber);
        Optional<FeeSchedule> schedule = feeScheduleService.currentSchedule(
            gatewayId, merchantId, service.toUpperCase(), chargeType.toUpperCase());
        if (schedule.isEmpty()) {
            return Map.of("code", "000", "configured", false);
        }
        FeeSchedule s = schedule.get();
        return Map.of(
            "code", "000",
            "configured", true,
            "scheduleId", s.id(),
            "chargingMethod", s.chargingMethod(),
            "amount", s.amount(),
            "merchantSpecific", s.merchantId() != null,
            "effectiveFrom", s.effectiveFrom().toString());
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

    private String required(Map<String, String> body, String field) {
        String value = body.get(field);
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value;
    }
}