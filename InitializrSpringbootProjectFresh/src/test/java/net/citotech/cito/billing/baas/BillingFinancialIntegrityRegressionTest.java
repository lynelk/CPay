package net.citotech.cito.billing.baas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BillingFinancialIntegrityRegressionTest {
    private String source(String path) throws Exception {
        return Files.readString(
                Path.of("src/main/java/net/citotech/cito/" + path), StandardCharsets.UTF_8);
    }

    @Test
    void chargingIsConcurrencyAndPeriodSafe() throws Exception {
        String java = source("billing/baas/BillingBaasChargingService.java");
        assertThat(java)
                .contains("COALESCE(SUM(consumed_quantity),0)")
                .contains("consumeEntitlementAtomically")
                .contains("LIMIT 1 FOR UPDATE")
                .contains("periodKey(reservation.createdAt())")
                .contains("sweepExpiredReservations")
                .contains("DuplicateKeyException");
    }

    @Test
    void settledPayinIsGloballyClaimedAndReclassifiedWithoutNewCash() throws Exception {
        String funding = source("billing/integration/cpay/BillingPaymentFundingService.java");
        String ledger = source("billing/integration/cpay/BillingLedgerAccountTemplateService.java");
        String admin = source("billing/baas/BillingBaasAdminService.java");
        assertThat(funding)
                .contains("LIMIT 2 FOR UPDATE")
                .contains("SUM(amount)")
                .contains("billing_payment_funding_allocations")
                .contains("currency does not match");
        assertThat(admin).contains("postPrepaidTopUpFromMerchantCollection");
        assertThat(ledger)
                .contains("collections_payable")
                .contains("postInvoicePaymentFromMerchantCollection");
    }

    @Test
    void invoiceAndCompletenessFinalizationFailClosed() throws Exception {
        String invoices = source("billing/invoicing/BillingInvoiceRepository.java");
        String service = source("billing/invoicing/BillingInvoiceService.java");
        String gate = source("billing/reconciliation/BillingCompletenessGateService.java");
        assertThat(invoices)
                .contains("FROM billing_invoices WHERE id = :id FOR UPDATE")
                .contains("outstanding_amount>=:amount");
        assertThat(service)
                .contains("requireFinalizedInvoiceLocked")
                .contains("fundingService.claim");
        assertThat(gate)
                .contains("currentUnstaged")
                .contains("observed_through_at IS NULL")
                .contains("observed_through_at<:period_end");
    }
}
