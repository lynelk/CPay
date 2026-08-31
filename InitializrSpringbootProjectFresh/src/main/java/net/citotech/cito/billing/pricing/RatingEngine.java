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
 * Deterministically folds an effective-dated price book's components into one rated charge.
 * FLAT, PERCENTAGE and TIER components contribute against the base amount; MINIMUM/MAXIMUM clamp
 * the accumulated total. The exact price-book version, tier path, formula inputs and rounding
 * policy are retained by the rated-charge persistence layer for reproducibility.
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

    /** Empty when no price book is effective for this key at {@code asOf}. */
    public Optional<RatedCharge> rate(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            BigDecimal baseAmount,
            String currency,
            Instant asOf) {
        validateInputs(baseAmount, currency, asOf);
        Optional<PriceBookVersion> resolved =
                priceResolver.resolve(
                        billingTenantId, serviceCode, meterCode, chargeType, asOf);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(
                rateVersion(
                        resolved.get(),
                        billingTenantId,
                        serviceCode,
                        meterCode,
                        chargeType,
                        baseAmount,
                        currency,
                        asOf));
    }

    /**
     * Rates against an explicitly approved/selected immutable price-book version, used for contract
     * overrides. It validates that the selected version matches the requested commercial key and
     * was effective at the business time so an override cannot smuggle in an unrelated or stale
     * price book.
     */
    public RatedCharge rateVersion(
            PriceBookVersion version,
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            BigDecimal baseAmount,
            String currency,
            Instant asOf) {
        validateInputs(baseAmount, currency, asOf);
        if (version == null) {
            throw new IllegalArgumentException("priceBookVersion is required");
        }
        if (!version.serviceCode().equals(serviceCode)
                || !version.meterCode().equals(meterCode)
                || !version.chargeType().equals(chargeType)) {
            throw new IllegalStateException("Selected price-book version does not match the rating key");
        }
        if (version.billingTenantId() != null
                && (billingTenantId == null || !version.billingTenantId().equals(billingTenantId))) {
            throw new IllegalStateException("Selected price-book version belongs to another tenant");
        }
        if (version.effectiveFrom().isAfter(asOf)
                || (version.effectiveTo() != null && !version.effectiveTo().isAfter(asOf))) {
            throw new IllegalStateException("Selected price-book version was not effective at rating time");
        }
        if (!version.currency().equalsIgnoreCase(currency.trim())) {
            throw new IllegalStateException(
                    "Resolved price-book currency "
                            + version.currency()
                            + " does not match rating currency "
                            + currency);
        }

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
        formulaInputs.put("billingTenantId", billingTenantId);
        formulaInputs.put("serviceCode", serviceCode);
        formulaInputs.put("meterCode", meterCode);
        formulaInputs.put("chargeType", chargeType);
        formulaInputs.put("baseAmount", baseAmount);
        formulaInputs.put("currency", currency.trim().toUpperCase());
        formulaInputs.put("asOf", asOf.toString());
        formulaInputs.put("componentCount", components.size());

        return new RatedCharge(
                version.id(),
                ratedAmount,
                version.currency(),
                ROUNDING_POLICY,
                writeJson(tierPath),
                writeJson(formulaInputs));
    }

    private void validateInputs(BigDecimal baseAmount, String currency, Instant asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required for deterministic rating");
        }
        if (baseAmount == null || baseAmount.signum() < 0) {
            throw new IllegalArgumentException("baseAmount must be zero or greater");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required for deterministic rating");
        }
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
