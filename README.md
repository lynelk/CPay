# CPay — Mobile Money Payment Gateway

CPay is a self-hosted payment gateway aggregator that bridges merchants to multiple East African mobile money networks:

| Network | Country | Capabilities |
|---|---|---|
| MTN MoMo | Uganda | Collections, Disbursements, Balance |
| Airtel Money | Uganda / Kenya | Collections, Disbursements (Legacy + OpenAPI) |
| Safaricom M-Pesa | Kenya | STK Push, B2C, Balance |

---

## Architecture

| Layer | Technology |
|---|---|
| Backend | Java 11, Spring Boot 2.7, Spring Security, Spring Session (JDBC) |
| Database | MySQL 5.7+ |
| Frontend | React 16, Ant Design 3, React Router 5 |
| Build | Maven (backend), react-app-rewired (frontend) |
| Metrics | Micrometer / Prometheus via Spring Actuator |

The React frontend is compiled into the Spring Boot static resources directory and served as a single-page application from the same port.

---

## Prerequisites

- Java JDK 11+
- MySQL 5.7+
- Node.js 14+ and npm
- Maven 3.6+

---

## Environment Variables

Copy `.env.example` to `.env` and fill in all values before starting the application. **Never commit `.env` to version control.**

### Required

| Variable | Description |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:mysql://localhost:3306/cpayadmin` |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `ACTUATOR_USERNAME` | HTTP Basic username for `/actuator/**` endpoints |
| `ACTUATOR_PASSWORD` | HTTP Basic password for `/actuator/**` endpoints |

> The application **will not start** if `ACTUATOR_USERNAME` or `ACTUATOR_PASSWORD` are missing. There are no built-in defaults.

### Recommended

| Variable | Description | Default |
|---|---|---|
| `GATEWAY_STATE` | `SANDBOX` or `PRODUCTION` | `SANDBOX` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed origins for the admin/merchant portals | `http://localhost:3000` |
| `APP_BASE_URL` | Base URL used in password-reset emails (no trailing slash) | `http://localhost:9000` |
| `HTTP_PORT` | Port the application listens on | `9000` |
| `LOCK_FILE_DIR` | Directory for scheduler lock files | `/tmp/cpay/locks/` |

### Email (SMTP)

| Variable | Description |
|---|---|
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |

### SSL / TLS

To enable HTTPS, uncomment the four SSL lines in `application.properties` and set:

| Variable | Description |
|---|---|
| `SSL_KEY_STORE` | Path to PKCS12 keystore |
| `SSL_KEY_STORE_PASSWORD` | Keystore password |
| `SSL_KEY_ALIAS` | Certificate alias |

See [Spring Boot SSL docs](https://docs.spring.io/spring-boot/docs/current/reference/html/application-properties.html#appendix.application-properties.server) and the Let's Encrypt section below.

---

## Database Setup

```bash
# 1. Create the schema and tables
mysql -u root -p < clientside/db/structure.sql

# 2. Apply cumulative DB changes
mysql -u root -p cpayadmin < clientside/db/db_changes.sql

# 3. Seed the initial admin user
mysql -u root -p cpayadmin < clientside/db/initialize.sql
```

Default admin credentials (change immediately after first login):

| Field | Value |
|---|---|
| Email | *(see `initialize.sql`)* |
| Password | *(see `initialize.sql`)* |

---

## Installation

```bash
# Clone and enter the repo
git clone <repo-url>
cd CPay

# Copy and populate environment variables
cp .env.example .env
# Edit .env with your values

# Run the install script (sets up the service)
cd setup
./install.sh
```

---

## Building

### Frontend (React)

```bash
cd clientside
npm install
npm run build
```

The build script (`react-app-rewired`) compiles the React app and copies the output to `InitializrSpringbootProject/src/main/resources/static/` automatically.

If you build manually, copy the contents of `clientside/build/` to that directory and make the following edits to `index.html`:

1. Add the loader CSS to `<head>`:
   ```html
   <style>.loader:empty{position:absolute;top:calc(50% - 4em);left:calc(50% - 4em);width:6em;height:6em;border:1.1em solid rgba(0,0,0,.2);border-left:1.1em solid #000;border-radius:50%;animation:load8 1.1s infinite linear}@keyframes load8{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>
   <script>function onLoad(){document.getElementById("cpay_loader").className=""}</script>
   ```
2. Add the loader `<div>` at the start of `<body>`:
   ```html
   <div id="cpay_loader" class="loader"></div>
   ```
3. Add `onload="onLoad();"` to the `<body>` tag.
4. Change `<link rel="icon" href="/favicon.ico"/>` to `<link rel="icon" href="/favicon.png"/>`.
5. Set `<title>CPay</title>`.

### Backend (Maven)

```bash
cd InitializrSpringbootProject
mvn package -DskipTests
# Output: target/cito-0.0.1-SNAPSHOT.jar
```

---

## Running

```bash
# Start
/etc/init.d/cpayadmin/start.sh

# Restart
/etc/init.d/cpayadmin/restart.sh

# Stop
/etc/init.d/cpayadmin/shutdown.sh
```

The application listens on the port defined by `HTTP_PORT` (default **9000**).

### Logs

| Log | Path |
|---|---|
| Application | `/var/log/cpayadmin/log.txt` |
| Console | `/tmp/cpayadmin.log` |

---

## SSL Certificate (Let's Encrypt)

```bash
# 1. Stop any service on port 80
# 2. Obtain / renew certificate
sudo certbot certonly -a standalone -d yourdomain.example.com

# 3. Convert to PKCS12
openssl pkcs12 -export \
  -in /etc/letsencrypt/live/yourdomain.example.com/fullchain.pem \
  -inkey /etc/letsencrypt/live/yourdomain.example.com/privkey.pem \
  -out springboot_letsencrypt.p12 \
  -name bootalias \
  -CAfile /etc/letsencrypt/live/yourdomain.example.com/chain.pem

# 4. Place the .p12 file in:
InitializrSpringbootProject/src/main/resources/keystore/

# 5. Set SSL_KEY_STORE, SSL_KEY_STORE_PASSWORD, SSL_KEY_ALIAS env vars
# 6. Uncomment the SSL lines in application.properties
# 7. Restart the server
```

Reference: [Spring Boot + Let's Encrypt](https://dzone.com/articles/spring-boot-secured-by-lets-encrypt)

---

## Security Notes

- **CSRF**: Currently disabled (see `SecurityConfig.java` TODO). To enable it, update the React frontend to read the `XSRF-TOKEN` cookie and send its value in the `X-XSRF-TOKEN` request header on all mutating requests.
- **Rate limiting**: Login attempts are limited to 5 per IP per 15 minutes. Password-reset endpoints share the same limit. Merchant API calls are limited to 60 requests per minute per merchant (configurable).
- **Password hashing**: New passwords use BCrypt. Legacy SHA-256 hashes are automatically upgraded to BCrypt on the next successful login.
- **Merchant API authentication**: RSA-SHA256 request signatures are verified against each merchant's stored public key.
- **Callback URL validation**: Outbound callback URLs are validated to block private/loopback addresses (SSRF prevention). URLs whose hostnames cannot be resolved are rejected.
- **Actuator endpoints**: Protected with HTTP Basic authentication. Expose only the endpoints you need via `management.endpoints.web.exposure.include` in `application.properties`.
- **Merchant private keys**: Stored in the database and used to sign outbound callbacks. Consider encrypting at rest or using a secrets manager for production deployments.

---

## Monitoring

Prometheus metrics are available at `/actuator/prometheus` (requires actuator credentials).

Key custom metrics:

| Metric | Description |
|---|---|
| `cpay.transaction.initiated` | Transactions started, tagged by gateway and type |
| `cpay.transaction.completed` | Transactions completed, tagged by status |
| `cpay.callback.delivery` | Callback delivery outcomes |
| `cpay.gateway.error` | Gateway API errors |
| `cpay.rate_limit.exceeded` | Merchant API rate-limit hits |

---

## API Overview

All merchant API endpoints are under `/api/` (also accessible via `/api/v1/` for versioned clients). Requests must be signed with the merchant's RSA private key.

| Endpoint | Method | Description |
|---|---|---|
| `/api/doMobileMoneyPayIn` | POST | Initiate a collection (pay-in) |
| `/api/doMobileMoneyPayOut` | POST | Initiate a disbursement (pay-out) |
| `/api/doTransactionCheckStatus` | POST | Query transaction status |
| `/api/doGetBalances` | POST | Retrieve gateway balances |

Admin / merchant portal endpoints are under `/auth/` (session-based).

---

## Project Structure

```
CPay/
├── clientside/              # React frontend
│   ├── src/
│   │   ├── components/      # Shared components and page modules
│   │   └── ...
│   ├── db/                  # SQL schema and seed files
│   └── package.json
├── InitializrSpringbootProject/
│   └── src/main/java/net/citotech/cito/
│       ├── Api.java                    # Merchant payment API
│       ├── AuthenticationController.java
│       ├── Common.java                 # Shared utilities
│       ├── DoPayGateway.java           # Gateway routing
│       ├── config/                     # Spring Security, SSL, OpenAPI
│       ├── metrics/                    # Micrometer counters
│       ├── scheduler/                  # Callback retry, transaction timeout
│       ├── security/                   # Rate limiter, password utils, SSRF validator
│       └── service/                    # Per-merchant API rate limiter
├── setup/                   # Deployment scripts
├── .env.example             # Environment variable template
├── INSTALLATION.md
└── SECURITY.md
```
