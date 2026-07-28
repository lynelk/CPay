# CPay Admin Backend (Fresh)

Spring Boot 4.1.0 · Java 21+ · MySQL 8

## Architecture overview

The backend is the system of record for merchant accounts, payment execution, callbacks, reconciliation, operations controls, and settings. It keeps the legacy `/api` and `/api/v1` surfaces available while newer code moves toward `/api/v2` services and gateway adapters.

```text
Controller layer
  AdminsController, Api, ApiV1Controller, SettingsController, portal/*, api/v2/*

Service layer
  PaymentOrchestrationService, callback/*, reconciliation/*, balance/*, merchant/*

Gateway layer
  gateway/* adapters, provider endpoint execution, legacy gateway wrappers

Persistence
  NamedParameterJdbcTemplate repositories plus Flyway migrations

Operations
  scheduler/*, admin/* dashboards, metrics/*, structured logging
```

Important package boundaries:

| Package | Responsibility |
|---|---|
| `api/v2` | Versioned merchant payment API and request security |
| `gateway` | Provider/channel adapter boundary |
| `callback` | Merchant callback task queue, claims, signing, and delivery |
| `reconciliation` | Provider statement parsing, matching, reviews, and finance close |
| `balance` | Normalized channel balances and ledger events |
| `admin` | Operating controls, readiness, permissions, and feature flags |
| `security` | CSRF, replay protection, signatures, allowlists, and rate limits |
| `scheduler` | Background retry, timeout, float alert, and cleanup jobs |

## Operational flows

### Collection or payout

1. Merchant request enters `/api/v1`, `/api/v2/payments/*`, or `/api/v2/native/payments/*`.
2. Request validation checks required fields, merchant status, signatures, replay protection, and rate limits.
3. The orchestration path selects a channel and calls the matching gateway adapter or legacy gateway wrapper.
4. Transaction rows are written to `merchant_transactions_log` and related statement/balance tables.
5. Final statuses enqueue merchant callback tasks where a callback URL exists.

### Callback delivery

1. `CallbackRetryScheduler` finds final transactions that still need callbacks.
2. `CallbackTaskService` signs and sends callbacks.
3. Successful delivery marks tasks `DONE`; failed attempts move to `RETRY` or `PARKED`.
4. Operations users follow `Docs/Runbooks/Operations-alerts.md` for parked callbacks.

### Reconciliation

1. Provider statements are parsed by provider-specific parser registrations.
2. `reconciliation_records` are matched to gateway transactions.
3. Exceptions are categorized and reviewed before finance close.
4. Daily close evidence is retained for audit.

## Schema and migrations

Flyway migrations under `src/main/resources/db/migration` are the canonical schema path. The old XML DB-change runner is disabled by default and should only be enabled for explicit legacy recovery work with `CPAY_LEGACY_DBCHANGES_ENABLED=true`.

Schema documentation:

- `Docs/Schema/Readme.md`
- `Docs/Schema/snapshots/2026-07-16-cpayadmin.sql`
- `Docs/Data-retention.md`
- `Docs/Money-ledger-and-orchestration-roadmap.md`
- `Docs/Process-flow-controls.md`
- `Docs/Reliability-scale-runbook.md`

## Observability

The backend emits JSON logs through `logback-spring.xml`, propagates `X-Request-ID`, and exposes Prometheus metrics through Actuator. See `Docs/Observability.md`.

## Production safeguards

Production profiles (`prod` or `production`) require `custom.gatewaystate=PRODUCTION` and reject `custom.ssl.skip-verify=true`. Graceful shutdown is enabled by default, and transaction timeout settings are configurable with `CPAY_TRANSACTION_TIMEOUT_MINUTES` and `CPAY_TRANSACTION_TIMEOUT_SCAN_DELAY_MS`.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 21 or 22 | Java 17 on your system PATH will **not** work — it cannot compile Java 21 targets |
| Maven | 3.8+ | Bundled with IntelliJ or installed separately |
| MySQL | 8.x | Database `cpayadmin` must exist |

## First-time setup

### 1. Create the database

```sql
CREATE DATABASE IF NOT EXISTS cpayadmin CHARACTER SET utf8mb3;
```

### 2. Create `application-local.properties`

Create the file at:
```
src/main/resources/application-local.properties
```

Minimum contents (update credentials to match your local MySQL):

```properties
# Database
spring.datasource.url=jdbc:mysql://localhost:3306/cpayadmin?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# Actuator / admin API
actuator.username=actuator
actuator.password=actuator123
admin.api.username=apiadmin
admin.api.password=apiadmin123

# Signing secrets (any non-blank value for local dev)
callback.signing.secret=local-dev-callback-secret-changeme
merchant.channel.encryption.key=local-dev-encrypt-key-changeme-32

# Mail (dummy values — app will start but won't send real emails)
spring.mail.host=localhost
spring.mail.port=1025
spring.mail.username=dev@local
spring.mail.password=dev

# Disable devtools restart
spring.devtools.restart.enabled=false
spring.devtools.livereload.enabled=false
```

> `application-local.properties` is gitignored — never commit it.

Flyway will automatically create all tables on first startup.

---

## Running the backend

> **Important:** If your system `PATH` defaults to Java 17, you must point to Java 21/22 explicitly, otherwise Maven will fail with `release version 21 not supported`.

### PowerShell

```powershell
$env:JAVA_HOME = "D:\joe\Software\java\openlogic-openjdk-22.0.2+9-windows-x64\openlogic-openjdk-22.0.2+9-windows-x64"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd "D:\joe\Jose\projects\joeWork\cito\cpay\newcpay\CPay\Initializrspringbootprojectfresh"
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### Command Prompt

```cmd
set JAVA_HOME=D:\joe\Software\java\openlogic-openjdk-22.0.2+9-windows-x64\openlogic-openjdk-22.0.2+9-windows-x64
set PATH=%JAVA_HOME%\bin;%PATH%
cd D:\joe\Jose\projects\joeWork\cito\cpay\newcpay\CPay\Initializrspringbootprojectfresh
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### Git Bash

```bash
export JAVA_HOME="D:/joe/Software/java/openlogic-openjdk-22.0.2+9-windows-x64/openlogic-openjdk-22.0.2+9-windows-x64"
export PATH="$JAVA_HOME/bin:$PATH"
cd "D:/joe/Jose/projects/joeWork/cito/cpay/newcpay/CPay/Initializrspringbootprojectfresh"
mvn spring-boot:run -Dspring-boot.run.profiles=local "-Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false"
```

### IntelliJ IDEA (recommended)

1. **Run → Edit Configurations → `+` → Application**
2. Set **Main class**: `net.citotech.cito.CpayadminFreshApplication`
3. Set **VM options**: `-Dspring.profiles.active=local`
4. Set **JRE**: point to your Java 21/22 installation
5. Click **Run**

All startup logs appear unfiltered in the IntelliJ console with this approach.

---

## Stopping the backend

- **Terminal**: `Ctrl + C`
- **IntelliJ**: click the red Stop button

---

## Verify it is running

```
GET http://localhost:8081/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

The app starts on port **8081** by default. Override with env var `HTTP_PORT`.
