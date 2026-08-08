package net.citotech.cito.billing.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Idempotent JDBC writer for {@code billing_rated_charges} (Flyway {@code V44}), mirroring {@code
 * UsageEventRepository}'s insert-if-absent pattern.
 */
@Repository
public class RatedChargeRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RatedChargeRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> findIdByIdempotencyKey(String idempotencyKey) {
        List<Long> rows =
                jdbcTemplate.query(
                        "SELECT id FROM billing_rated_charges WHERE idempotency_key=:idempotency_key",
                        new MapSqlParameterSource("idempotency_key", idempotencyKey),
                        (rs, rowNum) -> rs.getLong("id"));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Inserts a rated charge unless one already exists for {@code idempotencyKey} (a retried rating
     * pass), in which case the pre-existing row's id is returned and nothing new is inserted.
     */
    public long insertIfAbsent(
            long billingTenantId,
            String serviceCode,
            String meterCode,
            String chargeType,
            String sourceReference,
            BigDecimal baseAmount,
            RatedCharge ratedCharge,
            String idempotencyKey) {
        Optional<Long> existing = findIdByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("price_book_version_id", ratedCharge.priceBookVersionId());
        p.addValue("service_code", serviceCode);
        p.addValue("meter_code", meterCode);
        p.addValue("charge_type", chargeType);
        p.addValue("source_reference", sourceReference);
        p.addValue("base_amount", baseAmount);
        p.addValue("rated_amount", ratedCharge.ratedAmount());
        p.addValue("currency", ratedCharge.currency());
        p.addValue("rounding_policy", ratedCharge.roundingPolicy());
        p.addValue("tier_path", ratedCharge.tierPathJson());
        p.addValue("formula_inputs", ratedCharge.formulaInputsJson());
        p.addValue("idempotency_key", idempotencyKey);

        try {
            jdbcTemplate.update(
                    "INSERT INTO billing_rated_charges (billing_tenant_id, price_book_version_id, "
                            + "service_code, meter_code, charge_type, source_reference, base_amount, "
                            + "rated_amount, currency, rounding_policy, tier_path, formula_inputs, idempotency_key) "
                            + "VALUES (:billing_tenant_id, :price_book_version_id, :service_code, "
                            + ":meter_code, :charge_type, :source_reference, :base_amount, :rated_amount, "
                            + ":currency, :rounding_policy, :tier_path, :formula_inputs, :idempotency_key)",
                    p);
        } catch (DuplicateKeyException raced) {
            return findIdByIdempotencyKey(idempotencyKey).orElseThrow(() -> raced);
        }

        return findIdByIdempotencyKey(idempotencyKey)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Inserted rated charge not found: " + idempotencyKey));
    }
}
