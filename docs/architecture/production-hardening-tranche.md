# Production Hardening Tranche

This tranche addresses the outstanding recommendations after PR #15.

## Implemented

### Authoritative normalized balances

- Adds `balance_ledger_events` for normalized balance event history.
- Adds `normalized_balance_backfill_runs` to track migration/backfill runs.
- Adds `AuthoritativeBalanceService` for legacy balance backfill and normalized balance updates.
- Adds `/api/v2/admin/balances/sync-legacy` for controlled backfill from legacy balances.

### Callback security and deduplication

- Adds callback delivery signatures.
- Adds HMAC-SHA256 callback signing with timestamp and nonce headers.
- Adds callback enqueue deduplication before creating queue tasks.

### Reconciliation finance operations

- Adds settlement batch records.
- Adds validation runs for provider statements.
- Adds services and endpoints for statement validation and settlement batch operations.

### Role, permission, and audit controls

- Adds `admin_permissions` and `admin_audit_events`.
- Adds services for permission enforcement and audit recording.
- Adds seed-defaults endpoint for baseline admin permissions.

### Provider statement validation

- Adds validation run persistence for provider statement parser outputs.
- Checks required reference, amount, currency fields and duplicate provider rows.

## Still requires validation

- Backend Maven build and tests.
- Frontend build after additional UI wiring.
- Staging database migration trial.
- Provider-specific sample statement files.
- Sandbox callback delivery against real merchant callback receivers.

## Rollout guidance

1. Run migrations in staging.
2. Run `/api/v2/admin/permissions/seed-defaults`.
3. Run `/api/v2/admin/balances/sync-legacy` and compare totals with legacy balances.
4. Validate provider statement samples before import.
5. Enable signed callbacks for sandbox merchants before production merchants.
