# Contributing to CPay

Thank you for contributing to CPay. This guide explains how to make useful, safe, and reviewable changes to the project.

CPay is a payments gateway. Changes can affect merchant payments, customer payouts, callbacks, balances, reconciliation, channel setup, finance operations, and production controls. Please treat every change with care.

## Who this guide is for

This document is for:

- developers adding or fixing backend services
- frontend contributors improving the admin or merchant portal
- operations or finance contributors improving documentation and runbooks
- reviewers checking pull requests before they are merged

## Contribution principles

| Principle | What it means |
|---|---|
| Keep payment flows safe | Avoid changes that can duplicate transactions, skip checks, or hide failures. |
| Preserve backward compatibility | Existing v1 merchant integrations must not be broken without a planned migration. |
| Prefer clear, small changes | Smaller pull requests are easier to review and safer to merge. |
| Document operational impact | Update relevant docs when a change affects setup, security, callbacks, balances, reconciliation, channel setup, or admin operations. |
| Test what you change | Add or update tests for new behavior, bug fixes, and risky logic. |
| Protect access values | Never commit private keys, provider access values, callback signing values, admin access values, or production URLs. |

## Repository areas

| Area | Main purpose |
|---|---|
| `Initializrspringbootprojectfresh/` | Spring Boot 4.1 backend, APIs, payment gateway services, migrations, and tests. |
| `Clientside/` | React 18, Vite 8 admin and merchant portal with the CPay iOS-style design system. |
| `Integrations/Citoconnect/` | JavaScript reference client and integration assets. |
| `Docs/` | API documentation, architecture notes, readiness gates, production controls, and runbooks. |

## Before you start

Before making changes:

1. Read the `Readme.md` for the project overview.
2. Read `Installation.md` if you need to run the project locally.
3. Check the relevant documents under `Docs/`.
4. Confirm whether your change affects merchant APIs, admin APIs, finance operations, provider integrations, callback processing, scaling, or security.

Use `C:\Dev\CPay` as the local working copy on Windows. Do not do package installs, Maven builds, generated-output work, or repository updates from a Google Drive or OneDrive synced checkout.

If the change affects payment behavior, reconciliation, callbacks, balances, provider communication, or operating-control records, plan the change carefully. Payment bugs have a talent for becoming finance meetings, which nobody deserves.

## Branching and pull requests

Use a focused branch name that describes the change, for example:

```text
codex/improve-callback-retry-docs
codex/add-provider-statement-validation-test
codex/update-admin-operations-screen
```

Each pull request should include:

- a clear summary of what changed
- why the change was needed
- what areas are affected
- what testing was done
- any migration, deployment, or operational notes
- any remaining manual validation required

## Coding guidelines

### Backend

For backend changes:

- keep payment and finance calculations precise
- avoid floating-point arithmetic for money where possible
- keep provider-specific logic inside adapters or dedicated provider services
- never put a raw provider response body or a caught exception's message directly into a merchant-facing field; translate it through `net.citotech.cito.gateway.ProviderErrorTranslator` (or extend it) so merchants only ever see a stable, generic, non-sensitive message while the raw detail stays internal (logs, `provider_endpoint_runs`, etc.)
- reuse `net.citotech.cito.export.TabularExportService` for any new CSV/XLSX export surface rather than hand-building a CSV string or a client-side spreadsheet shim
- do not bypass request signing, nonce checks, idempotency, CSRF protection for browser routes, merchant validation, channel readiness checks, or operating-control records
- keep `/api/v1` behavior stable unless a migration plan exists
- keep `/api/v2/native/payments/*` adapter-backed behavior aligned with merchant channel credentials and the selected `CUSTOM_GATEWAYSTATE`
- use claim-based processing for callback workers where concurrency matters
- add tests for service logic, request signing, parsing, callbacks, reconciliation, provider endpoint handling, or money calculations where relevant

### Frontend

For frontend changes:

- keep screens simple and clear for operations users
- show loading, error, empty, and success states
- avoid exposing access values in the browser
- confirm that admin-only actions are clearly labelled
- use confirmation prompts for high-risk actions such as callback requeue, channel approval, daily close, or manual finance posting
- keep merchant channel setup clear enough for non-technical users
- when modernizing a legacy class component, follow the established pattern (see `ModuleDashboard.jsx`, `ModuleTransactions.jsx`, `ModuleReconciliation.tsx`, `ModuleAuditTrail.tsx`, `ModuleMerchantsAccount.tsx`, `MerchantModuleWebhooks.tsx`): typed `.tsx` function component, data fetching via a `src/shared/api/hooks.ts` query hook, mutation-driven cache invalidation instead of a manual reload, and inline field validation instead of only alert-dialog errors
- route new user-facing strings through `src/components/locale.js` rather than hardcoding English

### Documentation

For documentation changes:

- write in clear language that both technical and non-technical readers can understand
- explain what a feature does before explaining how to configure it
- keep merchant-facing, admin-facing, and internal finance documentation clearly separated
- update the README when a major capability, endpoint, or readiness position changes

## Testing expectations

Run the most relevant checks before submitting a pull request.

Backend:

```bash
cd Initializrspringbootprojectfresh
mvn test
mvn verify
```

`mvn test` excludes tests tagged `"docker"` by default, so an unavailable Docker daemon never
blocks a normal contribution. If your change touches something a Testcontainers-based DB test or
the `HealthEndpointE2ETest` end-to-end suite would cover, run them explicitly in a Docker-capable
environment before submitting:

```bash
cd Initializrspringbootprojectfresh
mvn test -Ddocker.tests.excludedGroups=
```

WireMock-based provider-mocking tests need no Docker and already run as part of the normal `mvn
test`. A separate, fully opt-in Gatling load-testing toolchain lives under
`src/test/java/net/citotech/cito/loadtest/` — it never runs as a side effect of `mvn test`/`mvn
verify`/`mvn package`; only run it deliberately (`mvn gatling:test
-Dgatling.simulationClass=net.citotech.cito.loadtest.<Simulation>`) against an environment you
intend to load-test, never against shared infrastructure without warning its owners first.

`mvn verify` also runs a Spotless (`google-java-format`, AOSP style) formatting check, but only
against files that actually differ from `origin/main` (`ratchetFrom`) — untouched legacy files are
grandfathered in, so you'll only ever be asked to format files you've actually modified. If it
fails, run `mvn spotless:apply` to auto-fix.

Frontend:

```bash
cd Clientside
npm install
npm run typecheck
npm test
npm run build
```

For documentation-only changes, a build may not be required, but the changed document should still be reviewed for accuracy, clarity, and broken references.

## Database and migration changes

If your change requires a database update:

- add a Flyway migration under `Initializrspringbootprojectfresh/src/main/resources/db/migration`
- use a unique migration version number
- avoid destructive schema changes unless there is a rollback or migration plan
- document any data backfill, cutover, or manual verification step
- test the migration against a staging copy before production use

## Sensitive changes

Treat the following as sensitive:

- request signing
- callback signing
- merchant public/private key handling
- admin authentication
- provider channel setup values
- callback signing values
- database access values
- CORS configuration
- CSRF configuration and `/auth/csrf`
- Spring Session JDBC tables and session timeout behavior
- actuator access
- audit logs
- operating-control records
- finance approval or posting controls
- callback worker claiming

Sensitive changes should include a clear explanation of the risk, the control being added or changed, and how the change was tested.

## Market-readiness changes

If your change affects launch readiness, update the relevant readiness or runbook documents:

- `Docs/Production-code-controls.md`
- `Docs/Readiness/Market-readiness-gates.md`
- `Docs/Runbooks/Production-incident-response.md`
- `Docs/Runbooks/Provider-certification-checklist.md`
- `Docs/Runbooks/Security-and-access-control.md`
- `Docs/Runbooks/Callback-security-and-requeue.md`
- `Docs/Runbooks/Reconciliation-finance-daily-close.md`

## Review checklist

Before requesting review, confirm that:

- the change is focused and easy to understand
- no private access values or production-only configuration values are included
- affected documentation has been updated
- tests have been added or updated where needed
- migrations are safe and clearly named
- API changes are reflected in OpenAPI and examples where applicable
- manual testing requirements are clearly stated
- provider, finance, security, or compliance signoff requirements are not hidden

## What should not be merged casually

Avoid merging changes casually if they affect:

- payment submission
- payout execution
- transaction status mapping
- balance updates
- callback delivery
- callback worker claiming
- provider adapters
- provider endpoint execution
- merchant channel setup
- reconciliation matching
- finance posting
- daily close
- admin authentication or permissions

These areas should be reviewed carefully because mistakes can create financial, operational, or compliance issues.

## Thank you

Good contributions make CPay safer, clearer, and easier to operate. That is the kind of progress even a payment gateway can respect.
