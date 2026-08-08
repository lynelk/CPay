package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a new price-book version and its components (the admin authoring surface, Slice 20),
 * mirroring {@code FeeScheduleService.create()}'s "close, don't delete" pattern: publishing a new
 * version never deletes the previous one - it closes the previous version's {@code effective_to}
 * window (set to the new version's {@code effectiveFrom}) so rate history is preserved, and the new
 * version becomes the active one for its {@code (billingTenantId, serviceCode, meterCode,
 * chargeType)} key.
 */
@Service
public class PriceBookAuthoringService {
    private final PriceBookRepository repository;

    public PriceBookAuthoringService(PriceBookRepository repository) {
        this.repository = repository;
    }

    /** One price component in a publish request, before it has a database id. */
    public record ComponentDraft(
            String componentType,
            BigDecimal flatAmount,
            BigDecimal percentageRate,
            String tierDefinitionJson) {}

    @Transactional
    public PriceBookVersion publish(
            Long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            String currency,
            List<ComponentDraft> components,
            Instant effectiveFrom,
            String createdBy) {
        requireNonBlank(serviceCode, "serviceCode");
        requireNonBlank(meterCode, "meterCode");
        requireOneOf(chargeType, "chargeType", "CUSTOMER_CHARGE", "PROVIDER_COST");
        requireNonBlank(currency, "currency");
        if (components == null || components.isEmpty()) {
            throw new PaymentGatewayException("At least one price component is required");
        }
        components.forEach(this::validateComponent);

        Instant startsAt = effectiveFrom == null ? Instant.now() : effectiveFrom;
        int nextVersionNo =
                repository.nextVersionNo(billingTenantId, serviceCode, meterCode, chargeType);
        repository.closeOpenVersions(billingTenantId, serviceCode, meterCode, chargeType, startsAt);
        long versionId =
                repository.insertVersion(
                        billingTenantId,
                        serviceCode,
                        meterCode,
                        chargeType,
                        currency,
                        nextVersionNo,
                        startsAt,
                        createdBy);

        int sequenceNo = 1;
        for (ComponentDraft component : components) {
            repository.insertComponent(
                    versionId,
                    component.componentType(),
                    sequenceNo++,
                    component.flatAmount(),
                    component.percentageRate(),
                    component.tierDefinitionJson());
        }

        return new PriceBookVersion(
                versionId,
                billingTenantId,
                serviceCode,
                meterCode,
                chargeType,
                currency,
                nextVersionNo,
                startsAt,
                null);
    }

    private void validateComponent(ComponentDraft component) {
        requireOneOf(
                component.componentType(),
                "componentType",
                "FLAT",
                "PERCENTAGE",
                "TIER",
                "MINIMUM",
                "MAXIMUM");
        if ("PERCENTAGE".equals(component.componentType()) && component.percentageRate() == null) {
            throw new PaymentGatewayException("PERCENTAGE component requires percentageRate");
        }
        boolean requiresFlatAmount =
                "FLAT".equals(component.componentType())
                        || "MINIMUM".equals(component.componentType())
                        || "MAXIMUM".equals(component.componentType());
        if (requiresFlatAmount && component.flatAmount() == null) {
            throw new PaymentGatewayException(
                    component.componentType() + " component requires flatAmount");
        }
        if ("TIER".equals(component.componentType())
                && (component.tierDefinitionJson() == null
                        || component.tierDefinitionJson().isBlank())) {
            throw new PaymentGatewayException("TIER component requires tierDefinitionJson");
        }
    }

    private void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
    }

    private void requireOneOf(String value, String field, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(value)) {
                return;
            }
        }
        throw new PaymentGatewayException(field + " must be one of " + Arrays.toString(allowed));
    }
}
