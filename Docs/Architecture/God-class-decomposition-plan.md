# God-Class Decomposition Plan (Funded Track)

## Status

**Implementation complete; verification pending before merge.** The funded extract-and-delegate
slices have been executed on `refactor/preferred-architecture-alignment`. The legacy public API
surface remains intact, but the highest-risk implementations no longer live in the two original
god classes.

`Common` now retains compatibility entry points while delegating statement mutations, pay-in/pay-out
execution and transaction status resolution to dedicated same-package engines. Modern v2 payment
orchestration reaches the extracted money engine through `LegacyMoneyMovementService` rather than
calling the `Common` compatibility facade.

`TransactionsLogController` now delegates the principal transaction, batch-payment, SMS and
statement read paths to `TransactionQueryService`, and statement/status mutation seams to
`TransactionResolutionService`. Session and permission checks remain at the HTTP boundary.

The remaining controller methods are legacy portal/dashboard/provider-test functionality outside the
funded transaction-query and resolution slices. They should be migrated by their owning product
modules rather than collected into another generic transaction god service.

## Why

The original design concentrated unrelated responsibilities in two long-standing classes:

| Class | Original concentration | Extracted responsibility |
| --- | --- | --- |
| `Common.java` | static utilities, settings/merchant access, statement balance mutation, pay-in/pay-out execution, transaction resolution | pure helpers, statement engine, money-movement engine, status-resolution engine |
| `TransactionsLogController` | HTTP/session handling mixed with transaction queries, statements, batch-payment views and financial commands | read-only query service plus explicit command service |

The implementation deliberately used extract-and-delegate rather than a rewrite so v1 request and
response semantics could remain stable while responsibilities moved behind narrower boundaries.

## Completed execution sequence

1. **Legacy money movement — COMPLETE.** `LegacyMoneyMovementEngine` owns the physical
   `doPayIn`/`doPayOut` implementation. `Common.doPayIn/doPayOut` remain compatibility delegates,
   while `LegacyMoneyMovementService` invokes the engine directly for modern orchestration.
2. **Pure Common helpers — COMPLETE.** `LegacyCommonSupport` owns deterministic helpers such as
   JSON text access, token generation, URL encoding and decimal helpers, with focused unit tests.
3. **Transaction listing/filtering — COMPLETE.** `TransactionQueryService` owns the principal
   admin/merchant transaction lists, merchant batch-payment history, SMS history and statement
   reads, preserving the existing JSON envelope/field names.
4. **Resolution and statement mutation — COMPLETE.** `TransactionResolutionEngine` owns transaction
   status resolution and reversal/settlement behavior. `LegacyStatementEngine` owns the legacy
   statement/balance mutation core. `TransactionResolutionService` is the application command seam.
5. **Retire legacy bodies — COMPLETE.** The extracted statement, pay-in/pay-out and status-resolution
   bodies were removed from `Common`; the public methods are now thin delegates. The temporary
   extraction generator/workflow used to make the large source move safely was removed after the
   generated source landed.

## Guard rails retained

- No v1 endpoint, request field or response envelope was intentionally changed by this track.
- No SQL/schema change was introduced by the decomposition itself.
- Raw v1 risk authorization remains covered by `CommonRiskAuthorizationTest`; no duplicate risk
  decision was added at the controller layer.
- Raw v1 payout-control parity remains covered by `ApiV1PayoutControlTest`; no duplicate approval
  gate was introduced.
- `PaymentOrchestrationService` continues to authorize risk once, then invokes the legacy execution
  engine through `LegacyMoneyMovementService` with the skip-risk compatibility flag.
- Query and command responsibilities are kept in separate services so read paths cannot casually
  acquire money-moving dependencies.

## Verification gates

Before this PR is taken out of draft:

1. `mvn test -Dspring.flyway.enabled=false` must compile and pass the backend test suite.
2. `mvn spotless:apply` / `mvn verify` must leave the extracted Java formatted and pass the
   ratcheted Spotless gate.
3. Explicit v1 pay-in/payout, idempotency, risk, payout-control, ledger and callback tests must stay
   green.
4. Frontend build/tests must stay green after the broader preferred-architecture changes.
5. Migration uniqueness, OpenAPI assets and security workflows must remain green.

A temporary branch-only Java verification workflow records the Maven result while this extraction is
being reviewed; that workflow and its diagnostic file are removed once verification is complete.

## Resulting architecture

- `LegacyCommonSupport`: side-effect-free legacy helpers.
- `LegacyMoneyMovementEngine`: physical compatibility implementation for pay-in/payout.
- `LegacyMoneyMovementService`: Spring application seam used by modern orchestration.
- `LegacyStatementEngine`: physical compatibility implementation for statement/balance mutation.
- `TransactionResolutionEngine`: physical transaction status-resolution/reversal implementation.
- `TransactionResolutionService`: Spring command seam for transaction mutations.
- `TransactionQueryService`: read-only portal/admin transaction, batch, SMS and statement queries.
- `Common`: compatibility facade plus remaining genuinely shared legacy utilities/data access.
- `TransactionsLogController`: HTTP/session boundary for the extracted transaction/query command
  surfaces, with unrelated legacy portal/test operations left for their domain-specific migrations.

## Out of scope / already addressed

- v1 ledger parity/idempotency and reserve/capture controls already exist and are preserved.
- v1 risk authorization already exists in the legacy money path.
- v1 payout controls already exist in `Api` and are tested.
- Refactoring provider model classes remains a separate payment-adapter migration track.
- Dashboard analytics, SMS purchasing and provider diagnostic endpoints are separate product/domain
  concerns; moving them into `TransactionQueryService` merely to reduce a line count would recreate
  the same god-class problem under a different filename.
