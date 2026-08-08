package net.citotech.cito.billing.integration.cpay;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes one {@code billing_ledger_links} row (ADR 0004), tracing a ledger posting back to the
 * billing artifact that caused it without ever mutating {@code ledger_transactions}/{@code
 * ledger_entries} themselves. Deliberately carries no {@code @Transactional} of its own: called
 * from inside {@link BillingLedgerAccountTemplateService}'s own {@code @Transactional} methods,
 * right after {@code DoubleEntryLedgerService.post()}/{@code reverse()} already ran on the same
 * thread, so the link row only survives if the whole posting commits - mirrors {@code
 * billing.outbox.OutboxWriter}'s "joins the caller's transaction" convention.
 */
@Service
public class BillingLedgerLinkWriter {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingLedgerLinkWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(
            long ledgerTransactionId,
            long billingTenantId,
            BillingLedgerLinkType linkType,
            String billingReference) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("ledger_transaction_id", ledgerTransactionId);
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("link_type", linkType.name());
        p.addValue("billing_reference", billingReference);
        jdbcTemplate.update(
                "INSERT INTO billing_ledger_links "
                        + "(ledger_transaction_id, billing_tenant_id, link_type, billing_reference) "
                        + "VALUES (:ledger_transaction_id, :billing_tenant_id, :link_type, :billing_reference)",
                p);
    }
}
