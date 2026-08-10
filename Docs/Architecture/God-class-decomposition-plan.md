# God-Class Decomposition Plan (Funded Track)

## Status

**Implementation complete.** The funded extract-and-delegate slices have been executed on
`refactor/preferred-architecture-alignment`. The legacy public API surface remains intact, while the
highest-risk transaction implementations no longer live in the two original god classes.

`Common` now retains compatibility entry points while delegating statement mutations, pay-in/pay-out
execution and transaction status resolution to dedicated same-package engines. Modern payment
orchestration reaches the extracted money engine through `LegacyMoneyMovementService` rather than
calling the `Common` compatibility facade.

`TransactionsLogController` delegates the principal transaction, batch-payment, SMS and statement
read paths to `TransactionQueryService`, statement/status mutation seams to
`TransactionResolutionService`, and portal money execution to `LegacyMoneyMovementService`.
Session and permission checks remain at the HTTP boundary.

The remaining controller methods are unrelated legacy portal/dashboard/provider-test functionality.
They should migrate with their owning product modules instead of being accumulated into another
generic transaction service merely to reduce a line count.

## Why

The original design concentrated unrelated responsibilities in two long-standing classes:

| Class | Original concentration | Extracted responsibility |
| --- | --- | --- |
| `Common.java` | static utilities, settings/merchant access, statement balance mutation, pay-in/pay-out execution, transaction resolution | pure helpers, statement engine, money-movement engine, status-resolution engine |
| `TransactionsLogController` | HTTP/session handling mixed with transaction queries, statements, batch-payment views and financial commands | read-only query service plus explicit command/money seams |

The implementation deliberately used extract-and-delegate rather than a rewrite so v1 request and
response semantics remain stable while responsibilities move behind narrower boundaries.

## Completed execution sequence

1. **Legacy money movement — COMPLETE.** `LegacyMoneyMovementEngine` owns the physical
   `doPayIn`/`doPayOut` implementation. `Common.doPayIn/doPayOut` remain compatibility delegates,
   while `LegacyMoneyMovementService` invokes the engine directly for modern orchestration and
   portal transaction commands.
2. **Pure Common helpers — COMPLETE.** `LegacyCommonSupport` owns deterministic helpers such as
   JSON text access, token generation, URL encoding and decimal helpers, with focused unit tests.
3. **Transaction listing/filtering — COMPLETE.** `TransactionQueryService` owns the principal
   admin/merchant transaction lists, merchant batch-payment history, SMS history and statement
   reads, preserving the existing JSON envelope/field names and legacy malformed-date response.
4. **Resolution and statement mutation — COMPLETE.** `TransactionResolutionEngine` owns transaction
   status resolution and reversal/settlement behavior. `LegacyStatementEngine` owns the legacy
   statement/balance mutation core. `TransactionResolutionService` is the application command seam.
5. **Retire legacy bodies — COMPLETE.** The extracted statement, pay-in/pay-out and status-resolution
   bodies were removed from `Common`; the public methods are thin compatibility delegates. Temporary
   source-rewrite and verification workflows used during the high-risk move were removed afterwards.

## Guard rails retained

- No v1 endpoint, request field or response envelope was intentionally changed by this track.
- No SQL/schema change was introduced by the decomposition itself.
- Raw v1 risk authorization remains covered by `CommonRiskAuthorizationTest`; no duplicate risk
  decision was added at the controller layer.
- Raw v1 payout-control parity remains covered by `ApiV1PayoutControlTest`; no duplicate approval
  gate was introduced.
- `PaymentOrchestrationService` continues to authorize risk once, then invokes the legacy execution
  engine through `LegacyMoneyMovementService` with the skip-risk compatibility flag.
- Portal-originated legacy payment requests use the same service seam while preserving their legacy
  risk execution behavior.
- Query and command responsibilities remain separate so read paths cannot casually acquire
  money-moving dependencies.

## Verification record

During extraction verification the backend compiled and executed the 707-test suite. Verification
surfaced two extraction-specific compatibility defects: a manually constructed controller test lacked
the newly extracted query dependency, and malformed transaction date ranges returned the generic
`102` response instead of the legacy `101` date-format response. Both were corrected at the new
service/test boundaries.

The normal repository CI remains the authoritative merge gate for Maven `verify`, Spotless, frontend
build/tests, migration uniqueness, API-contract assets and security checks. No temporary branch-only
verification workflow or diagnostic artifact is retained in the finished implementation.

## Resulting architecture

- `LegacyCommonSupport`: side-effect-free legacy helpers.
- `LegacyMoneyMovementEngine`: physical compatibility implementation for pay-in/payout.
- `LegacyMoneyMovementService`: Spring application seam used by modern and portal orchestration.
- `LegacyStatementEngine`: physical compatibility implementation for statement/balance mutation.
- `TransactionResolutionEngine`: physical transaction status-resolution/reversal implementation.
- `TransactionResolutionService`: Spring command seam for transaction mutations.
- `TransactionQueryService`: read-only portal/admin transaction, batch, SMS and statement queries.
- `Common`: compatibility facade plus remaining genuinely shared legacy utilities/data access.
- `TransactionsLogController`: HTTP/session boundary for extracted transaction/query command surfaces,
  with unrelated legacy dashboard/test operations left for domain-specific migrations.

## Out of scope / already addressed

- v1 ledger parity/idempotency and reserve/capture controls already exist and are preserved.
- v1 risk authorization already exists in the legacy money path.
- v1 payout controls already exist in `Api` and are tested.
- Refactoring provider model classes remains a separate payment-adapter migration track.
- Dashboard analytics, SMS purchasing and provider diagnostic endpoints are separate product/domain
  concerns; moving them into `TransactionQueryService` merely to reduce a line count would recreate
  the same god-class problem under a different filename.
