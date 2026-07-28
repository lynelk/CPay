# Schema Management

Flyway migrations are the canonical schema source for CPay.

## Source of truth

| Path | Purpose |
|---|---|
| `Initializrspringbootprojectfresh/src/main/resources/db/migration` | Versioned Flyway migrations applied at backend startup |
| `Docs/Schema/snapshots` | No-data schema snapshots generated from a migrated database for review |
| `Initializrspringbootprojectfresh/src/main/resources/dbchanges` | Legacy XML DB-change location, disabled by default |

The legacy XML runner is gated by `CPAY_LEGACY_DBCHANGES_ENABLED=false` by default. New schema work should use Flyway.

## Current snapshot

`Docs/Schema/snapshots/2026-07-16-cpayadmin.sql` was generated from the local migrated `cpayadmin` database on July 16, 2026.

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
  -> merchant_settings
  -> merchant_channel_credentials
  -> merchant_channel_balances
  -> merchant_batch_transactions_log
       -> beneficiaries
       -> merchant_transactions_log
            -> merchant_statement
            -> callback_tasks

reconciliation_imports
  -> reconciliation_records
       -> reconciliation_reviews

operating_control_events
operations_alerts
admin_audit_events
audit_trail
merchants_audit_trail
```

## Migration rules

- Prefer additive, idempotent migrations for existing dev and staging databases.
- Avoid destructive changes without an ADR and tested rollback plan.
- Use native `TIMESTAMP` or `DATETIME` columns for new time fields.
- Add indexes with dashboard and reconciliation query patterns in mind.
- Keep release notes in `Changelog.md` when a migration changes operational behavior.
