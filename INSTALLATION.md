# CPay Installation Guide

This guide explains how to set up CPay for local development, testing, staging, and production preparation.

CPay has two main parts:

| Component | Purpose |
|---|---|
| Backend | Spring Boot service that handles APIs, payment routing, callbacks, reconciliation, finance operations, operating controls, and admin services. |
| Frontend | React-based admin and merchant portal. |

Some steps require a developer or system administrator, because payment gateways do not install themselves out of politeness.

## 1. Before you begin

Confirm the purpose of the installation:

| Environment | Use case |
|---|---|
| Local development | Used by developers to build and test changes on their own machines. |
| Staging | Used to test migrations, provider sandbox activity, callbacks, reconciliation, and finance workflows before production. |
| Production | Used for real merchant and customer activity. Production requires manual security, provider, finance, monitoring, and compliance signoff. |

Do not use local development settings for production.

## 2. System requirements

Install the following tools:

| Tool | Recommended version | Purpose |
|---|---|---|
| Java | 11 or later | Runs the Spring Boot backend. |
| Maven | Latest stable | Builds and tests the backend. |
| MySQL | 8 or compatible | Stores transactions, merchants, callbacks, balances, reconciliation, channel setup, rate limits, and operations records. |
| Node.js | 18 or later | Builds the React frontend. |
| npm | Comes with Node.js | Installs frontend dependencies. |
| Git | Latest stable | Clones and manages the repository. |

## 3. Repository structure

```text
InitializrSpringbootProject/   Backend service
clientside/                    Frontend portal
integrations/citoconnect/      JavaScript integration assets
docs/                          API, readiness, security, and operations documentation
```

## 4. Database setup

Create a MySQL database for CPay.

Example database name:

```text
cpayadmin
```

The backend uses JDBC database configuration. The exact database user, password, and host should be provided through environment variables.

For local development, you may need to import baseline SQL files under:

```text
clientside/db/
```

Database changes are kept under Flyway migrations in:

```text
InitializrSpringbootProject/src/main/resources/db/migration
```

Recent production-control migrations include:

- callback task claim records
- provider endpoint run records
- operating-control event records
- API rate-limit windows
- merchant channel setup records

For staging or production, always test migrations on a copy of the database before applying them to a live environment.

## 5. Environment variables

Create a `.env` file for local development or configure these variables in your deployment environment.

Never commit `.env` files or real access values to the repository.

| Variable | Description |
|---|---|
| `DB_URL` | JDBC database URL. |
| `DB_USERNAME` | Database username. |
| `DB_PASSWORD` | Database password. |
| `MAIL_HOST` | SMTP server host. |
| `MAIL_PORT` | SMTP server port. |
| `MAIL_USERNAME` | SMTP username. |
| `MAIL_PASSWORD` | SMTP password. |
| `GATEWAY_STATE` | Gateway mode, usually `SANDBOX` or `PRODUCTION`. |
| `CORS_ALLOWED_ORIGINS` | Comma-separated trusted browser origins for the merchant and admin portals. |
| `APP_BASE_URL` | Public application URL used in generated links. |
| `HTTP_PORT` | Backend HTTP port. |
| `LOCK_FILE_DIR` | Directory used by schedulers for lock files. |
| `ACTUATOR_USERNAME` | Username for monitoring endpoints. |
| `ACTUATOR_PASSWORD` | Password for monitoring endpoints. |
| `ADMIN_API_USERNAME` | Username for admin API access. |
| `ADMIN_API_PASSWORD` | Password for admin API access. |
| `CALLBACK_SIGNING_SECRET` | Fallback value for signing merchant callbacks where merchant-specific values are not configured. |

Example local-only values:

```bash
DB_URL=jdbc:mysql://localhost:3306/cpayadmin
DB_USERNAME=cpay_user
DB_PASSWORD=change_me
GATEWAY_STATE=SANDBOX
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:2019
APP_BASE_URL=http://localhost:8080
HTTP_PORT=8080
LOCK_FILE_DIR=/tmp/cpay/locks
ACTUATOR_USERNAME=local-actuator
ACTUATOR_PASSWORD=change_me
ADMIN_API_USERNAME=local-admin
ADMIN_API_PASSWORD=change_me
CALLBACK_SIGNING_SECRET=change_me
```

Use stronger values outside local development. `change_me` is not a strategy, it is a future incident report.

## 6. Backend setup

From the repository root:

```bash
cd InitializrSpringbootProject
mvn clean package
java -jar target/cito-0.0.1-SNAPSHOT.jar
```

For development testing:

```bash
cd InitializrSpringbootProject
mvn test
mvn verify
```

If the backend fails to start, check:

- database connection settings
- missing environment variables
- port conflicts
- pending database migrations
- invalid admin or actuator credentials
- CORS origin settings

## 7. Frontend setup

From the repository root:

```bash
cd clientside
npm install
npm run build
```

For local frontend development, use the start command defined in `clientside/package.json` if available.

If the frontend build fails, check:

- Node.js version
- missing npm dependencies
- outdated lock file
- environment configuration
- API base URL settings

## 8. API documentation setup

The main API references are:

| Document | Purpose |
|---|---|
| `docs/api/cpay-v2-openapi.yaml` | Machine-readable v2 API contract. |
| `docs/api/cpay-v2-postman-collection.json` | Starter Postman collection. |
| `docs/api-v2-signing.md` | v2 request-signing rules. |
| `docs/api-v2-examples.md` | Example v2 requests. |
| `docs/citoconnect-integration.md` | Integration guide for merchant developers. |

The API documentation should be reviewed whenever payment endpoints, callback behavior, signing rules, or admin operations change.

## 9. Admin and monitoring setup

Admin routes are under:

```text
/api/v2/admin/**
```

Monitoring routes are under:

```text
/actuator/**
```

Use separate credentials for admin and monitoring access. Production access should be restricted to approved users and trusted network locations.

The operating-control summary is available at:

```text
/api/v2/admin/operating-controls/summary
```

## 10. Provider and merchant channel setup

Provider channel setup is managed per merchant through the merchant portal:

```text
Merchant Dashboard -> Payment Channels
```

Each configured channel should include:

- collect endpoint URL
- payout endpoint URL
- channel-specific setup values
- optional request header name and value where required for sandbox testing

In production mode, missing endpoint URLs should block execution.

Before enabling a provider for live traffic:

1. Run sandbox tests.
2. Validate callback handling.
3. Validate statement files.
4. Complete reconciliation testing.
5. Complete finance signoff.
6. Record evidence in the provider certification checklist.

See:

```text
docs/runbooks/provider-certification-checklist.md
```

## 11. Callback setup

Merchant callbacks should be tested before launch.

Required checks:

- callback URL is reachable
- callback payload is received
- callback signature is verified
- nonce and timestamp replay protection are handled by the merchant
- failed callbacks can be requeued if needed

Callback processing uses task-claim records to reduce duplicate delivery when multiple backend workers are running.

See:

```text
docs/runbooks/callback-security-and-requeue.md
```

## 12. Staging checklist

Before a release is considered ready for production:

- backend build passes
- frontend build passes
- migrations are tested on staging data
- provider sandbox checks are completed
- callback verification is completed
- statement validation is completed
- reconciliation daily close is dry-run
- operating-control summary is reviewed
- monitoring and alerts are configured
- security and compliance reviews are completed

## 13. Troubleshooting

| Problem | What to check |
|---|---|
| Backend does not start | Database URL, missing environment variables, port conflicts, migration errors. |
| Login/admin access fails | Admin username/password, trusted origins, browser origin. |
| API request is rejected | Signature headers, timestamp, nonce, merchant number, request body. |
| Provider call is rejected | Merchant channel setup, endpoint URLs, gateway mode, provider sandbox status. |
| Callback does not deliver | Callback URL, queue status, claim records, parked callbacks, merchant receiver logs. |
| Balance looks wrong | Legacy balance sync, normalized balances, transaction history, reconciliation records. |
| Statement validation fails | Provider format, required columns, duplicate references, file type. |

## 14. Production warning

Production deployment requires more than a successful installation. Before live merchant traffic is enabled, complete the readiness gates in:

```text
docs/readiness/market-readiness-gates.md
```

This includes provider certification, finance signoff, monitoring setup, security review, and compliance approval.
