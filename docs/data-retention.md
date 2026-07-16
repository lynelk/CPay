# Data Retention and Archival Policy

This policy keeps operational tables bounded while preserving regulated payment evidence.

## Principles

- Money movement tables are append-oriented and should not be deleted casually.
- Short-lived control tables should be cleaned automatically.
- Purges must be explicit, auditable, and reversible through backup or archive.
- Retention changes need an ADR when they affect finance, compliance, or customer evidence.

## Current retention classes

| Data area | Tables | Current handling | Target |
|---|---|---|---|
| Transaction ledger | `merchant_transactions_log`, `merchant_statement`, `balance_ledger_events` | Retained in primary DB | Partition or archive by month after finance signoff |
| Audit evidence | `audit_trail`, `merchants_audit_trail`, `admin_audit_events` | Retained in primary DB | Append-only retention with hash chaining or DB-level immutability |
| Callback work queue | `callback_tasks`, `callback_task_claims`, `callback_delivery_signatures` | Active rows retained; stale claims cleaned | Archive completed tasks after agreed retention |
| API security windows | `api_rate_limits`, `cpay_request_nonces` | `api_rate_limits` cleaned by scheduler; nonces cleaned by `JdbcNonceStore` | Keep only active/recent windows |
| Sessions | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | Managed by Spring Session schema | Periodic expired-session cleanup if DB growth requires it |
| Reconciliation | `reconciliation_*`, `settlement_batches`, `daily_close_runs` | Retained in primary DB | Archive only after close and finance approval |

## Implemented cleanup

`OperationalDataCleanupScheduler` runs every hour by default.

Environment controls:

| Variable | Default | Purpose |
|---|---:|---|
| `CPAY_CLEANUP_ENABLED` | `true` | Enables scheduled operational cleanup |
| `CPAY_API_RATE_LIMIT_RETENTION_MINUTES` | `1440` | Retains one day of API rate-limit windows |
| `CPAY_CALLBACK_CLAIM_RETENTION_HOURS` | `24` | Removes stale active callback claims left by dead workers |
| `CPAY_CLEANUP_FIXED_DELAY_MS` | `3600000` | Cleanup interval |

## Future archival work

- Add monthly partitions for `merchant_transactions_log` and high-volume statement tables after production volume is measured.
- Add immutable audit controls for audit tables before regulated launch.
- Move long-retention exports to encrypted object storage with restore tests.
- Add dashboard counters for oldest retained row per high-volume table.
