# ADR 0005: Billing Outbox Design

Date: 2026-08-07
Status: Accepted

## Context

The billing engine needs to capture a durable, ordered, at-least-once record of billable events
(a pay-in completed, an SMS segment delivered, a webhook fired) without depending on the calling
transaction's ultimate success being visible synchronously to a downstream consumer. No generic
outbox, event-store, or domain-event pattern exists anywhere in CPay today. The two closest things
in the codebase — `efris_receipts` and `merchant_webhook_deliveries` — are both single-purpose,
not reusable, and (in the EFRIS case) actually populated by a scheduled poll of
`merchant_transactions_log` rather than a real event hook. CPay also has no CDC (change-data-
capture) infrastructure, message broker, or streaming platform in place.

## Decision

Build a classic transactional-outbox with a polling relay, using patterns already proven in this
codebase rather than introducing new infrastructure:

- `billing_outbox` table: entry id, aggregate type/id, event type, payload, status
  (`PENDING`/`PROCESSING`/`DELIVERED`/`FAILED`), attempt count, next-attempt time.
- `OutboxWriter.write(...)` inserts into `billing_outbox` from inside the caller's existing
  `@Transactional` method — the write only survives if the caller's transaction commits, which is
  the entire point of the pattern (no separate transaction, no two-phase commit).
- `OutboxRelay` is a `@Scheduled` job guarded by ShedLock, following the exact pattern already
  used by `scheduler/LedgerOperationsScheduler` (and the ShedLock table added in `V30`) — no new
  job-locking mechanism is introduced. It polls `PENDING` entries, marks them `PROCESSING`, and
  transitions to `DELIVERED` or `FAILED` (with retry/backoff) on completion.
- No message broker is introduced in this phase. The relay's initial "publish" targets are
  in-process consumers (starting with `UsageGatewayService` for self-consumption); an external
  broker can replace the relay's delivery step later without changing `OutboxWriter`'s contract.

## Consequences

- Every billable event has a durable, replayable record independent of whether any downstream
  consumer was up at the time it was written.
- The relay must handle poison messages (a payload that always fails to process) without getting
  permanently stuck in `PROCESSING` — this is a first-class test requirement, not an edge case,
  since there is no existing pattern in this codebase to lean on for correctness here.
- Throughput is bounded by polling frequency and batch size, not by broker throughput — acceptable
  at CPay's current volume; revisit if Phase 6 load testing shows it isn't.

## Follow-ups

- Re-evaluate a real message broker if polling latency becomes a bottleneck at scale (Phase 6).
- Consider partitioning `billing_outbox` by `billing_tenant_id` if a single large tenant's volume
  starts starving smaller tenants' delivery latency.
