# Claude.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

CPay is a payments gateway platform for mobile money collections, payouts, status checks, balances,
callbacks, reconciliation, merchant self-service, and finance/ops workflows across MTN MoMo, Airtel
Money, Airtel OpenAPI, and Safaricom M-Pesa. It has real, unfinished production-readiness gaps — treat
payment, callback, reconciliation, and finance-path changes as high-risk (see "Sensitive areas" below).

Two API generations are live simultaneously: legacy `/api/v1/doMobileMoney*` (must stay stable) and
`/api/v2/**` (structured, signed, versioned). Do not merge or simplify them casually.

## Repository layout

```text
Initializrspringbootprojectfresh/   Active Spring Boot 4.1 backend (Java 21) — build/run/test from here
Clientside/                         React 18 + Vite 8 admin/merchant portal (TypeScript)
Integrations/Citoconnect/           JS reference client / integration bundle
Docs/                                Architecture, API contracts, ADRs, runbooks, readiness docs
Sdk/, Deployment/, Setup/            SDK assets, deployment scripts, local setup helpers
```

`InitializrSpringbootProject/` (without "Fresh") is an empty legacy scaffold — not in use, ignore it.

## Commands

### Backend (`Initializrspringbootprojectfresh/`)

```bash
mvn clean package                 # build
mvn test                          # unit tests
mvn verify                        # tests + verification bindings
mvn test -Dtest=ClassName                       # single test class
mvn test -Dtest=ClassName#methodName             # single test method
java -jar target/cito-fresh-0.0.1-SNAPSHOT.jar   # run the built jar
```

Runs on port `8081` by default (`HTTP_PORT`). Requires a MySQL 8 database and a `.env` populated from
`.env.example` (or exported env vars) — see `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and other keys
documented in the root `Readme.md`.

### Frontend (`Clientside/`)

```bash
npm install
npm run dev             # dev server on :3000, proxies /api /auth /admins /merchants /settings
                         # /audittrail /transactions /actuator to localhost:8081 (see vite.config.ts)
npm run build            # production build to build/
npm run typecheck        # tsc --noEmit
npm run lint              # eslint (flat config)
npm test                  # vitest run (once)
npm run test:watch        # vitest watch mode
npm run test:coverage     # vitest with coverage
npx vitest run src/path/to/File.test.tsx   # single test file
```

Node.js >= 20.19.0 required.

### Local DB / full stack

`compose.yaml` at repo root brings up MySQL + backend via Docker Compose for onboarding. Flyway
migrations live under `Initializrspringbootprojectfresh/src/main/resources/db/migration`
(`V1__...` .. current head) and are the canonical migration path — the legacy XML DB-change runner is
gated behind `CPAY_LEGACY_DBCHANGES_ENABLED` and should only be used to rebuild an old, unreconciled
local database.

## Backend architecture

Base package: `net.citotech.cito`.

- **Legacy layer (root package, flat)** — `ApiV1Controller`, `DoPayGateway`, `PaymentOrchestrationService`,
  `MerchantsController`, `SettingsController`, etc., plus request/response models under `Model/`
  (`Transaction`, `Payment`, `Merchant`, `*PaymentGateway` classes per provider). This is the original
  v1 implementation — provider logic here is hardcoded per-provider rather than adapter-based. Keep
  `/api/v1` behavior stable; changes here need a migration plan.
- **`gateway/`** — the adapter pattern that new/v2 work should use. Each provider implements
  `PaymentChannelAdapter` (see `Docs/Gateway-adapter-guide.md`) and is looked up through
  `PaymentChannelRegistry`; `GatewayExecutionService` executes the selected adapter, loading
  merchant-specific channel credentials for the active `CUSTOM_GATEWAYSTATE` (`SANDBOX`/`PRODUCTION`).
  Adapters: `MtnMomoAdapter`, `AirtelMoneyAdapter`, `AirtelOpenApiAdapter`, `SafaricomMpesaAdapter`,
  plus `LegacyGatewayAdapter` wrapping old provider classes for the transition period.
- **`api/v2/`** — v2 controllers/services: `PaymentsV2Controller` (compat `/api/v2/payments/*`),
  `NativePaymentsV2Controller` (adapter-backed `/api/v2/native/payments/*`), `V2RequestSecurityService`
  (request signing/nonce/idempotency enforcement), `IdempotencyService`, `PaymentStatusService`,
  `AccountValidationService`, `MerchantStatementExportService`. DTOs in `api/v2/dto/`.
- **`security/`** — request signing, nonce replay protection (in-memory or JDBC via
  `CPAY_SECURITY_NONCE_STORE`), admin MFA/TOTP, session/auth filters.
- **`callback/`** — provider and merchant callback processing; uses claim-based task assignment so
  multiple workers don't double-deliver.
- **`reconciliation/`** — statement matching, settlement scheduling, finance daily-close support.
- **`ledger/`** — double-entry ledger service (`DoubleEntryLedgerServiceTest` covers invariants).
- **`merchant/`** — merchant self-service signup, channel configuration.
- **`balance/`**, **`compliance/`**, **`checkout/`** (payment links/hosted checkout), **`scheduler/`**
  (timeout scans, cleanup jobs), **`webhook/`**, **`metrics/`**, **`admin/`**, **`portal/`**,
  **`config/`** (security/CORS/production-safety config + legacy deprecation header filter),
  **`repository/`**.

Config: `application.properties` (defaults, `SANDBOX` gateway state) and
`application-production.properties` (production profile guardrails — gateway mode and SSL verification
are locked down here). Most values are externalized as `${ENV_VAR:default}` — see the root `Readme.md`
"Key environment variables" table for the full list (DB, mail, actuator, admin API, callback signing,
merchant channel encryption key, etc.).

## Frontend architecture

- `src/App.tsx` / `src/Routers.tsx` — app shell and routing (React Router v7 native hooks for new code;
  a `withRouter`/`useHistory` compat shim exists at `src/shared/router/compat.tsx` for legacy class
  components only — do not use it in new code).
- `src/shared/api/httpClient.ts` — all new HTTP calls go through this; `src/shared/api/hooks.ts` for
  TanStack Query-based server state.
- `src/shared/csrfFetch.ts` — CSRF-aware fetch wrapper (backend CSRF token comes from `GET /auth/csrf`).
- `src/shared/config.ts` — `API_BASE` / `apiUrl()`; set via `VITE_API_BASE` (defaults to same-origin, so
  the dev proxy handles it).
- `src/ui/` — CPay iOS-style component primitives (design tokens in `src/index.css`); the ongoing
  migration off `rc-easyui` onto these primitives is tracked in `Clientside/Migration.md`.
- `src/features/` — feature modules (in progress; see `Notes.md` there).
- Auth/session model: cookie-based (`credentials: 'include'`) against the Spring Boot backend, not
  token-based.

## Sensitive areas (per `Contributing.md`)

Treat as high-risk and require careful review/testing: request signing, callback signing, merchant
key handling, admin authentication, provider channel setup values, database access values, CORS/CSRF
config, Spring Session JDBC config, actuator access, audit logs, operating-control records, finance
approval/posting, and callback worker claiming. Do not bypass request signing, nonce checks,
idempotency, CSRF protection, merchant validation, channel readiness checks, or operating-control
records. Avoid floating-point arithmetic for money.

Any DB change needs a new Flyway migration under `db/migration` with a unique version number — avoid
destructive changes without a rollback/migration plan.

## Documentation map

`Docs/` is extensive and split by concern — check it before large changes:
- `Docs/Gateway-adapter-guide.md` — how to add a provider adapter
- `Docs/Api-v2-signing.md`, `Docs/Api-v2-examples.md`, `Docs/Api-versioning-deprecation.md` — v2 API contract
- `Docs/Testing-strategy.md` — money-movement test invariants (idempotent retries, deduped callbacks,
  balanced ledger debits/credits, visible parked callback failures, audited reconciliation corrections)
- `Docs/Process-flow-controls.md`, `Docs/Money-ledger-and-orchestration-roadmap.md` — payment/ledger flow design
- `Docs/Readiness/Market-readiness-gates.md`, `Docs/Runbooks/` — launch readiness and operational runbooks
- `Docs/Adr/` — architecture decisions
- `Changelog.md` — keep updated before release/production deployment (calendar-versioned tags, e.g. `2026.07.16`)

## Notes

- Windows local dev: work from a plain local path (e.g. `C:\Dev\CPay`), not a OneDrive/Google Drive
  synced folder — Maven builds and package installs are unreliable there.
- `main` is the active development and default branch (verified against `origin/HEAD` and GitHub's
  default branch setting).
