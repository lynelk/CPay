# Cito Repository Guidance

This file is a concise engineering guide for coding agents and contributors working in this repository. The product/repository is **Cito**, previously CPay. Compatibility identifiers such as `CPAY_*`, `/api/v1/**` and some legacy class/database names remain intentionally stable.

## Active application

```text
InitializrSpringbootProjectFresh/   Spring Boot 4.1 / Java 21 backend
clientside/                         React 18 / Vite 8 frontend
Integrations/Citoconnect/           Integration/reference assets
Docs/                               Architecture, finance, API, security and runbooks
Sdk/                                Signing/SDK/OpenAPI assets
```

Do not treat the older non-Fresh Spring project scaffold as the active backend.

## Build commands

Backend:

```bash
cd InitializrSpringbootProjectFresh
mvn clean package
mvn test
mvn verify
```

Docker-tagged integration tests:

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

## Architecture boundaries

- Legacy root-package payment code and `/api/v1/**` exist for compatibility. Avoid expanding this architecture; preserve behavior unless a migration is explicit.
- New/v2 payment work belongs in the adapter/service architecture under `gateway/` and `api/v2/`.
- `billing/` owns metering, rating, effective-dated pricing, tax, FX, charging, BaaS, invoicing and billing traceability.
- `ledger/` owns immutable double-entry accounting and reversals.
- `reconciliation/` owns provider statement parsing, deterministic matching, exceptions and settlement workflows.
- `callback/` and `webhook/` own asynchronous delivery/retry behavior and must remain claim/idempotency safe under multiple replicas.
- `security/` owns signing, nonce/replay controls, authentication/MFA and related safeguards.
- `treasury/`, `finance/`, `reporting/`, `compliance/` and provider-specific modules must not invent parallel monetary truth outside the ledger/billing/reconciliation controls.

## Financial correctness: mandatory rules

Read `Docs/Financial-correctness-and-data-integrity.md` before editing money paths.

1. Authoritative money uses `BigDecimal`, four decimal places, `HALF_UP`.
2. `MoneyAmount` is the canonical precision helper. Do not add another independent amount/rounding utility.
3. Two-decimal rounding is presentation only.
4. Legacy `Double` values are compatibility boundaries. Convert to `BigDecimal` before arithmetic.
5. Every ledger transaction balances DR = CR per currency.
6. Ledger corrections are append-only reversals/new postings; never mutate posted entries.
7. Reused financial references must preserve the same commercial attributes.
8. Settlement provider/channel/currency/amount are immutable once a batch is opened.
9. Automatic reconciliation requires reference + amount + currency + eligible final status + exactly one candidate. A reference alone is never enough.
10. Legacy fee schedules currently support `FLAT_FEE` and `PERCENTAGE`; `TIER` fails closed until genuine bands are implemented there. The separate billing rating engine may have its own real tier implementation; do not conflate the two.
11. Tax and FX are effective-dated and their source evidence is snapshotted.
12. Credit-note gross/revenue/tax allocations must conserve the original invoice totals across repeated partial credits.

## Concurrency and HA

The backend may run multiple replicas. Scheduled/worker processing must use shared database locking or claim semantics. Do not add local-disk locks or process-local correlation stores for work that must survive replica changes.

Spring Session, ShedLock and shared replay/idempotency controls rely on all replicas using the same database service.

The current Railway application topology requests two backend and two frontend replicas in Amsterdam. MySQL is private and currently MySQL 9.4. **Native Railway MySQL HA (three data nodes + two HAProxy instances) is not complete until the live conversion and controlled failover are verified.** Do not claim otherwise from repository configuration alone.

## Database

Flyway migrations under `InitializrSpringbootProjectFresh/src/main/resources/db/migration/` are canonical. The repository migration head for the current financial-correctness release is **V110**.

Never edit an already-applied migration. Add a new migration for schema/control changes and enforce financial invariants with SQL constraints when practical.

## Provider and regulator honesty

Code presence is not certification. Do not describe a provider, EFRIS/URA, BoU/regulatory or other external integration as production-certified unless live credentials, contract/schema behavior, callbacks/settlement and external acceptance have actually been verified.

Provider errors returned to merchants must be translated to stable non-sensitive messages. Keep raw provider bodies/internal exceptions in protected operational evidence, not merchant responses.

## Security

Never commit secrets. Protect provider credentials, merchant secrets, signing keys, callback/webhook secrets, private keys, database credentials and admin/actuator credentials.

Do not bypass authentication, RBAC, CSRF (browser routes), signing, nonce checks, idempotency, maker-checker controls or audit trails to make a test pass.

## Source cleanup

Remove unused imports, obsolete comments, duplicate helpers, generated junk and dead code only when usage is proven. Do not delete compatibility APIs just because they are ugly. Old payment code is sometimes ugly **and** still someone's production integration, an impressive two-for-one inconvenience.

Prefer delegation to a canonical service over maintaining two implementations of the same rule.

## Documentation

Keep root docs and affected `Docs/` material synchronized with behavior. Key root sources of truth:

- `Readme.md`
- `Installation.md`
- `Deployment.md`
- `CI_CD_SETUP.md`
- `Contributing.md`

Normative financial policy:

- `Docs/Financial-correctness-and-data-integrity.md`

Do not claim deployment, migration, finance close, provider certification or HA completion without runtime/external evidence.
