package net.citotech.cito.analytics;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/analytics")
public class MerchantIntelligenceController {
    private final MerchantIntelligenceService intelligenceService;
    private final MerchantSessionContext sessionContext;

    public MerchantIntelligenceController(
            MerchantIntelligenceService intelligenceService, MerchantSessionContext sessionContext) {
        this.intelligenceService = intelligenceService;
        this.sessionContext = sessionContext;
    }

    @GetMapping("/daily")
    public ResponseEntity<?> daily(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            HttpServletRequest request) {
        try {
            DateRange range = range(from, to);
            return ResponseEntity.ok(
                    intelligenceService.daily(
                            sessionContext.requireMerchantId(request), range.from(), range.to()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("ANALYTICS_QUERY_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/providers")
    public ResponseEntity<?> providers(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            HttpServletRequest request) {
        try {
            DateRange range = range(from, to);
            return ResponseEntity.ok(
                    intelligenceService.providers(
                            sessionContext.requireMerchantId(request), range.from(), range.to()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("ANALYTICS_QUERY_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/recommendations")
    public ResponseEntity<?> recommendations(HttpServletRequest request) {
        return ResponseEntity.ok(
                intelligenceService.recommendations(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/recommendations/acknowledge")
    public ResponseEntity<?> acknowledge(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            intelligenceService.acknowledge(
                    sessionContext.requireMerchantId(request),
                    text(body.get("recommendationCode")),
                    text(body.get("subjectReference")),
                    sessionContext.actor(request));
            return ResponseEntity.ok(Map.of("acknowledged", true));
        } catch (PaymentGatewayException e) {
            return bad("RECOMMENDATION_UPDATE_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        long merchantId = sessionContext.requireMerchantId(request);
        LocalDate today = LocalDate.now();
        for (LocalDate day = today.minusDays(7); !day.isAfter(today); day = day.plusDays(1)) {
            intelligenceService.refreshDaily(merchantId, day);
            intelligenceService.refreshProviderDaily(merchantId, day);
        }
        intelligenceService.generateRecommendations(merchantId);
        return ResponseEntity.ok(Map.of("refreshed", true));
    }

    private DateRange range(String from, String to) {
        LocalDate end = parseDate(to, LocalDate.now());
        LocalDate start = parseDate(from, end.minusDays(30));
        return new DateRange(start, end);
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Dates must use YYYY-MM-DD format");
        }
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record DateRange(LocalDate from, LocalDate to) {}
}