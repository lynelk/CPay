# Data Retention and Archival Policy

This policy keeps operational tables bounded while preserving regulated payment evidence.

This doc was last checked against code on 2026-07-28 by reading
`InitializrSpringbootProjectFresh/src/main/java/net/citotech/cito/scheduler/OperationalDataCleanupScheduler.java`
and the `cpay.cleanup.*` / `cpay.security.session-absolute-max-hours` keys in `application.properties`
directly, so the "Current handling" and "Implemented cleanup" sections below describe what the code
actually does today, not aspirational behavior. The "NOT YET ENFORCED" table further down exists so a
future engineer doesn't have to re-derive this from source — treat any category listed there as still
open.

## Principles

- Money movement tables are append-oriented and should not be deleted casually.
- Short-lived control tables should be cleaned automatically.
- Purges must be explicit, auditable, and reversible through backup or archive.
- Retention changes need an ADR when they affect finance, compliance, or customer evidence.

## Current retention classes

| Data area | Tables | Current handling | Target |
|---|---|---|---|
| Transaction ledger | `merchant_transactions_log`, `merchant_statement`, `balance_ledger_events` | Retained in primary DB, no purge | Partition or archive by month after finance signoff |
| Audit evidence | `audit_trail`, `merchants_audit_trail`, `admin_audit_events` | Retained in primary DB, no purge | Append-only retention with hash chaining or DB-level immutability |
| Callback work queue | `callback_tasks`, `callback_task_claims`, `callback_delivery_signatures` | Only stale `ACTIVE` rows in `callback_task_claims` are cleaned; `callback_tasks` and `callback_delivery_signatures` are never purged | Archive completed tasks after agreed retention |
| Password reset tokens | `password_reset_tokens` | Cleaned by scheduler on a fixed age, regardless of whether the token was consumed | Keep only active/recent windows |
| Merchant webhook deliveries | `merchant_webhook_deliveries` | Cleaned by scheduler once terminal (`DELIVERED` or `FAILED`) and past retention; rows still `PENDING`/`RETRYING` are never purged by this job | Keep only active/recent windows |
| API security windows | `api_rate_limits`, `cpay_request_nonces` | `api_rate_limits` cleaned by scheduler; nonces cleaned separately by `JdbcNonceStore` (not this scheduler; not re-verified as part of this pass) | Keep only active/recent windows |
| Sessions | `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES` | Scheduler force-expires sessions past an absolute lifetime (by `CREATION_TIME`, independent of activity); ordinary inactivity-based expiry (`EXPIRY_TIME`) is handled separately by Spring Session's own JDBC cleanup, not this scheduler | Done for the absolute-cap case; revisit if DB growth still requires more |
| Reconciliation | `reconciliation_*`, `settlement_batches`, `daily_close_runs` | Retained in primary DB, no purge | Archive only after close and finance approval |

## Implemented cleanup

`OperationalDataCleanupScheduler` runs on a fixed delay (default every hour) and, when enabled, performs
five independent purges/expirations in one pass:

1. Deletes `api_rate_limits` rows older than the configured retention window.
2. Deletes `callback_task_claims` rows where `claim_status='ACTIVE'` and older than the configured
   retention window (cleans up claims abandoned by dead workers; does not touch `callback_tasks` itself).
3. Deletes `password_reset_tokens` rows older than the configured retention window, regardless of
   whether `consumed_at` is set (an unconsumed and a used token are purged the same way once they age out).
4. Deletes `merchant_webhook_deliveries` rows where `delivery_status` is `DELIVERED` or `FAILED` and older
   than the configured retention window. Deliveries still `PENDING` or in retry are left alone no matter
   how old they are.
5. Force-deletes `SPRING_SESSION` rows whose `CREATION_TIME` is older than the configured absolute
   session lifetime, regardless of recent activity (cascades to `SPRING_SESSION_ATTRIBUTES` via the
   table's `ON DELETE CASCADE` foreign key). This exists because Spring Session's own expiry is
   inactivity-only (`EXPIRY_TIME`), so a session kept alive by continuous activity would otherwise never
   expire.

A failure in the scheduled run is caught and logged at `WARNING`; it does not crash the scheduler thread,
but it also does not raise an operator-visible alert on its own (see `Docs/Runbooks/Operations-alerts.md`
and `InitializrSpringbootProjectFresh/src/main/resources/monitoring/alert-rules.yml` — there is currently
no metric or alert tied to cleanup failures; this scheduler's outcome is log-only).

Environment controls (all read directly from `application.properties`; defaults shown are what ships
today):

| Variable | Default | Purpose |
|---|---:|---|
| `CPAY_CLEANUP_ENABLED` | `true` | Master on/off switch for the whole scheduled cleanup pass |
| `CPAY_API_RATE_LIMIT_RETENTION_MINUTES` | `1440` | Retains one day of API rate-limit windows |
| `CPAY_CALLBACK_CLAIM_RETENTION_HOURS` | `24` | Removes stale active callback claims left by dead workers |
| `CPAY_PASSWORD_RESET_TOKEN_RETENTION_DAYS` | `7` | Deletes password reset tokens (consumed or not) past this age |
| `CPAY_WEBHOOK_DELIVERY_RETENTION_DAYS` | `30` | Deletes terminal (`DELIVERED`/`FAILED`) webhook delivery rows past this age |
| `CPAY_SESSION_ABSOLUTE_MAX_HOURS` | `12` | Absolute session lifetime cap regardless of activity |
| `CPAY_CLEANUP_FIXED_DELAY_MS` | `3600000` | Delay between cleanup runs, in milliseconds (not declared in `application.properties`; resolved directly from the environment via Spring's relaxed binding, so setting the env var still works) |

## NOT YET ENFORCED in code

The table below lists every retention category this document has ever described (or that the scheduler
could plausibly cover) that still has **no automated purge/archival today**. Anything in this list is
fair game for a future engineer to pick up; do not assume it is handled just because it is documented.

| Data area | Tables | Why it matters | Status |
|---|---|---|---|
| Transaction ledger | `merchant_transactions_log`, `merchant_statement`, `balance_ledger_events` | Grows unbounded with transaction volume; needs partitioning/archival before it becomes a query-performance problem | Not started — deliberately, per policy above, until finance signs off |
| Audit evidence | `audit_trail`, `merchants_audit_trail`, `admin_audit_events` | Same growth concern; also needs immutability controls, not just retention | Not started |
| Callback tasks and signatures | `callback_tasks`, `callback_delivery_signatures` | Only the claims table is cleaned; completed/parked tasks and their delivery signatures accumulate indefinitely | Not started |
| Reconciliation and settlement | `reconciliation_imports`, `reconciliation_records`, `reconciliation_reviews`, `reconciliation_daily_closes`, `reconciliation_settlement_batches` | No purge of any kind; needed once volume grows past what finance needs live | Not started |
| Provider run logs | `provider_sandbox_runs`, `provider_endpoint_runs`, `provider_statement_validation_runs` | Diagnostic/audit logs for provider calls with no documented retention target at all today | Not previously documented as a retention class; flagging here for the first time |
| In-flight webhook deliveries | `merchant_webhook_deliveries` rows still `PENDING`/`RETRYING` | A delivery stuck retrying forever is never cleaned by the current job (it only touches terminal rows) | Partially enforced — terminal rows only |
| Cleanup failure visibility | N/A (operational) | The scheduler swallows exceptions into a log line with no counter/alert, so a silently-failing cleanup pass could go unnoticed indefinitely | Not started |

## Future archival work

- Add monthly partitions for `merchant_transactions_log` and high-volume statement tables after production volume is measured.
- Add immutable audit controls for audit tables before regulated launch.
- Move long-retention exports to encrypted object storage with restore tests.
- Add dashboard counters for oldest retained row per high-volume table.
- Purge or archive `callback_tasks` / `callback_delivery_signatures` once a task reaches a terminal
  status and its retention window has passed, mirroring how `merchant_webhook_deliveries` is now handled.
- Emit a metric/counter from `OperationalDataCleanupScheduler` so a failed or silently-skipped cleanup
  pass is visible to monitoring rather than log-only.
