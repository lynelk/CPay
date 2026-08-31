package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import net.citotech.cito.billing.baas.BillingBaasContext;
import net.citotech.cito.billing.fx.BillingFxResolver;
import net.citotech.cito.billing.fx.BillingFxResolver.ResolvedFxRate;
import net.citotech.cito.billing.pricing.ContractPriceOverrideService.ResolvedContractPrice;
import net.citotech.cito.billing.tax.BillingTaxRuleResolver;
import net.citotech.cito.billing.tax.BillingTaxSnapshot;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the commercial rating chain without teaching the pure {@link RatingEngine} about
 * contracts, tax, FX or ledgers. This keeps price component math deterministic while retaining the
 * exact commercial context that wrapped it.
 */
@Service
public class BillingCommercialRatingService {
    private final PriceResolver priceResolver;
    private final PriceBookRepository priceBookRepository;
    private final RatingEngine ratingEngine;
    private final ContractPriceOverrideService contractPriceOverrideService;
    private final BillingTaxRuleResolver taxRuleResolver;
    private final BillingFxResolver fxResolver;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingCommercialRatingService(
            PriceResolver priceResolver,
            PriceBookRepository priceBookRepository,
            RatingEngine ratingEngine,
            ContractPriceOverrideService contractPriceOverrideService,
            BillingTaxRuleResolver taxRuleResolver,
            BillingFxResolver fxResolver,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.priceResolver = priceResolver;
        this.priceBookRepository = priceBookRepository;
        this.ratingEngine = ratingEngine;
        this.contractPriceOverrideService = contractPriceOverrideService;
        this.taxRuleResolver = taxRuleResolver;
        this.fxResolver = fxResolver;
        this.jdbcTemplate = jdbcTemplate;
    }

    public CommercialRatingResult rate(
            BillingBaasContext context,
            String billingAccountReference,
            String contractReference,
            String serviceCode,
            String meterCode,
            BigDecimal sourceBaseAmount,
            String sourceCurrency,
            Instant asOf) {
        requireContext(context);
        Instant ratedAt = asOf == null ? Instant.now() : asOf;
        if (ratedAt.isAfter(Instant.now().plusSeconds(60))) {
            throw new PaymentGatewayException(
                    "Commercial rating time cannot be materially in the future");
        }
        BigDecimal sourceBase = nonNegative(sourceBaseAmount, "sourceBaseAmount");
        String service = required(serviceCode, "serviceCode").toUpperCase(java.util.Locale.ROOT);
        String meter = required(meterCode, "meterCode");

        Optional<ResolvedContractPrice> contractPrice =
                contractPriceOverrideService.resolve(
                        context.billingTenantId(),
                        required(billingAccountReference, "billingAccountReference"),
                        contractReference,
                        service,
                        meter,
                        ratedAt);

        PriceBookVersion customerVersion =
                contractPrice
                        .map(ResolvedContractPrice::priceBookVersion)
                        .filter(java.util.Objects::nonNull)
                        .orElseGet(
                                () ->
                                        priceResolver
                                                .resolve(
                                                        context.billingTenantId(),
                                                        service,
                                                        meter,
                                                        "CUSTOMER_CHARGE",
                                                        ratedAt)
                                                .orElseThrow(
                                                        () ->
                                                                new PaymentGatewayException(
                                                                        "No effective customer price book for "
                                                                                + service
                                                                                + "/"
                                                                                + meter)));

        ResolvedFxRate customerFx =
                fxResolver.resolve(sourceCurrency, customerVersion.currency(), ratedAt);
        BigDecimal normalizedBase = fxResolver.convert(sourceBase, customerFx);
        RatedCharge customerCharge =
                ratingEngine.rateVersion(
                        customerVersion,
                        context.billingTenantId(),
                        service,
                        meter,
                        "CUSTOMER_CHARGE",
                        normalizedBase,
                        customerVersion.currency(),
                        ratedAt);

        var taxRule =
                taxRuleResolver.resolve(
                        context.billingTenantId(), "STANDARD", customerVersion.currency(), ratedAt);
        BillingTaxSnapshot tax =
                taxRuleResolver.calculate(
                        taxRule, customerCharge.ratedAmount(), customerVersion.currency());
        BigDecimal gross =
                customerCharge.ratedAmount().add(tax.taxAmount()).setScale(4, RoundingMode.HALF_UP);

        Long providerVersionId = null;
        BigDecimal providerCost = null;
        BigDecimal margin = null;
        Optional<PriceBookVersion> providerVersion =
                priceResolver.resolve(
                        context.billingTenantId(), service, meter, "PROVIDER_COST", ratedAt);
        if (providerVersion.isPresent()) {
            PriceBookVersion version = providerVersion.get();
            ResolvedFxRate providerBaseFx =
                    fxResolver.resolve(sourceCurrency, version.currency(), ratedAt);
            BigDecimal providerBase = fxResolver.convert(sourceBase, providerBaseFx);
            RatedCharge providerRated =
                    ratingEngine.rateVersion(
                            version,
                            context.billingTenantId(),
                            service,
                            meter,
                            "PROVIDER_COST",
                            providerBase,
                            version.currency(),
                            ratedAt);
            providerVersionId = version.id();
            providerCost = providerRated.ratedAmount();
            if (!version.currency().equalsIgnoreCase(customerVersion.currency())) {
                ResolvedFxRate costFx =
                        fxResolver.resolve(version.currency(), customerVersion.currency(), ratedAt);
                providerCost = fxResolver.convert(providerCost, costFx);
            }
            margin =
                    customerCharge
                            .ratedAmount()
                            .subtract(providerCost)
                            .setScale(4, RoundingMode.HALF_UP);
        }

        return new CommercialRatingResult(
                contractPrice.map(ResolvedContractPrice::billingContractId).orElse(null),
                contractPrice.map(ResolvedContractPrice::overrideId).orElse(null),
                service,
                meter,
                customerFx.sourceCurrency(),
                sourceBase.setScale(4, RoundingMode.HALF_UP),
                customerVersion.currency(),
                normalizedBase,
                customerVersion.id(),
                providerVersionId,
                customerCharge.ratedAmount(),
                tax.taxRuleVersionId(),
                tax.taxCode(),
                tax.taxRate(),
                tax.taxAmount(),
                gross,
                providerCost,
                margin,
                customerFx,
                ratedAt);
    }

    @Transactional
    public void retainEvidence(
            BillingBaasContext context,
            String reservationReference,
            CommercialRatingResult rating) {
        requireContext(context);
        if (rating == null || reservationReference == null || reservationReference.isBlank()) {
            throw new PaymentGatewayException(
                    "Commercial rating evidence requires reservation and rating");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("reservation", reservationReference.trim())
                        .addValue("contract", rating.billingContractId())
                        .addValue("override", rating.contractPriceOverrideId())
                        .addValue("service", rating.serviceCode())
                        .addValue("meter", rating.meterCode())
                        .addValue("source_currency", rating.sourceCurrency())
                        .addValue("source_base", rating.sourceBaseAmount())
                        .addValue("billing_currency", rating.billingCurrency())
                        .addValue("normalized_base", rating.normalizedBaseAmount())
                        .addValue("customer_price", rating.customerPriceBookVersionId())
                        .addValue("provider_price", rating.providerPriceBookVersionId())
                        .addValue("net", rating.customerNetAmount())
                        .addValue("tax_rule", rating.taxRuleVersionId())
                        .addValue("tax", rating.taxAmount())
                        .addValue("gross", rating.grossAmount())
                        .addValue("provider_cost", rating.providerCostAmount())
                        .addValue("margin", rating.marginAmount())
                        .addValue("fx_source_id", rating.sourceFxRate().sourceFxRateId())
                        .addValue("fx_rate", rating.sourceFxRate().rate())
                        .addValue("fx_provider", rating.sourceFxRate().provider())
                        .addValue("rated_at", Timestamp.from(rating.ratedAt()));
        jdbcTemplate.update(
                "INSERT INTO billing_commercial_rating_evidence "
                        + "(billing_tenant_id,reservation_reference,billing_contract_id,contract_price_override_id,"
                        + "service_code,meter_code,source_currency,source_base_amount,billing_currency,"
                        + "normalized_base_amount,customer_price_book_version_id,provider_price_book_version_id,"
                        + "customer_net_amount,tax_rule_version_id,tax_amount,gross_amount,provider_cost_amount,"
                        + "margin_amount,source_fx_rate_id,fx_rate,fx_provider,rated_at) "
                        + "VALUES (:tenant,:reservation,:contract,:override,:service,:meter,:source_currency,"
                        + ":source_base,:billing_currency,:normalized_base,:customer_price,:provider_price,:net,"
                        + ":tax_rule,:tax,:gross,:provider_cost,:margin,:fx_source_id,:fx_rate,:fx_provider,:rated_at)",
                p);
        fxResolver.snapshot(
                context.billingTenantId(),
                "BAAS_CHARGE",
                reservationReference.trim(),
                rating.sourceFxRate());
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new PaymentGatewayException(field + " must be zero or greater");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }
}
