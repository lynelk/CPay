package net.citotech.cito.billing.integration.cpay;

import java.util.Optional;
import net.citotech.cito.fees.FeeSchedule;
import net.citotech.cito.fees.FeeScheduleService;
import org.springframework.stereotype.Service;

/**
 * Read-only projection of the legacy, live {@link FeeScheduleService#currentSchedule} into the new
 * {@code billing_price_components} (V43) shape, for shadow comparison only in this phase - nothing
 * writes a real price-book row from this projection yet. {@code FLAT_FEE} and {@code PERCENTAGE}
 * charging methods project cleanly; {@code TIER} is explicitly flagged not-yet- computable rather
 * than reusing {@link FeeSchedule#apply}'s flat-fallback behaviour, since that fallback is a known,
 * documented gap (see that method's own comment) and not a correct tier calculation - silently
 * treating it as a flat fee here would misrepresent what the legacy system actually charges.
 */
@Service
public class LegacyFeeSchedulePriceAdapter {
    private final FeeScheduleService feeScheduleService;

    public LegacyFeeSchedulePriceAdapter(FeeScheduleService feeScheduleService) {
        this.feeScheduleService = feeScheduleService;
    }

    public Optional<ProjectedPriceComponent> project(
            String gatewayId, Long merchantId, String service, String chargeType) {
        return feeScheduleService
                .currentSchedule(gatewayId, merchantId, service, chargeType)
                .map(this::toProjection);
    }

    private ProjectedPriceComponent toProjection(FeeSchedule schedule) {
        return switch (schedule.chargingMethod()) {
            case "FLAT_FEE" -> ProjectedPriceComponent.flat(schedule.amount());
            case "PERCENTAGE" -> ProjectedPriceComponent.percentage(schedule.amount());
            default ->
                    ProjectedPriceComponent.notComputable(
                            schedule.chargingMethod()
                                    + " charging method is not yet computable (fee_schedules id "
                                    + schedule.id()
                                    + ")");
        };
    }
}
