# CPay — Core Payments Service Engine

CPay is a Spring Boot orchestration service with admin and merchant portals. It supports collections, payouts, status checks, balance checks, and provider callback handling across configured channels.

## Current native channels

| Network | Country | Capabilities |
|---|---|---|
| MTN MoMo | Uganda | Collections, disbursements, balance |
| Airtel Money | Uganda / Kenya | Collections, disbursements, legacy API + OpenAPI |
| Safaricom M-Pesa | Kenya | STK Push, B2C, balance |

Additional channels should be added through the gateway adapter pattern described in `docs/gateway-adapter-guide.md`.

## Repository layout

```text
InitializrSpringbootProject/   Spring Boot backend
clientside/                    React admin and merchant portal
integrations/citoconnect/      Node/JS reference client and integration bundle
docs/                          Architecture and integration documentation
```

## Prerequisites

- Java 11+
- Maven
- MySQL 8+
- Node.js 18+ for frontend development
- npm

## Local setup

1. Create a database, for example `cpayadmin`.
2. Copy `.env.example` to `.env` and provide local values.
3. Import the baseline SQL files under `clientside/db/` until the schema is fully migrated to Flyway.
4. Start the backend from `InitializrSpringbootProject`.
5. Start or build the frontend from `clientside`.

```bash
cd InitializrSpringbootProject
mvn clean package
java -jar target/cito-0.0.1-SNAPSHOT.jar
```

```bash
cd clientside
npm install
npm run build
```

## Environment variables

Never commit `.env`, provider credentials, production URLs, or merchant secrets.

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL for MySQL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |
| `GATEWAY_STATE` | `SANDBOX` or `PRODUCTION` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated admin/merchant portal origins |
| `APP_BASE_URL` | Public app URL used in email links |
| `HTTP_PORT` | Backend HTTP port |
| `LOCK_FILE_DIR` | Scheduler lock-file directory |
| `ACTUATOR_USERNAME` | Monitoring username |
| `ACTUATOR_PASSWORD` | Monitoring password |

## Merchant API surface

Current versioned endpoints are exposed under `/api/v1`:

| Operation | Endpoint |
|---|---|
| Collect | `POST /api/v1/doMobileMoneyPayIn` |
| Payout | `POST /api/v1/doMobileMoneyPayOut` |
| Status | `POST /api/v1/doTransactionCheckStatus` |
| Balance | `POST /api/v1/doGetBalances` |

See `docs/citoconnect-integration.md` for request shapes, signing rules, and CitoConnect integration details.

## Security notes

- Merchant API requests are RSA-signed.
- Merchant callback URLs are validated to reduce SSRF risk.
- Actuator endpoints must use explicit credentials.
- Do not document real deployment commands or production host details in the repository.

## Development roadmap

1. Formalise the gateway adapter registry and route all providers through it.
2. Add explicit `channel`, `country`, and `currency` request fields.
3. Introduce `/api/v2/payments/*` endpoints with a safer canonical signing contract.
4. Move database setup fully into Flyway migrations.
5. Add gateway health checks, callback retries, and reconciliation dashboards.
6. Modernise the React portal and expose structured gateway management.
