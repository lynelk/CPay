package net.citotech.cito.billing.pricing;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.citotech.cito.billing.baas.BillingBaasContext;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Effective-dated contract-specific price selection. Overrides are submitted then approved by a
 * different service-account actor; resolution considers only APPROVED rows and fails on ambiguous
 * active contracts instead of selecting whichever SQL happens to return first.
 */
@Service
public class ContractPriceOverrideService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PriceBookRepository priceBookRepository;

    public ContractPriceOverrideService(
            NamedParameterJdbcTemplate jdbcTemplate, PriceBookRepository priceBookRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.priceBookRepository = priceBookRepository;
    }

    @Transactional
    public Map<String, Object> submit(
            BillingBaasContext context,
            String contractReference,
            String serviceCode,
            String meterCode,
            long priceBookVersionId,
            Instant effectiveFrom,
            Instant effectiveTo) {
        requireContext(context);
        Instant start = effectiveFrom == null ? Instant.now() : effectiveFrom;
        if (effectiveTo != null && !effectiveTo.isAfter(start)) {
            throw new PaymentGatewayException("Price override effectiveTo must be after effectiveFrom");
        }
        long contractId = contractId(context.billingTenantId(), required(contractReference, "contractReference"));
        PriceBookVersion priceBook =
                priceBookRepository
                        .findVersionById(priceBookVersionId)
                        .orElseThrow(() -> new PaymentGatewayException("Price-book version was not found"));
        String service = required(serviceCode, "serviceCode").toUpperCase();
        String meter = required(meterCode, "meterCode");
        if (!priceBook.serviceCode().equals(service)
                || !priceBook.meterCode().equals(meter)
                || !"CUSTOMER_CHARGE".equals(priceBook.chargeType())) {
            throw new PaymentGatewayException("Price-book version does not match the contract pricing key");
        }
        if (priceBook.billingTenantId() != null
                && !priceBook.billingTenantId().equals(context.billingTenantId())) {
            throw new PaymentGatewayException("Price-book version belongs to another tenant");
        }
        String actor = actor(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("contract", contractId)
                        .addValue("service", service)
                        .addValue("meter", meter)
                        .addValue("price_book", priceBookVersionId)
                        .addValue("effective_from", Timestamp.from(start))
                        .addValue("effective_to", effectiveTo == null ? null : Timestamp.from(effectiveTo))
                        .addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO billing_contract_price_overrides "
                        + "(billing_tenant_id,billing_contract_id,service_code,meter_code,charge_type,"
                        + "price_book_version_id,status,effective_from,effective_to,created_by,submitted_by,submitted_at) "
                        + "VALUES (:tenant,:contract,:service,:meter,'CUSTOMER_CHARGE',:price_book,'SUBMITTED',"
                        + ":effective_from,:effective_to,:actor,:actor,CURRENT_TIMESTAMP)",
                p);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return view(context.billingTenantId(), id == null ? 0L : id);
    }

    @Transactional
    public Map<String, Object> approve(BillingBaasContext context, long overrideId) {
        requireContext(context);
        Map<String, Object> row = locked(context.billingTenantId(), overrideId);
        if (!"SUBMITTED".equals(String.valueOf(row.get("status")))) {
            throw new PaymentGatewayException("Only a SUBMITTED price override can be approved");
        }
        String actor = actor(context);
        if (actor.equals(String.valueOf(row.get("submittedBy")))) {
            throw new PaymentGatewayException("Price override maker and checker must be different actors");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("id", overrideId)
                        .addValue("contract", row.get("billingContractId"))
                        .addValue("service", row.get("serviceCode"))
                        .addValue("meter", row.get("meterCode"))
                        .addValue("start", row.get("effectiveFrom"))
                        .addValue("end", row.get("effectiveTo"))
                        .addValue("actor", actor);
        Long overlaps =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_contract_price_overrides "
                                + "WHERE billing_tenant_id=:tenant AND billing_contract_id=:contract "
                                + "AND service_code=:service AND meter_code=:meter AND charge_type='CUSTOMER_CHARGE' "
                                + "AND status='APPROVED' AND id<>:id "
                                + "AND effective_from < COALESCE(:end,'9999-12-31 23:59:59') "
                                + "AND (effective_to IS NULL OR effective_to > :start)",
                        p,
                        Long.class);
        if (overlaps != null && overlaps > 0) {
            throw new PaymentGatewayException("Approved contract price override would overlap an existing version");
        }
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_contract_price_overrides SET status='APPROVED',approved_by=:actor,"
                                + "approved_at=CURRENT_TIMESTAMP WHERE id=:id AND billing_tenant_id=:tenant "
                                + "AND status='SUBMITTED'",
                        p);
        if (updated != 1) {
            throw new PaymentGatewayException("Price override approval lost a concurrent update");
        }
        return view(context.billingTenantId(), overrideId);
    }

    public Optional<ResolvedContractPrice> resolve(
            long billingTenantId,
            String billingAccountReference,
            String contractReference,
            String serviceCode,
            String meterCode,
            Instant asOf) {
        if (billingTenantId <= 0 || asOf == null) {
            throw new PaymentGatewayException("Contract price resolution requires tenant and asOf");
        }
        String account = required(billingAccountReference, "billingAccountReference");
        String service = required(serviceCode, "serviceCode").toUpperCase();
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("account", account)
                        .addValue("service", service)
                        .addValue("as_of", Timestamp.from(asOf));
        String contractFilter = "";
        if (contractReference != null && !contractReference.isBlank()) {
            p.addValue("contract_reference", contractReference.trim());
            contractFilter = " AND c.contract_reference=:contract_reference";
        }
        List<Long> contracts =
                jdbcTemplate.query(
                        "SELECT DISTINCT c.id FROM billing_accounts a "
                                + "JOIN billing_subscriptions s ON s.billing_account_id=a.id AND s.billing_tenant_id=a.billing_tenant_id "
                                + "JOIN billing_contracts c ON c.id=s.billing_contract_id AND c.billing_tenant_id=a.billing_tenant_id "
                                + "WHERE a.billing_tenant_id=:tenant AND a.external_reference=:account "
                                + "AND s.service_code=:service AND s.status='ACTIVE' "
                                + "AND s.starts_at<=:as_of AND (s.ends_at IS NULL OR s.ends_at>:as_of) "
                                + "AND c.status='ACTIVE' AND c.effective_from<=:as_of "
                                + "AND (c.effective_to IS NULL OR c.effective_to>:as_of)"
                                + contractFilter
                                + " ORDER BY c.id LIMIT 2",
                        p,
                        (rs, rowNum) -> rs.getLong(1));
        if (contracts.size() > 1) {
            throw new PaymentGatewayException(
                    "Multiple active billing contracts match; contractReference is required");
        }
        if (contracts.isEmpty()) {
            return Optional.empty();
        }
        long contractId = contracts.get(0);
        p.addValue("contract", contractId).addValue("meter", required(meterCode, "meterCode"));
        List<Map<String, Object>> overrides =
                jdbcTemplate.queryForList(
                        "SELECT id,price_book_version_id FROM billing_contract_price_overrides "
                                + "WHERE billing_tenant_id=:tenant AND billing_contract_id=:contract "
                                + "AND service_code=:service AND meter_code=:meter AND charge_type='CUSTOMER_CHARGE' "
                                + "AND status='APPROVED' AND effective_from<=:as_of "
                                + "AND (effective_to IS NULL OR effective_to>:as_of) "
                                + "ORDER BY effective_from DESC,id DESC LIMIT 2",
                        p);
        if (overrides.size() > 1) {
            throw new PaymentGatewayException("Multiple approved contract price overrides are effective");
        }
        if (overrides.isEmpty()) {
            return Optional.of(new ResolvedContractPrice(contractId, null, null));
        }
        long overrideId = ((Number) overrides.get(0).get("id")).longValue();
        long priceBookId = ((Number) overrides.get(0).get("price_book_version_id")).longValue();
        PriceBookVersion version =
                priceBookRepository
                        .findVersionById(priceBookId)
                        .orElseThrow(() -> new PaymentGatewayException("Contract override price book was deleted"));
        return Optional.of(new ResolvedContractPrice(contractId, overrideId, version));
    }

    private long contractId(long tenantId, String reference) {
        List<Long> ids =
                jdbcTemplate.query(
                        "SELECT id FROM billing_contracts WHERE billing_tenant_id=:tenant "
                                + "AND contract_reference=:reference AND status IN ('APPROVED','ACTIVE') LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", reference),
                        (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) {
            throw new PaymentGatewayException("Contract must be APPROVED or ACTIVE before pricing override");
        }
        return ids.get(0);
    }

    private Map<String, Object> locked(long tenantId, long id) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_contract_id AS billingContractId,service_code AS serviceCode,"
                                + "meter_code AS meterCode,status,effective_from AS effectiveFrom,"
                                + "effective_to AS effectiveTo,submitted_by AS submittedBy "
                                + "FROM billing_contract_price_overrides WHERE id=:id AND billing_tenant_id=:tenant FOR UPDATE",
                        new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenantId));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Contract price override was not found for this tenant");
        }
        return rows.get(0);
    }

    private Map<String, Object> view(long tenantId, long id) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_contract_id AS billingContractId,service_code AS serviceCode,"
                                + "meter_code AS meterCode,charge_type AS chargeType,price_book_version_id AS priceBookVersionId,"
                                + "status,effective_from AS effectiveFrom,effective_to AS effectiveTo,created_by AS createdBy,"
                                + "submitted_by AS submittedBy,approved_by AS approvedBy,approved_at AS approvedAt "
                                + "FROM billing_contract_price_overrides WHERE id=:id AND billing_tenant_id=:tenant",
                        new MapSqlParameterSource().addValue("id", id).addValue("tenant", tenantId));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Contract price override was not found");
        }
        return rows.get(0);
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }

    private String actor(BillingBaasContext context) {
        return "SERVICE_ACCOUNT:" + context.serviceAccountId();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    public record ResolvedContractPrice(
            long billingContractId, Long overrideId, PriceBookVersion priceBookVersion) {}
}
