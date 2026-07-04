# Contributing to CPay

Thank you for contributing to CPay. This guide explains how to make useful, safe, and reviewable changes to the project.

CPay is a payments gateway. That means changes can affect merchant payments, customer payouts, callbacks, balances, reconciliation, and finance operations. Please treat every change with care, especially anything related to money movement, provider integrations, security, or production operations.

## Who this guide is for

This document is for:

- developers adding or fixing backend services
- frontend contributors improving the admin or merchant portal
- operations or finance contributors improving documentation and runbooks
- reviewers checking pull requests before they are merged

## Contribution principles

Please follow these principles when working on CPay:

| Principle | What it means |
|---|---|
| Keep payment flows safe | Avoid changes that can duplicate transactions, skip checks, or hide failures. |
| Preserve backward compatibility | Existing v1 merchant integrations must not be broken without a planned migration. |
| Prefer clear, small changes | Smaller pull requests are easier to review and safer to merge. |
| Document operational impact | If a change affects setup, security, callbacks, balances, reconciliation, or admin operations, update the relevant documentation. |
| Test what you change | Add or update tests for new behavior, bug fixes, and risky logic. |
| Protect secrets | Never commit credentials, private keys, provider secrets, callback secrets, or production URLs. |

## Repository areas

| Area | Main purpose |
|---|---|
| `InitializrSpringbootProject/` | Spring Boot backend, APIs, payment gateway services, migrations, and tests. |
| `clientside/` | React admin and merchant portal. |
| `integrations/citoconnect/` | JavaScript reference client and integration assets. |
| `docs/` | API documentation, architecture notes, readiness gates, and runbooks. |

## Before you start

Before making changes:

1. Read the `README.md` for the project overview.
2. Read `INSTALLATION.md` if you need to run the project locally.
3. Check the relevant documents under `docs/`.
4. Confirm whether your change affects merchant APIs, admin APIs, finance operations, provider integrations, or security.

If the change affects payment behavior, reconciliation, callbacks, balances, or provider communication, plan the change carefully. Payment bugs have a talent for becoming finance meetings, which nobody deserves.

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
- do not bypass request signing, nonce checks, idempotency, or merchant validation
- keep `/api/v1` behavior stable unless a migration plan exists
- add tests for service logic, request signing, parsing, callbacks, reconciliation, or money calculations where relevant

### Frontend

For frontend changes:

- keep screens simple and clear for operations users
- show loading, error, empty, and success states
- avoid exposing secrets in the browser
- confirm that admin-only actions are clearly labelled
- use confirmation prompts for high-risk actions such as callback requeue, secret rotation, daily close, or manual finance posting

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
cd InitializrSpringbootProject
mvn test
mvn verify
```

Frontend:

```bash
cd clientside
npm install
npm run build
```

For documentation-only changes, a build may not be required, but the changed document should still be reviewed for accuracy, clarity, and broken references.

## Database and migration changes

If your change requires a database update:

- add a Flyway migration under `InitializrSpringbootProject/src/main/resources/db/migration`
- use a unique migration version number
- avoid destructive schema changes unless there is a rollback or migration plan
- document any data backfill, cutover, or manual verification step
- test the migration against a staging copy before production use

## Security-sensitive changes

Treat the following as security-sensitive:

- request signing
- callback signing
- merchant public/private key handling
- admin authentication
- provider credentials
- callback secrets
- database credentials
- CORS configuration
- actuator access
- audit logs
- finance approval or posting controls

Security-sensitive changes should include a clear explanation of the risk, the control being added or changed, and how the change was tested.

## Market-readiness changes

If your change affects launch readiness, update the relevant readiness or runbook documents:

- `docs/readiness/market-readiness-gates.md`
- `docs/runbooks/production-incident-response.md`
- `docs/runbooks/provider-certification-checklist.md`
- `docs/runbooks/security-and-access-control.md`
- `docs/runbooks/callback-security-and-requeue.md`
- `docs/runbooks/reconciliation-finance-daily-close.md`

## Review checklist

Before requesting review, confirm that:

- the change is focused and easy to understand
- no secrets or production credentials are included
- affected documentation has been updated
- tests have been added or updated where needed
- migrations are safe and clearly named
- API changes are reflected in OpenAPI and examples where applicable
- manual testing requirements are clearly stated

## What should not be merged casually

Avoid merging changes casually if they affect:

- payment submission
- payout execution
- transaction status mapping
- balance updates
- callback delivery
- provider adapters
- reconciliation matching
- finance posting
- daily close
- admin authentication or permissions

These areas should be reviewed carefully because mistakes can create financial, operational, or compliance issues.

## Thank you

Good contributions make CPay safer, clearer, and easier to operate. That is the kind of progress even a payment gateway can respect.