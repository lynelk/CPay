# Reliability, Operations, and Scale Runbook

This runbook captures the operational changes needed before horizontal scale and production growth.

## Current Improvements

- Graceful shutdown is enabled by default.
- Transaction timeout scan interval and timeout minutes are configurable.
- Legacy XML schema updates are disabled by default.
- Nonce storage defaults to JDBC.
- Cleanup jobs remove stale rate-limit and callback-claim rows.
- A local Docker Compose stack is available for MySQL plus backend onboarding.

## Scale Requirements

| Area | Target |
|---|---|
| Locks | Replace file locks with DB or Redis locks. |
| Tokens | Move provider tokens from local files to DB or Redis with TTL and encryption. |
| Scheduled jobs | Use `@Scheduled` plus DB locks so only one node runs each job. |
| Async work | Use managed executors or durable queues instead of raw `new Thread`. |
| Backups | Document RPO/RTO, backup cadence, restore drill, and restore owner. |
| Profiles | Production profile must reject sandbox and SSL-bypass settings. |

## Backup Cadence

- Daily logical backup for operational databases.
- Weekly restore drill in non-production.
- Retain restore evidence with the release or operations record.
- Confirm provider token and merchant key recovery separately from transaction data.

## Shutdown Procedure

1. Stop accepting new traffic at the load balancer.
2. Allow the configured graceful shutdown window to drain requests.
3. Confirm callback and scheduled workers are either complete or safely claimable by another node.
4. Restart one instance at a time.
