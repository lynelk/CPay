package net.citotech.cito.billing.baas;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Explicit release invariants for money-bearing Cito billing paths. */
class BillingFinancialInvariantReleaseGateTest {

    private String source(String relative) throws Exception {
        return Files.readString(Path.of(relative), StandardCharsets.UTF_8);
    }

    @Test
    void doubleEntryPostingMustBalancePerCurrencyAndCorrectionsMustReverse() throws Exception {
        String ledger =
                source("src/main/java/net/citotech/cito/ledger/DoubleEntryLedgerService.java");

        assertThat(ledger)
                .contains("debits.compareTo(total.getValue().credits) != 0")
                .contains("Ledger transaction is not balanced")
                .contains("public long reverse(")
                .contains("String flipped")
                .contains("return post(")
                .contains("checkPeriodsNotLocked");
    }

    @Test
    void onlineChargingCannotOverspendOrDoubleCommit() throws Exception {
        String charging =
                source(
                        "src/main/java/net/citotech/cito/billing/baas/BillingBaasChargingService.java");

        assertThat(charging)
                .contains("subtract(chargingAccount.reservedAmount())")
                .contains("Insufficient BaaS charging balance or credit headroom")
                .contains("account.creditUsed().add(credit).compareTo(account.creditLimit()) > 0")
                .contains("WHERE reservation_reference=:reservation AND billing_tenant_id=:tenant")
                .contains("AND status='AUTHORIZED'")
                .contains("DuplicateKeyException");
    }

    @Test
    void ratedChargeAndInvoiceStagingRemainIdempotent() throws Exception {
        String ratedChargeMigration =
                source("src/main/resources/db/migration/V44__billing_rated_charges.sql");
        String invoiceMigration =
                source("src/main/resources/db/migration/V47__billing_invoices_core.sql");

        assertThat(ratedChargeMigration)
                .contains("UNIQUE KEY `uk_billing_rated_charge_idem` (`idempotency_key`)");
        assertThat(invoiceMigration)
                .contains("UNIQUE KEY `uk_billing_invoice_line_charge` (`billing_rated_charge_id`)")
                .contains("FINALIZED (immutable");
    }

    @Test
    void completenessAndTaxConfigurationFailClosed() throws Exception {
        String controls =
                source("src/main/resources/db/migration/V99__billing_p0_commercial_controls.sql");

        assertThat(controls)
                .contains("No jurisdiction-specific tax rate is seeded")
                .contains("status` VARCHAR(20) NOT NULL DEFAULT 'PENDING'")
                .contains("'CPAY_PAYMENT', 'PAYMENT', CURRENT_TIMESTAMP, 'PENDING'");
    }
}
