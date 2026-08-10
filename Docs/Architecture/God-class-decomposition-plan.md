# God-Class Decomposition Plan (Funded Track)

## Status

**In progress.** The first extract-and-delegate seam has landed on the preferred-architecture branch:
`payments/legacy/LegacyMoneyMovementService` now owns the compatibility boundary used by
`PaymentOrchestrationService`, so new v2 orchestration no longer calls `Common.doPayIn/doPayOut`
directly. The service deliberately delegates to the legacy implementation for now, preserving the
v1 contract while creating a stable place to move the implementation in later slices.

The remaining work still changes money-moving code paths, so it must continue in small slices with
the existing verification gates.

## Why

Two long-standing classes concentrate most of the blast radius:

| Class | Approx. size | Risk |
| --- | --- | --- |
| `TransactionsLogController` | 6,000+ lines | Every admin/merchant transaction view, resolution, statement, and reconcile action routed through one controller |
| `Common.java` | 2,900+ lines | Static helpers used by every legacy call site; central `getSettings` / `getMerchant*` / `doPayIn` / `doPayOut` |

Rewriting either directly is high-risk for low short-term value. The correct move is
extract-and-delegate in small, behavior-preserving slices.

## Execution sequence

1. **Legacy money-movement seam — STARTED.** `LegacyMoneyMovementService` is now the dependency used
   by `PaymentOrchestrationService`. Next, move the internals of `doPayIn` / `doPayOut` behind this
   service while leaving the `Common` methods as compatibility delegates for raw v1 callers.
2. **Extract pure helpers from `Common` into a `LegacyCommonSupport` component** (no DB access):
   string/parse/build utilities. Add unit tests mirroring current behavior, then leave compatibility
   delegates in `Common` until callers migrate.
3. **Extract transaction listing/filtering from `TransactionsLogController`** into a
   `TransactionQueryService` (read-only). Start with GET endpoints, then POST list endpoints.
4. **Extract resolution/reconciliation actions** into a `TransactionResolutionService` after the
   query extraction has proven the pattern.
5. **Retire legacy bodies** once all compatibility entry points delegate to services and the v1
   contract suite proves parity.

## Guard rails

- Each high-risk extraction slice should remain reviewable and independently testable.
- No SQL change without a Flyway migration in the same PR.
- `mvn verify` (tests + Spotless) must pass before merge.
- v1 compatibility contract (`Docs/Api-v1-contract.md`) must not change: same endpoints, request
  fields, response envelopes and status semantics.
- Raw v1 risk authorization is already covered by `CommonRiskAuthorizationTest`; do not add a
  second risk decision at the controller layer.
- Raw v1 payout-control parity is already covered by `ApiV1PayoutControlTest`; do not duplicate the
  approval/control gate.
- After moving the Common money bodies, run pay-in/payout, idempotency, risk, payout-control,
  ledger and callback tests explicitly.

## Metrics of success

- `Common.java` shrinks to < 600 lines of compatibility glue and shared constants.
- `TransactionsLogController` shrinks to request mapping and HTTP concerns only.
- No behavioral diff in v1 responses (verified by compatibility tests).
- New services have focused unit/integration coverage.
- New v2/domain code has no direct dependency on `Common.doPayIn/doPayOut`.

## Out of scope / already addressed

- v1 ledger parity/idempotency and reserve/capture controls already exist and should be preserved.
- v1 risk authorization already exists in the Common money path.
- v1 payout controls already exist in `Api` and are tested.
- Refactor of legacy provider model classes remains a separate adapter-migration track.
