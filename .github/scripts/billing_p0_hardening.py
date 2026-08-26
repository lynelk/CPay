from pathlib import Path

ROOT = Path("InitializrSpringbootProjectFresh")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"Expected patch anchor not found in {path}: {old[:100]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Patch anchor is not unique in {path}: {old[:100]!r}")
    path.write_text(text.replace(old, new, 1))


admin = ROOT / "src/main/java/net/citotech/cito/billing/baas/BillingBaasAdminService.java"
replace_once(
    admin,
    "import net.citotech.cito.Common;\n",
    "import net.citotech.cito.Common;\nimport net.citotech.cito.Model.Transaction;\nimport net.citotech.cito.Model.TransactionStatus;\n",
)
replace_once(
    admin,
    """        int updated =
                jdbcTemplate.update(
                        \"UPDATE billing_baas_tenant_profiles SET activation_status='ACTIVE',\"
                                + \"approved_by=:actor,approved_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP \"
                                + \"WHERE billing_tenant_id=:tenant AND activation_status IN ('READY','ACTIVE') \"
                                + \"AND legal_model_status='APPROVED' AND commercial_model_status='APPROVED' \"
                                + \"AND tax_model_status='APPROVED' AND funds_flow_status='APPROVED'\",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    \"BaaS tenant cannot activate until legal, commercial, tax and funds-flow reviews are approved\");
        }
""",
    """        int updated =
                jdbcTemplate.update(
                        \"UPDATE billing_baas_tenant_profiles SET activation_status='ACTIVE',\"
                                + \"activated_by=:actor,activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP \"
                                + \"WHERE billing_tenant_id=:tenant AND activation_status IN ('READY','ACTIVE') \"
                                + \"AND legal_model_status='APPROVED' AND commercial_model_status='APPROVED' \"
                                + \"AND tax_model_status='APPROVED' AND funds_flow_status='APPROVED' \"
                                + \"AND approved_by IS NOT NULL AND approved_by<>:actor\",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    \"BaaS tenant activation requires all reviews approved and a different activator from the reviewer\");
        }
""",
)
replace_once(
    admin,
    """        BillingAccount account = billingAccount(billingTenantId, accountReference);
        ensureChargingAccount(billingTenantId, account);
        String adjustmentKey = \"topup:\" + billingTenantId + \":\" + paymentReference;
""",
    """        BillingAccount account = billingAccount(billingTenantId, accountReference);
        verifySettledPayIn(billingTenantId, paymentReference, safeAmount);
        ensureChargingAccount(billingTenantId, account);
        String adjustmentKey = \"topup:\" + billingTenantId + \":\" + paymentReference;
""",
)
replace_once(
    admin,
    """                        + \"activation_status AS activationStatus,\"
                                + \"approved_by AS approvedBy,approved_at AS approvedAt,suspended_by AS suspendedBy,\"
                                + \"suspended_at AS suspendedAt,suspension_reason AS suspensionReason,updated_at AS updatedAt \"
""",
    """                        + \"activation_status AS activationStatus,\"
                                + \"approved_by AS approvedBy,approved_at AS approvedAt,activated_by AS activatedBy,\"
                                + \"activated_at AS activatedAt,suspended_by AS suspendedBy,suspended_at AS suspendedAt,\"
                                + \"suspension_reason AS suspensionReason,updated_at AS updatedAt \"
""",
)
replace_once(
    admin,
    """    private void ensureChargingAccount(long tenantId, BillingAccount account) {
""",
    """    private void verifySettledPayIn(long tenantId, String paymentReference, BigDecimal amount) {
        List<PaymentProof> proofs =
                jdbcTemplate.query(
                        \"SELECT t.status,t.tx_type,t.original_amount FROM merchant_transactions_log t \"
                                + \"JOIN billing_tenants bt ON bt.merchant_id=t.merchant_id \"
                                + \"WHERE bt.id=:tenant AND (t.tx_unique_id=:reference OR t.tx_merchant_ref=:reference \"
                                + \"OR t.tx_gateway_ref=:reference) ORDER BY t.id DESC LIMIT 2\",
                        new MapSqlParameterSource()
                                .addValue(\"tenant\", tenantId)
                                .addValue(\"reference\", paymentReference),
                        (rs, rowNum) ->
                                new PaymentProof(
                                        rs.getString(\"status\"),
                                        rs.getString(\"tx_type\"),
                                        rs.getBigDecimal(\"original_amount\")));
        if (proofs.size() != 1) {
            throw new PaymentGatewayException(
                    \"Verified payment reference must resolve to exactly one CPay transaction for this tenant\");
        }
        PaymentProof proof = proofs.get(0);
        TransactionStatus status = TransactionStatus.fromString(proof.status());
        if (status != TransactionStatus.SUCCESSFUL) {
            throw new PaymentGatewayException(
                    \"Prepaid funding requires a SUCCESSFUL CPay payment\");
        }
        if (!Transaction.TX_TYPE_PAYIN.equals(proof.transactionType())) {
            throw new PaymentGatewayException(\"Prepaid funding requires a CPay PAYIN transaction\");
        }
        if (proof.amount() == null || proof.amount().compareTo(amount) != 0) {
            throw new PaymentGatewayException(
                    \"Prepaid top-up amount must exactly match the settled CPay payment amount\");
        }
    }

    private void ensureChargingAccount(long tenantId, BillingAccount account) {
""",
)
replace_once(
    admin,
    """    private record BillingAccount(long id, long customerId, String currency, BigDecimal creditLimit) {}
""",
    """    private record PaymentProof(String status, String transactionType, BigDecimal amount) {}

    private record BillingAccount(long id, long customerId, String currency, BigDecimal creditLimit) {}
""",
)

protected_actions = ROOT / "src/main/java/net/citotech/cito/billing/baas/BillingBaasProtectedActionService.java"
replace_once(
    protected_actions,
    """    public Map<String, Object> find(long tenantId, long requestId) {
""",
    """    @Transactional
    public void consumeApproved(
            BillingBaasContext context,
            String actionType,
            String resourceType,
            String resourceReference) {
        String actor = actor(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue(\"tenant\", context.billingTenantId())
                        .addValue(\"action\", required(actionType, \"actionType\").toUpperCase())
                        .addValue(\"resource\", required(resourceType, \"resourceType\").toUpperCase())
                        .addValue(\"reference\", required(resourceReference, \"resourceReference\"))
                        .addValue(\"actor\", actor);
        int updated =
                jdbcTemplate.update(
                        \"UPDATE billing_protected_action_requests SET status='CONSUMED',\"
                                + \"consumed_by=:actor,consumed_at=CURRENT_TIMESTAMP \"
                                + \"WHERE billing_tenant_id=:tenant AND action_type=:action \"
                                + \"AND resource_type=:resource AND resource_reference=:reference \"
                                + \"AND status='APPROVED' AND requested_by=:actor \"
                                + \"AND approved_by IS NOT NULL AND approved_by<>:actor \"
                                + \"ORDER BY approved_at ASC LIMIT 1\",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    \"Protected action requires an unused approval from a different service account\");
        }
    }

    public Map<String, Object> find(long tenantId, long requestId) {
""",
)
replace_once(
    protected_actions,
    """                                + \"requested_at AS requestedAt,status,approved_by AS approvedBy,\"
                                + \"approved_at AS approvedAt,decision_reason AS decisionReason \"
""",
    """                                + \"requested_at AS requestedAt,status,approved_by AS approvedBy,\"
                                + \"approved_at AS approvedAt,decision_reason AS decisionReason,\"
                                + \"consumed_by AS consumedBy,consumed_at AS consumedAt \"
""",
)

charging = ROOT / "src/main/java/net/citotech/cito/billing/baas/BillingBaasChargingService.java"
replace_once(
    charging,
    """    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BillingLedgerAccountTemplateService ledgerService;

    public BillingBaasChargingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            BillingLedgerAccountTemplateService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
    }
""",
    """    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BillingLedgerAccountTemplateService ledgerService;
    private final BillingBaasProtectedActionService protectedActionService;

    public BillingBaasChargingService(
            NamedParameterJdbcTemplate jdbcTemplate,
            BillingLedgerAccountTemplateService ledgerService,
            BillingBaasProtectedActionService protectedActionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
        this.protectedActionService = protectedActionService;
    }
""",
)
replace_once(
    charging,
    """            if (replay.billingAccountId() != account.id()
                    || replay.authorizedNetAmount().compareTo(net) != 0
                    || !replay.serviceCode().equals(service)
                    || replay.usageQuantity().compareTo(quantity) != 0) {
""",
    """            if (replay.billingAccountId() != account.id()
                    || replay.authorizedNetAmount().compareTo(net) != 0
                    || !replay.serviceCode().equals(service)
                    || replay.usageQuantity().compareTo(quantity) != 0
                    || !sameNullable(replay.entitlementCode(), blankToNull(entitlementCode))) {
""",
)
replace_once(
    charging,
    """    @Transactional
    public CommitView commit(BillingBaasContext context, String reservationReference) {
""",
    """    @Transactional(noRollbackFor = ChargingReservationExpiredException.class)
    public CommitView commit(BillingBaasContext context, String reservationReference) {
""",
)
replace_once(
    charging,
    """            expireAuthorizedReservation(context, reservation);
            throw new PaymentGatewayException(\"Charging reservation has expired\");
""",
    """            expireAuthorizedReservation(context, reservation);
            throw new ChargingReservationExpiredException(\"Charging reservation has expired\");
""",
)
replace_once(
    charging,
    """        requireApprovedProtectedAction(context.billingTenantId(), reservation.reservationReference());
""",
    """        protectedActionService.consumeApproved(
                context,
                \"CHARGE_REVERSE\",
                \"CHARGE_RESERVATION\",
                reservation.reservationReference());
""",
)
replace_once(
    charging,
    """    private void requireApprovedProtectedAction(long tenantId, String reservationReference) {
        Integer count =
                jdbcTemplate.queryForObject(
                        \"SELECT COUNT(*) FROM billing_protected_action_requests \"
                                + \"WHERE billing_tenant_id=:tenant AND action_type='CHARGE_REVERSE' \"
                                + \"AND resource_type='CHARGE_RESERVATION' AND resource_reference=:reference \"
                                + \"AND status='APPROVED'\",
                        new MapSqlParameterSource()
                                .addValue(\"tenant\", tenantId)
                                .addValue(\"reference\", reservationReference),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException(
                    \"Charging reversal requires an approved protected-action request\");
        }
    }

""",
    "",
)
replace_once(
    charging,
    """    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

""",
    """    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean sameNullable(String left, String right) {
        if (left == null || left.isBlank()) {
            return right == null || right.isBlank();
        }
        return left.equals(right);
    }

""",
)
replace_once(
    charging,
    """    private record BillingAccount(long id, long customerId, String currency, BigDecimal creditLimit) {}
""",
    """    static final class ChargingReservationExpiredException extends PaymentGatewayException {
        private static final long serialVersionUID = 1L;

        ChargingReservationExpiredException(String message) {
            super(message);
        }
    }

    private record BillingAccount(long id, long customerId, String currency, BigDecimal creditLimit) {}
""",
)

migration = ROOT / "src/main/resources/db/migration/V102__billing_baas_p0_hardening.sql"
if migration.exists():
    raise SystemExit(f"Migration already exists: {migration}")
migration.write_text(
    """-- Close BaaS P0 maker-checker evidence and one-time protected-action gaps.
ALTER TABLE `billing_baas_tenant_profiles`
  ADD COLUMN `activated_by` VARCHAR(191) NULL AFTER `approved_at`,
  ADD COLUMN `activated_at` TIMESTAMP NULL AFTER `activated_by`;

ALTER TABLE `billing_protected_action_requests`
  ADD COLUMN `consumed_by` VARCHAR(191) NULL AFTER `decision_reason`,
  ADD COLUMN `consumed_at` TIMESTAMP NULL AFTER `consumed_by`;

ALTER TABLE `billing_protected_action_requests`
  DROP CHECK `chk_billing_protected_action_status`,
  ADD CONSTRAINT `chk_billing_protected_action_status`
    CHECK (`status` IN ('PENDING','APPROVED','REJECTED','EXPIRED','CONSUMED'));
"""
)

print("Applied deterministic CPay BaaS P0 hardening patch")
