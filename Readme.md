# CPay — Core Payments Gateway

CPay is a payments gateway platform for managing mobile money collections, payouts, transaction status checks, balances, callbacks, reconciliation, merchant channel setup, and operational monitoring from one central system.

In simple terms, CPay helps a business connect to providers such as MTN MoMo, Airtel Money, Airtel OpenAPI, and Safaricom M-Pesa through one gateway instead of building and managing every integration separately. It supports both merchant-facing APIs and internal operations teams that need visibility over transactions, callbacks, provider activity, reconciliation, and readiness controls.

## What CPay does

| Capability | What it means |
|---|---|
| Collections | Receive money from a customer through a supported mobile money provider. |
| Payouts | Send money from a merchant or platform account to a customer or beneficiary. |
| Transaction status checks | Confirm whether a payment is pending, successful, failed, or unresolved. |
| Balance checks | Review available balances by merchant, channel, and currency. |
| Merchant self-service | Allow merchants to register and configure their own supported channels. |
| Provider callbacks | Receive and process updates from payment providers. |
| Merchant callbacks | Notify merchants when a transaction changes status. |
| Reconciliation | Match provider statements against internal transaction records. |
| Finance operations | Support review, adjustment, reporting, and daily close workflows. |
| Operations monitoring | Track alerts, callback queues, operating controls, and readiness evidence. |

## Supported payment channels

The current gateway design supports the following adapter-backed channels:

| Provider | Country | Supported use cases |
|---|---|---|
| MTN MoMo | Uganda | Collections, payouts, balance checks, callbacks |
| Airtel Money | Uganda / Kenya | Collections, payouts, legacy API and OpenAPI support |
| Airtel OpenAPI | Uganda | Collections, payouts, sandbox and certification preparation |
| Safaricom M-Pesa | Kenya | STK Push, B2C payouts, balance checks |
| Yo! Payments | Uganda | Collections, payouts (native v2 adapter, response signature verification) |

Provider adapters can execute through merchant-configured endpoint URLs. In production mode, missing endpoint URLs should block execution.

New providers should be added through the gateway adapter pattern described in `Docs/Gateway-adapter-guide.md`.

## Who uses CPay

| User group | Main need |
|---|---|
| Merchants | Accept customer payments, make payouts, configure channels, and check transaction status. |
| Operations teams | Monitor transactions, provider activity, callbacks, alerts, failed tasks, and operating controls. |
| Finance teams | Reconcile provider statements, review exceptions, and complete daily close. |
| Developers | Integrate merchant systems using the v1 or v2 API. |
| Administrators | Manage readiness, callbacks, provider testing, channel approvals, and operational controls. |

## Main components

```text
Initializrspringbootprojectfresh/   Spring Boot 4.1 backend and payment gateway services
Clientside/                    React-based admin and merchant portal
Integrations/Citoconnect/      JavaScript reference client and integration bundle
Docs/                          API, architecture, readiness, and operations documentation
```

## API overview

CPay currently has two API generations.

### v1 API

The v1 API is the legacy merchant API. It is useful for existing integrations and supports core mobile money operations.

| Operation | Endpoint |
|---|---|
| Collect | `POST /api/v1/doMobileMoneyPayIn` |
| Payout | `POST /api/v1/doMobileMoneyPayOut` |
| Status check | `POST /api/v1/doTransactionCheckStatus` |
| Balance check | `POST /api/v1/doGetBalances` |

### v2 API

The v2 API is the newer, more structured API. It introduces explicit fields for channel, country, currency, versioned request signing, idempotency, and clearer error responses. The compatibility `/api/v2/payments/*` routes remain available, while `/api/v2/native/payments/*` routes execute through the adapter-backed gateway flow.

| Operation | Endpoint |
|---|---|
| Collect | `POST /api/v2/payments/collect` |
| Payout | `POST /api/v2/payments/payout` |
| Native collect | `POST /api/v2/native/payments/collect` |
| Native payout | `POST /api/v2/native/payments/payout` |
| List channels | `GET /api/v2/channels` |
| Transaction status | `GET /api/v2/payments/{reference}` |
| Balance check | `GET /api/v2/balances?merchantNumber=...` |

For developer details, see:

- `Docs/Api/cpay-v2-openapi.yaml`
- `Docs/Api/cpay-v2-postman-collection.json`
- `Docs/Api-v2-signing.md`
- `Docs/Api-v2-examples.md`
- `Docs/Citoconnect-integration.md`

## Merchant self-service

Merchants can register through the merchant portal at:

```text
/signup
```

After registration, the merchant account is created in a pending approval state. Logged-in merchants can configure supported payment channels under:

```text
Merchant Dashboard -> Payment Channels
```

Channel setup includes endpoint URLs and channel-specific setup values. Stored values are encrypted server-side and shown back to the merchant only in masked form.

Merchants can also manage their own webhook endpoints — register an endpoint, rotate its signing secret, view the delivery log, and replay a failed delivery — without needing an admin to do it on their behalf.

See:

```text
Docs/Merchant-self-service.md
```

## Admin and operations APIs

CPay includes internal administrative APIs for controlled operations. These are not public merchant APIs.

| Area | Purpose |
|---|---|
| Balance sync | Backfill normalized balances from legacy balances. |
| Callback administration | Rotate callback signing values and requeue parked callbacks. |
| Provider sandbox testing | Record provider sandbox validation runs. |
| Statement validation | Validate provider statement files before import. |
| Reconciliation finance | Review summaries and close reconciliation days. |
| Operations dashboard | Track alerts, parked callbacks, and reconciliation exceptions. |
| Operating controls | Review open operating-control event counts. |
| Readiness dashboard | View non-manual market-readiness evidence counters. |

Admin routes are under `/api/v2/admin/**` and must be protected using admin credentials and operational controls.

## Production and market-readiness status

The codebase now includes software controls for:

- merchant self-registration
- merchant-managed channel setup
- adapter-backed provider endpoint execution
- database-backed signup rate limiting
- claim-based callback processing for multiple workers
- restricted trusted origins for API access
- operating-control summary reporting
- reconciliation and finance workflow foundations
- risk/fraud authorization and double-entry ledger posting parity between the legacy and v2 payment paths
- step-up MFA for high-value merchant payouts
- distributed locking for multi-instance-safe money-movement crons
- defense-in-depth method-level admin authorization alongside path-based access rules
- a provider-to-merchant-safe error taxonomy so raw provider text never reaches a merchant response
- merchant self-service webhook secret rotation, delivery log, and failed-delivery replay
- uniform server-side CSV/XLSX export
- automatic dependency-update pull requests (Dependabot) and a CI formatting check

Readiness documentation is available in:

- `Docs/Production-code-controls.md`
- `Docs/Observability.md`
- `Docs/Data-retention.md`
- `Docs/Schema/Readme.md`
- `Docs/Api-versioning-deprecation.md`
- `Docs/Error-catalog.md`
- `Docs/Pagination.md`
- `Docs/Webhook-events.md`
- `Docs/Developer-experience.md`
- `Docs/Provider-integration-roadmap.md`
- `Docs/Security-authentication-roadmap.md`
- `Docs/Testing-strategy.md`
- `Docs/Money-ledger-and-orchestration-roadmap.md`
- `Docs/Process-flow-controls.md`
- `Docs/Reliability-scale-runbook.md`
- `Docs/Merchant-facing-features-roadmap.md`
- `Docs/Compliance-risk-controls.md`
- `Docs/Readiness/Market-readiness-gates.md`
- `Docs/Runbooks/Production-incident-response.md`
- `Docs/Runbooks/Operations-alerts.md`
- `Docs/Runbooks/Provider-certification-checklist.md`
- `Docs/Runbooks/Security-and-access-control.md`
- `Docs/Runbooks/Callback-security-and-requeue.md`
- `Docs/Runbooks/Reconciliation-finance-daily-close.md`
- `Docs/Runbooks/Provider-sandbox-and-statement-validation.md`

Architecture decisions are recorded in `Docs/Adr/`, and release notes are tracked in `Changelog.md`.

Manual signoff is still required for real provider sandbox certification, staging migration validation, merchant callback verification, finance signoff, security review, production monitoring setup, and regulatory or compliance approval. Code can support evidence. It cannot issue approvals, because apparently institutions remain stubbornly human.

## Security model

| Control | Purpose |
|---|---|
| Merchant request signing | Confirms that a merchant API request came from the expected merchant system. |
| Timestamp and nonce checks | Helps prevent replay of old API requests. |
| Idempotency keys | Helps merchants safely retry payment submissions without creating duplicates. |
| Signup rate limiting | Reduces repeated automated merchant registration attempts. |
| Risk/fraud authorization | Screens both the legacy and v2 payment paths before a pay-in or pay-out is submitted. |
| Step-up MFA for high-value payouts | Requires a fresh TOTP code for merchant payout batches above a configurable amount threshold. |
| JDBC-backed sessions | Stores admin and merchant portal sessions in the database for multi-worker operation. |
| CSRF token endpoint | Provides browser CSRF tokens through `GET /auth/csrf`; legacy API groups are exempted route-by-route instead of globally disabling CSRF. |
| Admin route protection | Restricts internal operations to authorized administrators, enforced at both the URL-path level and the method level (`@PreAuthorize`). |
| Trusted-origin API access | Limits browser access to configured origins. |
| Signed callbacks | Allows merchants to verify that callback messages came from CPay. |
| Provider response verification | Verifies signed provider responses (e.g. Yo! Payments) before trusting them. |
| Merchant-safe error messages | Translates raw provider responses and internal exceptions into a stable, generic message before they reach a merchant — the raw detail stays internal. |
| Callback task claims | Reduces duplicate callback delivery when multiple workers are running. |
| Distributed cron locking | Prevents the status-check and payout crons from processing the same batch twice across multiple instances. |
| Audit and readiness records | Supports operational tracking and post-incident review. |

Never commit `.env` files, provider access values, production URLs, private keys, merchant signing material, or callback signing values to the repository.

## Local development prerequisites

To run the project locally, you will need:

- Java 21
- Maven
- MySQL 8 or compatible database
- Node.js 20.19.0 or later
- npm

## Local setup

1. Create a local database, for example `cpayadmin`.
2. Copy `.env.example` to `.env` and provide local values.
3. Use the Flyway migrations under `Initializrspringbootprojectfresh/src/main/resources/db/migration`; import legacy baseline SQL only when rebuilding an older local database that has not yet been reconciled.
4. Start the backend from `Initializrspringbootprojectfresh`.
5. Start or build the frontend from `Clientside`.

Backend:

```bash
cd Initializrspringbootprojectfresh
mvn clean package
java -jar target/cito-fresh-0.0.1-SNAPSHOT.jar
```

Frontend:

```bash
cd Clientside
npm install
npm run dev
npm run build
```

## Key environment variables

| Variable | Description |
|---|---|
| `DB_URL` | JDBC database connection URL. |
| `DB_USERNAME` | Database username. |
| `DB_PASSWORD` | Database password. |
| `MAIL_HOST` | SMTP host for email notifications. |
| `MAIL_PORT` | SMTP port. |
| `MAIL_USERNAME` | SMTP username. |
| `MAIL_PASSWORD` | SMTP password. |
| `CUSTOM_GATEWAYSTATE` | Gateway mode, usually `SANDBOX` or `PRODUCTION`. |
| `CUSTOM_SSL_SKIP_VERIFY` | Explicitly controls non-production SSL verification bypasses. |
| `CORS_ALLOWED_ORIGINS` | Trusted merchant and admin portal origins. |
| `APP_BASE_URL` | Public application URL used in generated links. |
| `HTTP_PORT` | Backend HTTP port. |
| `CUSTOM_LOCKFILEDIRECTORY` | Scheduler lock-file directory. |
| `ACTUATOR_USERNAME` | Monitoring username. |
| `ACTUATOR_PASSWORD` | Monitoring password. |
| `SPRINGDOC_API_DOCS_ENABLED` | Enables `/v3/api-docs`; defaults to `false`. |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | Enables Swagger UI; defaults to `false`. |
| `CPAY_SECURITY_NONCE_STORE` | v2 replay-protection nonce store; defaults to `jdbc` (shared, safe for clustered deployments). Set to `memory` only for a single-instance local dev run. |
| `ADMIN_API_USERNAME` | Admin API username. |
| `ADMIN_API_PASSWORD` | Admin API password. |
| `CALLBACK_SIGNING_SECRET` | Fallback value used for callback signing where merchant-specific values are not configured. |
| `MERCHANT_CHANNEL_ENCRYPTION_KEY` | Encryption key used for merchant channel credentials at rest. |
| `CPAY_TRUSTED_PROXY_IPS` | Comma-separated IP(s) of trusted reverse proxies/load balancers. `X-Forwarded-For`/`X-Real-IP` are only honored when the direct TCP peer is one of these; empty (default) means the app always uses the raw socket address. |

## Testing and quality checks

Typical local checks:

```bash
cd Initializrspringbootprojectfresh
mvn test
mvn verify
```

`mvn test` excludes tests tagged `"docker"` (Testcontainers-based DB integration tests, the
end-to-end suite) by default so a missing Docker daemon never blocks the build; run
`mvn test -Ddocker.tests.excludedGroups=` in a Docker-capable environment for full coverage. A
separate opt-in Gatling load-testing toolchain (`mvn gatling:test -Dgatling.simulationClass=...`)
is never part of the default build.

```bash
cd Clientside
npm install
npm run typecheck
npm test
npm run build
```

The CI pipeline is expected to cover:

- backend build and tests
- frontend install and build
- migration naming checks
- API contract presence
- OWASP dependency scanning
- CodeQL security analysis
- readiness documentation checks

## Documentation map

| Document | Purpose |
|---|---|
| `Docs/Api/cpay-v2-openapi.yaml` | Machine-readable v2 API contract. |
| `Docs/Api/cpay-v2-postman-collection.json` | Starter Postman collection for v2. |
| `Docs/Api-v2-signing.md` | v2 request-signing rules. |
| `Docs/Api-v2-examples.md` | Example v2 API requests. |
| `Docs/Api-versioning-deprecation.md` | Versioning rules and legacy deprecation headers. |
| `Docs/Error-catalog.md` | Stable public error code catalog. |
| `Docs/Pagination.md` | Cursor pagination convention for list APIs. |
| `Docs/Webhook-events.md` | Versioned webhook event registry. |
| `Docs/Developer-experience.md` | SDK and docs portal expectations. |
| `Docs/Merchant-self-service.md` | Merchant signup and payment-channel setup guide. |
| `Docs/Production-code-controls.md` | Production-control code summary. |
| `Docs/Gateway-adapter-guide.md` | How to add or maintain provider adapters. |
| `Docs/Provider-integration-roadmap.md` | Provider adapter migration plan. |
| `Docs/Security-authentication-roadmap.md` | Authentication and security-control roadmap. |
| `Docs/Testing-strategy.md` | Money-movement and provider testing plan. |
| `Docs/Money-ledger-and-orchestration-roadmap.md` | Target ledger and payment state-machine design. |
| `Docs/Process-flow-controls.md` | Payin, payout, callback, signup, SMS, and recon controls. |
| `Docs/Reliability-scale-runbook.md` | Scaling, backup, shutdown, and job-safety runbook. |
| `Docs/Merchant-facing-features-roadmap.md` | Merchant product gap roadmap and new v2 endpoints. |
| `Docs/Compliance-risk-controls.md` | EAC-context risk, KYC, AML, retention, and reporting controls. |
| `Docs/Readiness/Market-readiness-gates.md` | Launch-readiness checklist. |
| `Docs/Runbooks/` | Operational procedures for production support. |

## Development roadmap

The main focus areas are:

1. Complete real provider sandbox certification for MTN, Airtel, Airtel OpenAPI, and Safaricom.
2. Complete staging migration validation and balance reconciliation signoff.
3. Expand frontend admin screens for operations, finance, callbacks, readiness, operating controls, and provider testing.
4. Complete production monitoring, alerting, and support escalation setup.
5. Complete security, compliance, and regulatory signoff before broad commercial launch.

## Final note

CPay now has a stronger production-oriented software foundation: merchant self-service, channel setup, provider endpoint execution, rate limiting, callback task claims, operating-control visibility, and readiness documentation. It should still be treated as certification-ready until all provider, finance, security, and compliance gates are completed.
