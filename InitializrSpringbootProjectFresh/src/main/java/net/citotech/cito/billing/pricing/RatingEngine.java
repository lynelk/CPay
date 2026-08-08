package net.citotech.cito.billing.pricing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Folds a resolved price book's components (in {@code sequence_no} order) into one rated charge.
 * {@code FLAT}, {@code PERCENTAGE}, and {@code TIER} each contribute a charge amount computed
 * against {@code baseAmount} - not compounded against each other or against a running total, since
 * real fee schedules don't charge a percentage of a percentage. {@code MINIMUM}/{@code MAXIMUM}
 * then clamp the accumulated total (e.g. "2.9% + flat fee, minimum X, maximum Y"). Rounding is
 * fixed at {@code HALF_UP} scale 2, recorded literally per rated charge (not just as a constant
 * reference) so an already-computed row stays reproducible even if the constant later changes.
 */
@Service
public class RatingEngine {
    private static final int ROUNDING_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final String ROUNDING_POLICY = "HALF_UP_SCALE_2";

    private final PriceResolver priceResolver;
    private final PriceBookRepository priceBookRepository;
    private final ObjectMapper objectMapper;

    public RatingEngine(
            PriceResolver priceResolver,
            PriceBookRepository priceBookRepository,
            ObjectMapper objectMapper) {
        this.priceResolver = priceResolver;
        this.priceBookRepository = priceBookRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Empty when no active price book resolves for this key - not an error, just
     * not-yet-configured.
     */
    public Optional<RatedCharge> rate(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            BigDecimal baseAmount,
            String currency,
            Instant asOf) {
        Optional<PriceBookVersion> resolved =
                priceResolver.resolve(billingTenantId, serviceCode, meterCode, chargeType);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        PriceBookVersion version = resolved.get();
        List<PriceComponent> components = priceBookRepository.findComponents(version.id());

        BigDecimal runningTotal = BigDecimal.ZERO;
        List<Map<String, Object>> tierPath = new ArrayList<>();
        for (PriceComponent component : components) {
            switch (component.componentType()) {
                case "FLAT" -> runningTotal = runningTotal.add(nullToZero(component.flatAmount()));
                case "PERCENTAGE" ->
                        runningTotal =
                                runningTotal.add(
                                        baseAmount
                                                .multiply(nullToZero(component.percentageRate()))
                                                .setScale(4, RoundingMode.HALF_UP));
                case "TIER" -> {
                    TierResult tierResult =
                            TierCalculator.calculate(
                                    baseAmount, parseTierBands(component.tierDefinitionJson()));
                    runningTotal = runningTotal.add(tierResult.totalCharge());
                    tierPath.addAll(toTierPathEntries(tierResult));
                }
                case "MINIMUM" -> {
                    BigDecimal floor = nullToZero(component.flatAmount());
                    if (runningTotal.compareTo(floor) < 0) {
                        runningTotal = floor;
                    }
                }
                case "MAXIMUM" -> {
                    BigDecimal ceiling = nullToZero(component.flatAmount());
                    if (runningTotal.compareTo(ceiling) > 0) {
                        runningTotal = ceiling;
                    }
                }
                default ->
                        throw new IllegalStateException(
                                "Unsupported billing_price_components.component_type: "
                                        + component.componentType());
            }
        }

        BigDecimal ratedAmount = runningTotal.setScale(ROUNDING_SCALE, ROUNDING_MODE);
        Map<String, Object> formulaInputs = new LinkedHashMap<>();
        formulaInputs.put("baseAmount", baseAmount);
        formulaInputs.put("componentCount", components.size());

        return Optional.of(
                new RatedCharge(
                        version.id(),
                        ratedAmount,
                        currency,
                        ROUNDING_POLICY,
                        writeJson(tierPath),
                        writeJson(formulaInputs)));
    }

    private List<TierBand> parseTierBands(String tierDefinitionJson) {
        if (tierDefinitionJson == null || tierDefinitionJson.isBlank()) {
            throw new IllegalStateException("TIER component is missing tier_definition");
        }
        try {
            List<Map<String, Object>> raw =
                    objectMapper.readValue(
                            tierDefinitionJson, new TypeReference<List<Map<String, Object>>>() {});
            List<TierBand> bands = new ArrayList<>();
            for (Map<String, Object> entry : raw) {
                Object upTo = entry.get("upTo");
                BigDecimal upToInclusive = upTo == null ? null : new BigDecimal(upTo.toString());
                BigDecimal rate = new BigDecimal(entry.get("rate").toString());
                bands.add(new TierBand(upToInclusive, rate));
            }
            return bands;
        } catch (IOException e) {
            throw new IllegalStateException("Invalid tier_definition JSON", e);
        }
    }

    private List<Map<String, Object>> toTierPathEntries(TierResult result) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (TierStep step : result.steps()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("bandFrom", step.bandFrom());
            entry.put("bandTo", step.bandTo());
            entry.put("rate", step.rate());
            entry.put("amountInBand", step.amountInBand());
            entry.put("contribution", step.contribution());
            entries.add(entry);
        }
        return entries;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rating output", e);
        }
    }
}
