# Schema Management

Flyway migrations are the canonical schema source for CPay.

## Source of truth

| Path | Purpose |
|---|---|
| `InitializrSpringbootProjectFresh/src/main/resources/db/migration` | Versioned Flyway migrations applied at backend startup |
| `Docs/Schema/snapshots` | No-data schema snapshots generated from a migrated database for review |
| `InitializrSpringbootProjectFresh/src/main/resources/dbchanges` | Legacy XML DB-change location, disabled by default |

The legacy XML runner is gated by `CPAY_LEGACY_DBCHANGES_ENABLED=false` by default. New schema work should use Flyway.

## Current snapshot

Flyway is currently at `V44` (through `V44__billing_rated_charges.sql`).
`Docs/Schema/snapshots/2026-07-28-cpayadmin.sql` is the latest committed snapshot, but it predates
later migrations (`V19` through `V44`). Unlike the July 16 snapshot below, no live migrated
database was available when it was written, so it was hand-reconstructed by reading the migration
DDL rather than produced with `mysqldump`. Treat it as a review aid, not the release baseline.
Regenerate a real no-data snapshot against a freshly migrated database before tagging a release so
the snapshot catches drift between authored migrations and applied schema.

`Docs/Schema/snapshots/2026-07-16-cpayadmin.sql` is the previous real `mysqldump` snapshot, generated from the local migrated `cpayadmin` database on July 16, 2026. It reflects schema state through V1, V2, and V5 only (the 19 baseline tables, plus the 24 tables added by V2, plus `feature_flags` from V5) — it predates V3's `merchant_transactions_log.currency` widening and everything from V7 onward, so it is missing 40 tables (ledger, risk, compliance, FX/treasury, payment links/checkout, webhooks, sandbox/environment controls, channel routing, fee schedules, and the payout compensation saga). Kept for history; prefer the 2026-07-28 snapshot.

Regenerate a snapshot after migrations change:

```powershell
mysqldump -h 127.0.0.1 -P 3307 -uroot --no-data --skip-comments --skip-add-locks --skip-disable-keys --skip-set-charset --skip-dump-date --compact --no-tablespaces cpayadmin --result-file=Docs/Schema/snapshots/YYYY-MM-DD-cpayadmin.sql
```

Normalize live counters before committing:

```powershell
$path = "Docs/Schema/snapshots/YYYY-MM-DD-cpayadmin.sql"
$content = Get-Content $path -Raw
$content = $content -replace " AUTO_INCREMENT=\d+", ""
Set-Content -Path $path -Value $content -NoNewline
```

## Core relationships

```text
merchants
  -> merchant_admins
       -> merchant_mfa_totp
  -> merchant_settings
  -> merchant_channel_credentials
  -> merchant_channel_balances
  -> merchant_environment_preferences
  -> merchant_webhook_endpoints
       -> merchant_webhook_deliveries
  -> merchant_notification_preferences
  -> merchant_batch_transactions_log
       -> beneficiaries
       -> merchant_transactions_log
            -> merchant_statement
            -> callback_tasks
            -> payout_compensation_sagas

reconciliation_imports
  -> reconciliation_records
       -> reconciliation_reviews

ledger_accounts
  -> ledger_entries <- ledger_transactions
  -> ledger_account_balances

risk_rules
risk_decisions
  -> risk_decision_scores
compliance_profiles
compliance_cases
  -> compliance_case_notes
compliance_watchlist_entries
  -> compliance_screening_hits

fx_rates
fx_quotes
treasury_positions
cross_border_corridors
payment_intents
transfer_intents
payment_links
  -> hosted_checkout_attempts

fee_schedules
channel_routing_prefixes
password_reset_tokens

billing_tenants
  -> billing_customers
  -> billing_accounts
billing_service_catalog
  -> billing_meters
       -> billing_meter_versions
billing_usage_events
billing_outbox
billing_price_book_versions
  -> billing_price_components
billing_rated_charges

operating_control_events
operations_alerts
admin_audit_events
audit_trail
merchants_audit_trail
```

Tables added since the original 19-table V1 baseline are grouped by the migration that introduced
them in the header comment of `Docs/Schema/snapshots/2026-07-28-cpayadmin.sql`; update that snapshot
after generating a real migrated-database dump for the next release.

## Migration rules

- Prefer additive, idempotent migrations for existing dev and staging databases.
- Avoid destructive changes without an ADR and tested rollback plan.
- Use native `TIMESTAMP` or `DATETIME` columns for new time fields.
- Add indexes with dashboard and reconciliation query patterns in mind.
- Keep release notes in `Changelog.md` when a migration changes operational behavior.
