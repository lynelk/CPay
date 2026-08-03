# God-Class Decomposition Plan (Funded Track)

## Status

Plan only. This is the funded structural track from the cost/speed audit
(`project_info__4.md` / `project_info__5.md`). It changes money-moving code paths, so it
must be executed carefully, in the sequence below, with the existing verification gates.

## Why

Two long-standing classes concentrate most of the blast radius:

| Class | Approx. size | Risk |
| --- | --- | --- |
| `TransactionsLogController` | 6,000+ lines | Every admin/merchant transaction view, resolution, statement, and reconcile action routed through one controller |
| `Common.java` | 2,900+ lines | Static helpers used by every legacy call site; central `getSettings` / `getMerchant*` / `doPayIn` / `doPayOut` |

Rewriting either directly is high-risk for low short-term value. The correct move is
extract-and-delegate in small, behavior-preserving slices.

## Recommended sequence

1. **Extract pure helpers from `Common` into a `LegacyCommonSupport` component** (no DB
   access): string/parse/build utilities. Add unit tests mirroring the current behavior,
   then delegate. Zero behavior change.
2. **Extract `doPayIn` / `doPayOut` orchestration into a `LegacyMoneyMovementService`**
   that keeps the same signature semantics and calls the existing providers/ledger. This
   is the highest-risk slice; run the full v1 compatibility tests after.
3. **Extract transaction listing/filtering from `TransactionsLogController`** into a
   `TransactionQueryService` (read-only). Start with the GET endpoints, then the POST
   list endpoints. Behaviour must be identical.
4. **Extract resolution/reconciliation actions** into a `TransactionResolutionService`
   after the query extraction has proven the pattern.
5. **Retire**: once no controller references the original methods, delete the duplicated
   legacy code and re-run `mvn verify` + the migration-uniqueness/OpenAPI CI gates.

## Guard rails

- Each slice lands as its own PR with its own tests.
- No SQL change without a Flyway migration in the same PR.
- `mvn verify` (tests + Spotless) must pass on every PR.
- v1 compatibility contract (`Docs/Api-v1-contract.md`) must not change: same endpoints,
  same request fields, same response envelope shapes.
- After step 2, run the v1 money-path tests (payin/payout) explicitly before merging.

## Metrics of success

- `Common.java` shrinks to < 600 lines of glue.
- `TransactionsLogController` shrinks to routing + request mapping only.
- No behavioral diff in v1 responses (verified by existing compatibility tests).
- New services have unit coverage for the extracted behavior.

## Out of scope

- v1 ledger unification/idempotency is already implemented (Audit D1/A8/B1) in
  `Api.java`; no further change needed there.
- Refactor of the legacy `Model` gateways (e.g. `AirtelMoneyPaymentGateway`) is separate;
  the adapter architecture already provides the modern replacement path.
