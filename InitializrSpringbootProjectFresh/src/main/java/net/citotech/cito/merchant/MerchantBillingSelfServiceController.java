package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.billing.metering.MeterAggregationService;
import net.citotech.cito.billing.pricing.PriceBookRepository;
import net.citotech.cito.billing.pricing.PriceBookVersion;
import net.citotech.cito.billing.pricing.PriceComponent;
import net.citotech.cito.billing.pricing.PriceResolver;
import net.citotech.cito.billing.tenancy.BillingTenantResolver;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only billing self-service scoped to the authenticated merchant role/session. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/billing")
public class MerchantBillingSelfServiceController {
    private final BillingTenantResolver tenantResolver;
    private final PriceResolver priceResolver;
    private final PriceBookRepository priceBookRepository;
    private final MeterAggregationService meterAggregationService;

    public MerchantBillingSelfServiceController(
            BillingTenantResolver tenantResolver,
            PriceResolver priceResolver,
            PriceBookRepository priceBookRepository,
            MeterAggregationService meterAggregationService) {
        this.tenantResolver = tenantResolver;
        this.priceResolver = priceResolver;
        this.priceBookRepository = priceBookRepository;
        this.meterAggregationService = meterAggregationService;
    }

    @GetMapping(path = "/price-book")
    public ResponseEntity<?> priceBook(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("meterCode") String meterCode,
            HttpServletRequest request) {
        try {
            long tenantId = tenantResolver.resolveTenantId(merchantIdWithBillingAccess(request));
            PriceBookVersion version =
                    priceResolver
                            .resolve(tenantId, serviceCode, meterCode, "CUSTOMER_CHARGE")
                            .orElse(null);
            if (version == null) {
                return ResponseEntity.ok(
                        Map.of(
                                "configured", false,
                                "serviceCode", serviceCode,
                                "meterCode", meterCode));
            }
            List<PriceComponent> components = priceBookRepository.findComponents(version.id());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("configured", true);
            response.put("serviceCode", version.serviceCode());
            response.put("meterCode", version.meterCode());
            response.put("currency", version.currency());
            response.put("versionNo", version.versionNo());
            response.put("effectiveFrom", version.effectiveFrom());
            response.put("components", components);
            return ResponseEntity.ok(response);
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("MERCHANT_BILLING_FORBIDDEN", ex.getMessage()));
        }
    }

    @GetMapping(path = "/usage")
    public ResponseEntity<?> usage(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam("meterCode") String meterCode,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            HttpServletRequest request) {
        try {
            long tenantId = tenantResolver.resolveTenantId(merchantIdWithBillingAccess(request));
            ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
            Instant fromTime =
                    blank(from)
                            ? now.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant()
                            : Instant.parse(from);
            Instant toTime = blank(to) ? now.toInstant() : Instant.parse(to);
            if (!fromTime.isBefore(toTime)) {
                throw new IllegalArgumentException("from must be before to");
            }
            BigDecimal usage =
                    meterAggregationService.aggregate(
                            tenantId, serviceCode, meterCode, fromTime, toTime, null, null);
            return ResponseEntity.ok(
                    Map.of(
                            "serviceCode", serviceCode,
                            "meterCode", meterCode,
                            "from", fromTime,
                            "to", toTime,
                            "usage", usage));
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("MERCHANT_BILLING_FORBIDDEN", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(error("BILLING_USAGE_REJECTED", ex.getMessage()));
        }
    }

    private long merchantIdWithBillingAccess(HttpServletRequest request) {
        MerchantUser user = MerchantAuthorization.requireCapability(request, "BILLING");
        return user.getMerchant_id();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of("code", code, "message", message);
    }
}
