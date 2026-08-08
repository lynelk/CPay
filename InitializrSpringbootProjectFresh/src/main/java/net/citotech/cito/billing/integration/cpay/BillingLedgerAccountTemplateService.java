package net.citotech.cito.billing.integration.cpay;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place in the billing module allowed to call {@code DoubleEntryLedgerService.post()}
 * directly (ADR 0004 / Architectural Decision #1) - every other billing service that needs a ledger
 * effect goes through here, so account-code conventions stay centralized and reviewable in one file
 * instead of scattered string-building across the module.
 *
 * <p>Implements the accounting patterns from the billing spec as pre-built, always-balanced {@link
 * LedgerEntryCommand} lists. "Tax" is not its own method: a standalone tax entry can never balance
 * on its own, so it is folded into {@link #postCustomerCharge} as an optional additional line,
 * matching how a real invoice bills tax alongside the underlying charge.
 *
 * <p>Every posting uses the new {@code billing:{tenantId}:{ccy}:{purpose}} account-code namespace
 * (owner_type {@code BILLING_TENANT}, owner_id the billing tenant id) alongside the existing {@code
 * provider:{gatewayId}:{ccy}:*}/{@code cpay:{ccy}:*} conventions already used by {@code
 * PaymentOrchestrationService.postLedgerEntries}, and immediately records a {@code
 * billing_ledger_links} row after every post via {@link BillingLedgerLinkWriter} so the ledger
 * transaction can be traced back to its billing artifact.
 */
@Service
public class BillingLedgerAccountTemplateService {
    private static final String TENANT_OWNER_TYPE = "BILLING_TENANT";

    private final DoubleEntryLedgerService ledgerService;
    private final BillingLedgerLinkWriter linkWriter;

    public BillingLedgerAccountTemplateService(
            DoubleEntryLedgerService ledgerService, BillingLedgerLinkWriter linkWriter) {
        this.ledgerService = ledgerService;
        this.linkWriter = linkWriter;
    }

    /**
     * Postpaid customer charge: debits the tenant's receivable for the full amount (charge + tax)
     * and credits billing revenue and, if {@code taxAmount} is positive, tax payable.
     */
    @Transactional
    public long postCustomerCharge(
            long billingTenantId,
            String currency,
            BigDecimal chargeAmount,
            BigDecimal taxAmount,
            String chargeReference,
            String memo) {
        BigDecimal tax = taxAmount == null ? BigDecimal.ZERO : taxAmount;
        BigDecimal total = chargeAmount.add(tax);
        List<LedgerEntryCommand> entries = new ArrayList<>();
        entries.add(
                entry(
                        arAccount(billingTenantId, currency),
                        "Billing accounts receivable",
                        "RECEIVABLE",
                        billingTenantId,
                        "DR",
                        total,
                        currency,
                        memo));
        entries.add(
                entry(
                        billingRevenueAccount(currency),
                        "CPay billing revenue",
                        "REVENUE",
                        null,
                        "CR",
                        chargeAmount,
                        currency,
                        memo));
        if (tax.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(
                    entry(
                            taxPayableAccount(billingTenantId, currency),
                            "Billing tax payable",
                            "TAX_LIABILITY",
                            billingTenantId,
                            "CR",
                            tax,
                            currency,
                            memo));
        }
        return postAndLink(
                "billing-charge:" + chargeReference,
                "BILLING_CHARGE",
                chargeReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.CHARGE,
                chargeReference);
    }

    /** Provider cost accrual: recognizes an owed-but-unpaid provider cost as an expense. */
    @Transactional
    public long postProviderCostAccrual(
            long billingTenantId,
            String currency,
            BigDecimal costAmount,
            String providerId,
            String costReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                providerCostExpenseAccount(currency),
                                "CPay provider cost expense",
                                "EXPENSE",
                                null,
                                "DR",
                                costAmount,
                                currency,
                                memo),
                        entry(
                                providerAccruedPayableAccount(providerId, currency),
                                "Provider accrued payable",
                                "PROVIDER_LIABILITY",
                                null,
                                "CR",
                                costAmount,
                                currency,
                                memo));
        return postAndLink(
                "billing-cost:" + costReference,
                "BILLING_COST",
                costReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.COST,
                costReference);
    }

    /** Prepaid top-up: the tenant pays CPay cash/settlement and receives stored-value credit. */
    @Transactional
    public long postPrepaidTopUp(
            long billingTenantId,
            String currency,
            BigDecimal amount,
            String paymentReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                settlementCashAccount(currency),
                                "CPay settlement cash",
                                "ASSET",
                                null,
                                "DR",
                                amount,
                                currency,
                                memo),
                        entry(
                                storedValueLiabilityAccount(billingTenantId, currency),
                                "Billing stored-value liability",
                                "STORED_VALUE_LIABILITY",
                                billingTenantId,
                                "CR",
                                amount,
                                currency,
                                memo));
        return postAndLink(
                "billing-topup:" + paymentReference,
                "BILLING_TOPUP",
                paymentReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.PAYMENT,
                paymentReference);
    }

    /** Prepaid consumption: draws down stored value as the tenant uses metered services. */
    @Transactional
    public long postPrepaidConsumption(
            long billingTenantId,
            String currency,
            BigDecimal amount,
            String usageReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                storedValueLiabilityAccount(billingTenantId, currency),
                                "Billing stored-value liability",
                                "STORED_VALUE_LIABILITY",
                                billingTenantId,
                                "DR",
                                amount,
                                currency,
                                memo),
                        entry(
                                billingRevenueAccount(currency),
                                "CPay billing revenue",
                                "REVENUE",
                                null,
                                "CR",
                                amount,
                                currency,
                                memo));
        return postAndLink(
                "billing-consumption:" + usageReference,
                "BILLING_CONSUMPTION",
                usageReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.CHARGE,
                usageReference);
    }

    /** Invoice payment: clears the tenant's receivable as cash/settlement is received. */
    @Transactional
    public long postInvoicePayment(
            long billingTenantId,
            String currency,
            BigDecimal amount,
            String invoiceReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                settlementCashAccount(currency),
                                "CPay settlement cash",
                                "ASSET",
                                null,
                                "DR",
                                amount,
                                currency,
                                memo),
                        entry(
                                arAccount(billingTenantId, currency),
                                "Billing accounts receivable",
                                "RECEIVABLE",
                                billingTenantId,
                                "CR",
                                amount,
                                currency,
                                memo));
        return postAndLink(
                "billing-invoice-payment:" + invoiceReference,
                "BILLING_INVOICE",
                invoiceReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.INVOICE,
                invoiceReference);
    }

    /** Credit note / refund: reduces billing revenue and the tenant's outstanding receivable. */
    @Transactional
    public long postCreditNote(
            long billingTenantId,
            String currency,
            BigDecimal amount,
            String creditReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                billingRevenueAccount(currency),
                                "CPay billing revenue",
                                "REVENUE",
                                null,
                                "DR",
                                amount,
                                currency,
                                memo),
                        entry(
                                arAccount(billingTenantId, currency),
                                "Billing accounts receivable",
                                "RECEIVABLE",
                                billingTenantId,
                                "CR",
                                amount,
                                currency,
                                memo));
        return postAndLink(
                "billing-credit:" + creditReference,
                "BILLING_CREDIT",
                creditReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.REVERSAL,
                creditReference);
    }

    /** BaaS platform fee revenue, kept segregated from a tenant's own merchant revenue. */
    @Transactional
    public long postBaasPlatformFee(
            long billingTenantId,
            String currency,
            BigDecimal amount,
            String feeReference,
            String memo) {
        List<LedgerEntryCommand> entries =
                List.of(
                        entry(
                                arAccount(billingTenantId, currency),
                                "Billing accounts receivable",
                                "RECEIVABLE",
                                billingTenantId,
                                "DR",
                                amount,
                                currency,
                                memo),
                        entry(
                                baasFeeRevenueAccount(currency),
                                "CPay BaaS platform fee revenue",
                                "REVENUE",
                                null,
                                "CR",
                                amount,
                                currency,
                                memo));
        return postAndLink(
                "billing-baas-fee:" + feeReference,
                "BILLING_BAAS_FEE",
                feeReference,
                memo,
                entries,
                billingTenantId,
                BillingLedgerLinkType.CHARGE,
                feeReference);
    }

    private long postAndLink(
            String transactionReference,
            String sourceType,
            String sourceReference,
            String description,
            List<LedgerEntryCommand> entries,
            long billingTenantId,
            BillingLedgerLinkType linkType,
            String billingReference) {
        long txId =
                ledgerService.post(
                        transactionReference, sourceType, sourceReference, description, entries);
        linkWriter.write(txId, billingTenantId, linkType, billingReference);
        return txId;
    }

    private LedgerEntryCommand entry(
            String accountCode,
            String accountName,
            String accountType,
            Long ownerId,
            String direction,
            BigDecimal amount,
            String currency,
            String memo) {
        return new LedgerEntryCommand(
                accountCode,
                accountName,
                accountType,
                ownerId == null ? "SYSTEM" : TENANT_OWNER_TYPE,
                ownerId,
                direction,
                amount,
                currency,
                memo);
    }

    private String arAccount(long billingTenantId, String currency) {
        return "billing:" + billingTenantId + ":" + currency + ":ar";
    }

    private String taxPayableAccount(long billingTenantId, String currency) {
        return "billing:" + billingTenantId + ":" + currency + ":tax_payable";
    }

    private String storedValueLiabilityAccount(long billingTenantId, String currency) {
        return "billing:" + billingTenantId + ":" + currency + ":stored_value_liability";
    }

    private String billingRevenueAccount(String currency) {
        return "cpay:" + currency + ":billing_revenue";
    }

    private String baasFeeRevenueAccount(String currency) {
        return "cpay:" + currency + ":baas_platform_fee_revenue";
    }

    private String providerCostExpenseAccount(String currency) {
        return "cpay:" + currency + ":provider_cost_expense";
    }

    private String providerAccruedPayableAccount(String providerId, String currency) {
        return "provider:" + providerId + ":" + currency + ":accrued_payable";
    }

    private String settlementCashAccount(String currency) {
        return "cpay:" + currency + ":settlement_cash";
    }
}
