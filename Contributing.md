# Contributing to Cito

Cito moves and accounts for money. Contributions therefore need stronger evidence than “the endpoint returned 200.” This guide defines the minimum engineering standard for changes to the repository.

The project was previously named CPay. Keep compatibility identifiers such as `CPAY_*`, legacy API routes and database names stable unless the change includes an explicit migration plan.

## Repository areas

| Area | Purpose |
|---|---|
| `InitializrSpringbootProjectFresh/` | Java 21 / Spring Boot backend, migrations and tests |
| `clientside/` | React/Vite admin and merchant portal |
| `Integrations/Citoconnect/` | Integration/reference assets |
| `Docs/` | Architecture, API, finance, security, readiness and runbooks |
| `Sdk/` | Signing/SDK/OpenAPI assets |

## Pull requests

Use a focused branch and PR. A PR should explain what changed, why, affected financial/security/provider surfaces, tests performed, migration/deployment impact and any remaining manual certification.

Do not bundle generated junk, local build output, editor files, credentials or unrelated formatting into a functional PR.

## Financial code rules

Read `Docs/Financial-correctness-and-data-integrity.md` before changing money paths.

Required principles:

- use `BigDecimal` and the canonical `MoneyAmount` four-decimal HALF_UP policy for authoritative money;
- treat two-decimal formatting as presentation only;
- do not introduce new `double`/`Double` financial arithmetic;
- preserve DR = CR independently per currency;
- correct ledger history through reversals/new entries, never mutation;
- make financial idempotency validate commercial attributes, not just reference strings;
- leave ambiguous reconciliation rows unmatched;
- do not weaken maker-checker controls;
- preserve effective-dated pricing/tax/FX evidence;
- reject unsupported calculations rather than silently substituting another formula.

Legacy Double signatures may remain at compatibility boundaries. Convert to `BigDecimal` immediately and keep all internal arithmetic precise.

## Backend style

New payment/provider work should prefer the adapter/service architecture instead of adding more provider conditionals to legacy root classes. Preserve `/api/v1/**` compatibility unless the change has a documented migration.

Never return raw provider response bodies or internal exception messages to merchants. Keep sensitive detail in internal evidence/logging and translate external failures through the platform error model.

Use shared export, upload-validation, idempotency, audit, encryption and security services instead of creating one-off substitutes.

## Database changes

Use a new Flyway migration under:

```text
InitializrSpringbootProjectFresh/src/main/resources/db/migration
```

Rules:

- never edit an already-applied migration;
- prefer additive changes;
- add database constraints for financial invariants where practical;
- document backfill/cutover requirements;
- test the new schema with the application;
- update root/relevant docs and migration-head references.

The migration head for the current financial-correctness work is V110.

## Testing

Backend:

```bash
cd InitializrSpringbootProjectFresh
mvn test
mvn verify
```

Run Docker-tagged integration tests when relevant and Docker is available:

```bash
mvn test -Ddocker.tests.excludedGroups=
```

Frontend:

```bash
cd clientside
npm install
npm run typecheck
npm test
npm run build
```

Every defect fix should add a regression test that would have failed before the fix. Financial tests should verify conservation/equality outcomes rather than merely checking that no exception occurred.

## Formatting and cleanup

`mvn verify` enforces Spotless on changed Java files. Remove unused imports, stale comments, duplicate helpers and dead code only when usage is proven. Do not delete a compatibility API merely because its name looks old.

When consolidating duplicated logic, keep one canonical implementation. The obsolete root-package `Amount` helper was removed after repository-wide reference checks showed no callers; `MoneyAmount` is now the sole application precision helper.

## Security-sensitive changes

Treat authentication, authorization, request/callback signing, nonce/idempotency storage, merchant/provider credentials, encryption keys, payout approvals, finance close, reconciliation, webhooks, sessions and audit records as sensitive.

Never commit secrets. Production credentials belong in the deployment platform's secret store.

## Documentation

Behavioral changes must update the relevant documentation in the same PR. At minimum keep `Readme.md`, `Installation.md`, `Deployment.md`, `CI_CD_SETUP.md` and the affected `Docs/` material consistent.

Do not describe an unfinished provider/regulator/platform integration as certified. In particular, repository code, a successful build and production certification are three separate states, because software apparently needed all three opportunities to disappoint us.

## Review checklist

Before merge confirm:

- tests and relevant CI gates are green;
- new/changed financial invariants have regression coverage;
- migration numbers are unique and safe;
- no secrets or unrelated junk are present;
- documentation matches the code after the change;
- API compatibility impact is explicit;
- deployment/runtime verification requirements are stated;
- unresolved provider, regulatory or HA certification is not hidden behind optimistic wording.
