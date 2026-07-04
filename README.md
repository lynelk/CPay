# CPay — Core Payments Gateway

CPay is a payments gateway platform for managing mobile money collections, payouts, transaction status checks, balances, callbacks, reconciliation, and operational monitoring from one central system.

In simple terms, CPay helps a business connect to payment providers such as MTN MoMo, Airtel Money, and Safaricom M-Pesa through a single gateway instead of building and managing every provider integration separately. It is designed to support both merchant-facing payment APIs and internal operations teams that need visibility over transactions, callbacks, balances, provider activity, and reconciliation.

## What CPay does

CPay provides the core services needed to operate a mobile money payment gateway:

| Capability | What it means |
|---|---|
| Collections | Receive money from a customer through a supported mobile money provider. |
| Payouts | Send money from a merchant or platform account to a customer or beneficiary. |
| Transaction status checks | Confirm whether a payment is pending, successful, failed, or unresolved. |
| Balance checks | Review available balances by merchant, channel, and currency. |
| Provider callbacks | Receive and process updates from payment providers. |
| Merchant callbacks | Notify merchants when a transaction changes status. |
| Reconciliation | Match provider statements against internal transaction records. |
| Finance operations | Support review, adjustment, reporting, and daily close workflows. |
| Operations monitoring | Track alerts, callback queues, provider checks, and readiness evidence. |

## Supported payment channels

The current gateway design supports the following channels through adapter-based integration:

| Provider | Country | Supported use cases |
|---|---|---|
| MTN MoMo | Uganda | Collections, payouts, balance checks, callbacks |
| Airtel Money | Uganda / Kenya | Collections, payouts, legacy API and OpenAPI support |
| Safaricom M-Pesa | Kenya | STK Push, B2C payouts, balance checks |

New providers should be added through the gateway adapter pattern described in `docs/gateway-adapter-guide.md`.

## Who uses CPay

CPay is designed for several user groups:

| User group | Main need |
|---|---|
| Merchants | Accept customer payments, make payouts, and check transaction status. |
| Operations teams | Monitor transactions, provider activity, callbacks, alerts, and failed tasks. |
| Finance teams | Reconcile provider statements, review exceptions, and complete daily close. |
| Developers | Integrate merchant systems using the v1 or v2 API. |
| Administrators | Manage system readiness, callbacks, provider testing, and operational controls. |

## Main components

The repository contains both backend and frontend components:

```text
InitializrSpringbootProject/   Spring Boot backend and payment gateway services
clientside/                    React-based admin and merchant portal
integrations/citoconnect/      JavaScript reference client and integration bundle
docs/                          API, architecture, readiness, and operations documentation
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

The v2 API is the newer, more structured API. It introduces explicit fields for channel, country, currency, versioned request signing, idempotency, and clearer error responses.

| Operation | Endpoint |
|---|---|
| Native collect | `POST /api/v2/native/payments/collect` |
| Native payout | `POST /api/v2/native/payments/payout` |
| List channels | `GET /api/v2/channels` |
| Transaction status | `GET /api/v2/payments/{reference}` |
| Balance check | `GET /api/v2/balances?merchantNumber=...` |

For developer details, see:

- `docs/api/cpay-v2-openapi.yaml`
- `docs/api/cpay-v2-postman-collection.json`
- `docs/api-v2-signing.md`
- `docs/api-v2-examples.md`
- `docs/citoconnect-integration.md`

## Admin and operations APIs

CPay also includes internal administrative APIs for controlled operations. These are not public merchant APIs.

| Area | Purpose |
|---|---|
| Balance sync | Backfill normalized balances from legacy balances. |
| Callback administration | Rotate callback secrets and requeue parked callbacks. |
| Provider sandbox testing | Record provider sandbox validation runs. |
| Statement validation | Validate provider statement files before import. |
| Reconciliation finance | Review summaries and close reconciliation days. |
| Operations dashboard | Track alerts, parked callbacks, and reconciliation exceptions. |
| Readiness dashboard | View non-manual market-readiness evidence counters. |

Admin routes are under `/api/v2/admin/**` and must be protected using admin credentials and operational controls.

## Market-readiness status

CPay is structured for controlled pilot use and provider certification preparation. Broad commercial rollout should only happen after the readiness gates are completed.

Readiness documentation is available in:

- `docs/readiness/market-readiness-gates.md`
- `docs/runbooks/production-incident-response.md`
- `docs/runbooks/provider-certification-checklist.md`
- `docs/runbooks/security-and-access-control.md`
- `docs/runbooks/callback-security-and-requeue.md`
- `docs/runbooks/reconciliation-finance-daily-close.md`
- `docs/runbooks/provider-sandbox-and-statement-validation.md`

Manual signoff is still required for items that cannot be completed through code alone, including real provider sandbox certification, staging migration validation, merchant callback verification, finance signoff, security review, production monitoring setup, and regulatory or compliance approval.

## Security model

CPay uses several layers of protection:

| Control | Purpose |
|---|---|
| Merchant request signing | Confirms that a merchant API request came from the expected merchant system. |
| Timestamp and nonce checks | Helps prevent replay of old API requests. |
| Idempotency keys | Helps merchants safely retry payment submissions without creating duplicates. |
| Admin route protection | Restricts internal operations to authorized administrators. |
| Signed callbacks | Allows merchants to verify that callback messages came from CPay. |
| Audit and readiness records | Supports operational tracking and post-incident review. |

Never commit `.env` files, provider credentials, production URLs, private keys, merchant secrets, or callback secrets to the repository. Humans have many hobbies; leaking payment secrets should not be one of them.

## Local development prerequisites

To run the project locally, you will need:

- Java 11 or later
- Maven
- MySQL 8 or compatible database
- Node.js 18 or later
- npm

## Local setup

1. Create a local database, for example `cpayadmin`.
2. Copy `.env.example` to `.env` and provide local values.
3. Import the baseline SQL files under `clientside/db/` if your local environment still needs the legacy schema setup.
4. Start the backend from `InitializrSpringbootProject`.
5. Start or build the frontend from `clientside`.

Backend:

```bash
cd InitializrSpringbootProject
mvn clean package
java -jar target/cito-0.0.1-SNAPSHOT.jar
```

Frontend:

```bash
cd clientside
npm install
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
| `GATEWAY_STATE` | Gateway mode, usually `SANDBOX` or `PRODUCTION`. |
| `CORS_ALLOWED_ORIGINS` | Allowed admin and merchant portal origins. |
| `APP_BASE_URL` | Public application URL used in generated links. |
| `HTTP_PORT` | Backend HTTP port. |
| `LOCK_FILE_DIR` | Scheduler lock-file directory. |
| `ACTUATOR_USERNAME` | Monitoring username. |
| `ACTUATOR_PASSWORD` | Monitoring password. |
| `ADMIN_API_USERNAME` | Admin API username. |
| `ADMIN_API_PASSWORD` | Admin API password. |
| `CALLBACK_SIGNING_SECRET` | Fallback secret used for callback signing where merchant-specific secrets are not configured. |

## Testing and quality checks

The repository includes CI checks for backend, frontend, security, and readiness assets.

Typical local checks:

```bash
cd InitializrSpringbootProject
mvn test
mvn verify
```

```bash
cd clientside
npm install
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
| `docs/api/cpay-v2-openapi.yaml` | Machine-readable v2 API contract. |
| `docs/api/cpay-v2-postman-collection.json` | Starter Postman collection for v2. |
| `docs/api-v2-signing.md` | v2 request-signing rules. |
| `docs/api-v2-examples.md` | Example v2 API requests. |
| `docs/gateway-adapter-guide.md` | How to add or maintain provider adapters. |
| `docs/readiness/market-readiness-gates.md` | Launch-readiness checklist. |
| `docs/runbooks/` | Operational procedures for production support. |

## Development roadmap

The main focus areas are:

1. Complete real provider sandbox certification for MTN, Airtel, Airtel OpenAPI, and Safaricom.
2. Replace any remaining legacy payment-routing paths with fully provider-native adapters.
3. Complete staging migration validation and balance reconciliation signoff.
4. Expand frontend admin screens for operations, finance, callbacks, readiness, and provider testing.
5. Complete production monitoring, alerting, and support escalation setup.
6. Complete security, compliance, and regulatory signoff before broad commercial launch.

## Final note

CPay is a payment gateway foundation with growing operational, finance, and developer-readiness controls. It should be treated as a controlled pilot and certification-ready platform until all provider, finance, security, and compliance gates are completed.
