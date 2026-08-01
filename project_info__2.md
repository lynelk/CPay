# CPay — Codebase Overview & Open-Implementation Audit

> Cross-checked against the attached `CPay-Comprehensive-Review-Fresh (1).docx` (rev. 3 catalog, commit `da16833`). Every claim below was verified in code on this checkout.

---

## Summary

CPay is a mobile-money payments gateway (collections, payouts, status checks, balances, callbacks, reconciliation, merchant self-service, finance/ops workflows) for MTN MoMo, Airtel Money, Airtel OpenAPI, Safaricom M-Pesa, and Yo! Payments. Two API generations run side-by-side: a legacy flat `/api`/`/api/v1` surface (the original monolith, must stay stable) and a signed/versioned `/api/v2/**` surface backed by a new adapter/ledger/risk architecture.

The codebase is **not a single unfinished scaffold**. It is a working payment gateway with a **verified, strong production-control foundation** (Flyway V1–V30, ShedLock, JDBC sessions, signed v2, double-entry ledger, risk authorization, circuit breaker, merchant channel crypto, reconciliation, reporting aggregates, operational cleanup) and a set of **well-defined structural long-poles** the attached review confirms are still open: legacy money-path ledger unification (A1/B1), god-class decomposition (J2), real sanctions screening (I2/I8), KYC tiers (I3), and continued frontend module migration (L1/L3/L4).

---

## Architecture

**Pattern:** Layered monolith with an emerging event-driven callback/claims subsystem and a plugin-style gateway adapter boundary.

```
Controllers → Services → Gateway adapters / Provider endpoint execution → JDBC repositories → MySQL 8 (Flyway-managed)
                 ↑                ↑
           Risk/ledger/     Callback queue (claim-based),
           webhook seams     webhooks, reconciliation, schedulers
```

**Stack:**
- Backend: Spring Boot 4.1 (Spring Framework 7.x), Java 21, virtual threads on, Maven, MySQL 8, Flyway, Spring Session JDBC, ShedLock 7.7.0, Bucket4j 8.0.1, Apache POI 5.5.1, springdoc 3.0.3, Micrometer/Prometheus, Logstash logback encoder. Base package `net.citotech.cito`.
- Frontend: React 18.3 + Vite 8 (Rolldown), TypeScript (incremental, `allowJs`), TanStack Query v5, React Router v7, Tailwind 4, Vitest, own iOS-style `src/ui` design system. Runs on :3000, proxies `/api` `/auth` `/transactions` etc. to :8081.
- Docs/SDK: `Docs/` (API contracts, ADRs, runbooks, readiness), `Sdk/` (Node/PHP/Python signing clients), `Integrations/Citoconnect/` (JS reference client).

**Execution start:**
1. `CpayadminApplication` → `StartupApplicationListener`, `SchedulerLockConfig`, `SecurityConfig`, `RestClientOutboundHttpExecutor.register()` (installs `Common.OutboundHttpExecutor`), `SslConfig` (propagates app base URL + trusted proxy IPs).
2. Requests land either on legacy flat controllers (`ApiV1Controller`, `TransactionsLogController`, `AdminsController`, …) or on `/api/v2/**` (`PaymentsV2Controller`, `NativePaymentsV2Controller`, merchant/service controllers).
3. Scheduled work runs via `@Scheduled` + `@SchedulerLock` (ShedLock): `CallbackRetryScheduler`, `TransactionTimeoutScheduler`, `SettlementSweepScheduler`, `LedgerOperationsScheduler`, `OperationalDataCleanupScheduler`, `ReportingAggregateScheduler`, `InvoiceExpiryScheduler`, `FloatAlertScheduler`, `MerchantWebhookDeliveryScheduler`, `TransactionLogArchivalScheduler`, plus the two money-movement crons inside `TransactionsLogController` (`testCheckstatusCron`, `paymentsPayCron`).

---

## Directory Structure (annotated)

```
CPay/
├── InitializrSpringbootProjectFresh/        # THE backend (build/run/test here)
│   ├── pom.xml                              # Java 21, Spring Boot 4.1, ShedLock, wiremock, gatling (opt-in), spotless
│   └── src/main/
│       ├── java/net/citotech/cito/
│       │   ├── Common.java                  # GOD CLASS (2,900+ lines): doPayIn/doPayOut/recordStatementTx + static helpers
│       │   ├── TransactionsLogController.java # GOD CLASS (6,000+ lines): tx lists, dashboard queries, crons, SMS, uploads
│       │   ├── PaymentOrchestrationService.java # v2 compat engine: risk → adapter → Common legacy engine → ledger+webhook
│       │   ├── DoPayGateway.java            # legacy per-provider dispatch + charge math (Double)
│       │   ├── ApiV1Controller.java         # /api/v1 doMobileMoney* (live, unchanged)
│       │   ├── gateway/                     # PaymentChannelAdapter, Registry, ExecutionService, ProviderEndpointExecutionService,
│       │   │                                #   CircuitBreaker, ChannelRouting*, ProviderErrorTranslator, YoPaymentsCallbackVerifier,
│       │   │                                #   RestClientOutboundHttpExecutor, token stores, adapters (MTN/Airtel/OpenAPI/M-Pesa/Yo/Legacy)
│       │   ├── api/v2/                      # PaymentsV2/NativePaymentsV2/BatchPayoutsV2/RefundsV2 controllers + security/idempotency/status/export services
│       │   ├── security/                    # Signing, nonce (jdbc|memory), MFA (admin+merchant), rate limit, TOTP, password reset, sessions
│       │   ├── callback/                    # Claim-based task queue, signing, admin/ops controllers
│       │   ├── webhook/                     # MerchantWebhookService (admin + merchant self-service), event catalog
│       │   ├── ledger/                      # DoubleEntryLedgerService, LegacyLedgerPostingService, repair sweep, trial balance
│       │   ├── reconciliation/              # Import/validate/match/review/finance-close + 5 provider statement parsers
│       │   ├── balance/                     # AuthoritativeBalanceService (legacy backfill), balance views/repos
│       │   ├── merchant/                    # Self-service signup, channel credentials + crypto, email verification, notification prefs
│       │   ├── admin/                       # Readiness (platform + per-merchant), ops dashboard, impersonation, permissions, feature flags
│       │   ├── scheduler/                   # All @Scheduled jobs (cleanup, timeout, settlement, ledger, reporting, invoices, floats, webhooks, archival)
│       │   ├── checkout/  crossborder/  payout/  refund/  fees/  batch/
│       │   ├── compliance/                  # RiskDecisionService (blocklist/caps), sanctions scaffold, KYC scaffold, case service
│       │   ├── reporting/  metrics/  audit/  portal/  export/  upload/  async/  config/  repository/
│       │   └── Model/                       # Legacy models incl. provider gateway monoliths (SafariComPaymentGateway ~1,088 lines)
│       └── resources/
│           ├── application.properties       # defaults: nonce=jdbc, graceful shutdown, cleanup toggles, ledger repair limit
│           ├── application-production.properties # locks gateway state + nonce store, removes dev CSP carve-out
│           └── db/migration/V1..V30         # canonical schema path (baseline V1 .. shedlock V30)
├── Clientside/                              # React 18 + Vite 8 admin/merchant portal
│   └── src/
│       ├── Routers.tsx / App.tsx            # lazy-loaded shells (/dashboard/* admin, /dashboardMerchant/* merchant)
│       ├── features/OperationsConsole.jsx   # ⚠ MINIMAL STUB — see open items F-1
│       ├── components/modules/              # Module*.jsx/.tsx (dashboard/transactions/recon/audit/merchants/account converted to hooks/TSX)
│       ├── components/modules/merchant/     # merchant variants (transactions/payments/sms/settings/channels/dashboard…)
│       ├── shared/api/hooks.ts              # ✅ REAL TanStack domain hooks (L2 verified closed)
│       ├── shared/api/httpClient.ts / v2Client.ts / csrfFetch.ts / config.ts / useAuth.ts
│       └── ui/                              # iOS-style primitives (Button, Table w/ card fallback, Sheet, Badge, …)
├── Docs/                                    # ~40 docs: Architecture/, Api/, Runbooks/, Adr/, Readiness/, Schema/snapshots/
├── Sdk/  Integrations/  deployment/  setup/ # SDKs (Node/PHP/Python), Citoconnect client, deploy scripts
└── compose.yaml                             # MySQL 8.4 (:3307) + backend onboarding
```

---

## Key Abstractions

### PaymentChannelAdapter (`gateway/PaymentChannelAdapter.java`)
- **Responsibility:** the provider-agnostic channel contract. Each provider implements `channelCode()`, `capabilities()`, `collect()`, `payout()`, `checkStatus()`, `getBalance()`, and (optionally) `verifyCallback()` for signed provider responses.
- **Lifecycle:** Spring beans collected by `PaymentChannelRegistry` at startup.
- **Used by:** `PaymentOrchestrationService`, `AdapterNativePaymentService`, `/api/v2/channels`.

### PaymentOrchestrationService (`PaymentOrchestrationService.java`)
- **Responsibility:** v2 compatibility engine. Validates merchant/request → risk check → resolves legacy gateway id + adapter → computes charges (`DoPayGateway` Double math) → calls **`Common.doPayIn/doPayOut` with `skipRiskCheck=true`** → posts double-entry ledger entries → captures/releases ledger reservation on payout → queues a webhook.
- **Key invariant:** ledger `post()` is keyed `payment:<tx_unique_id>` so it can never double-post; risk check runs exactly once.
- **Used by:** `PaymentsV2Controller` (compat `/api/v2/payments/*`), `PaymentLinkService`, `InvoiceService`.

### GatewayExecutionService (`gateway/GatewayExecutionService.java`)
- Virtual-thread executor for gateway calls; unwraps `ExecutionException`. The adapter-native path.

### ProviderEndpointExecutionService (`gateway/ProviderEndpointExecutionService.java`)
- Executes a configured provider endpoint URL from merchant channel metadata. Sandbox mode synthesizes deterministic scenarios by account suffix (`…000002` fail, `…000003` pending, `…000004` timeout, `…000005` unsupported). Production mode **throws if endpoint URL missing**. Wires the Yo! HMAC signature verification, records every run to `provider_endpoint_runs`, and translates raw provider text via `ProviderErrorTranslator` before it can reach a merchant.

### ChannelCircuitBreaker (`gateway/ChannelCircuitBreaker.java`)
- Per-channel CLOSED/OPEN/HALF_OPEN breaker (5 consecutive failures → open 60s), fully wired into `ProviderEndpointExecutionService`.

### DoubleEntryLedgerService + LegacyLedgerPostingService (`ledger/`)
- Double-entry poster that rejects unbalanced groups, idempotent by reference. `LegacyLedgerPostingService.postPaymentEntries` gives legacy call sites (portal payin, batch payout cron) ledger parity with the v2 path.

### V2RequestSecurityService (`api/v2/V2RequestSecurityService.java`)
- Signature + timestamp + nonce + merchant verification for v2. Nonce store defaults to JDBC, `InMemoryNonceStore` only for single-instance dev.

### CallbackTaskService / CallbackClaimRepository (`callback/`)
- Claim-based callback queue: workers claim tasks to avoid double delivery; terminal failures PARK; `CallbackRetryScheduler` retries; `CallbackAdminService` requeues parked tasks; merchant callbacks signed HMAC-SHA256 with timestamp+nonce using per-merchant secrets.

### MerchantWebhookService (`webhook/`)
- Per-endpoint secrets, delivery log, terminal-delivery retention, failed-delivery replay. Admin + merchant self-service routes (merchant overloads scope updates by `merchant_id`).

### TransactionsLogController — the god class
- 6,000+ lines. Holds every legacy `/transactions/**` list endpoint, all dashboard chart endpoints, `testCheckstatusCron` + `paymentsPayCron` (both `@Scheduled` + `@SchedulerLock`, both still guarded by legacy `FileLock`), `buySms`, `saveSms`/`cancelSms`, `resolveTransaction`, spreadsheet upload validation, `recordStatementTx` (near-duplicate of `Common.recordStatementTx`), and step-up MFA for batch payouts.

### Common.java — the other god class
- 2,900+ lines of static helpers + legacy money movement (`doPayIn`, `doPayOut`, `recordStatementTx`, `updateTx`, `doHttpRequest`, JSON helpers, merchant lookups). All outbound HTTP flows through the `OutboundHttpExecutor` bridge (`RestClientOutboundHttpExecutor`). 0 raw `new Thread()`; async via `ManagedAsyncTasks`.

---

## Data Flow

### v2 collection (compat path)
1. Merchant POSTs `/api/v2/payments/collect` (signed, nonce, optional idempotency key).
2. `V2RequestSecurityService.verify()` → merchant loaded, signature/timestamp/nonce checked (JDBC store default).
3. `IdempotencyService.findExisting()` returns the prior `PaymentResult` if the same key+body was recorded.
4. `PaymentOrchestrationService.collect()` → `RiskDecisionService.authorizePayment()` → `resolveLegacyGatewayId()` (explicit channel or MSISDN prefix via DB `ChannelRoutingService` with hardcoded fallback).
5. `Common.doPayIn(tx, merchant, jdbc, txManager, skipRiskCheck=true)` executes the legacy insert → gateway call → status update; `skipRiskCheck` prevents a duplicate `risk_decisions` row.
6. `postLedgerEntries("COLLECT", …)` posts DR provider float / CR merchant collections-payable, idempotent by `payment:<tx_unique_id>`.
7. Webhook `payment.pending` queued; `PaymentResult{status:"SUBMITTED"}` returned with 202.

### v2 payout (compat path)
1–4 as above for `PAYOUT`.
5. `ensureMerchantHasAvailableBalance` (legacy snapshot balances) → `ledgerService.reserve(...)` (reserve-then-capture, audit A8) → `Common.doPayOut(..., skipRiskCheck=true)` → on success `postLedgerEntries("PAYOUT",…)` + `captureReservation`; on any `RuntimeException` → `releaseReservation` + metric increment.
6. Webhook `payout.pending` queued.

### Native v2 (adapter path)
`/api/v2/native/payments/*` → `AdapterNativePaymentService` (resolves environment via `X-CPay-Environment`, loads merchant channel credentials for the active `CUSTOM_GATEWAYSTATE`) → `GatewayExecutionService` → adapter → `ProviderEndpointExecutionService` (circuit breaker, token store, signature verify for Yo!, provider run record, translated merchant-safe error).

### Callback / status processing
1. `CallbackRetryScheduler` finds final transactions needing callbacks; `CallbackTaskService` signs + delivers with claim-based locking.
2. `TransactionsLogController.testCheckstatusCron` (ShedLock + file-lock) polls PENDING/UNDETERMINED rows (LIMIT 100 FOR UPDATE), calls the provider status check, and on SUCCESSFUL applies the full statement-write sequence via `recordStatementTx`, then queues the merchant callback. On FAILED payout it applies the reversal sequence.

### Reconciliation
Provider statements (CSV/XLSX) parsed by provider-specific parsers into `reconciliation_records` → auto-match → admin manual-match workbench (`ModuleReconciliation.tsx` + `GET /unmatched`, `candidate-transactions`, `POST /manual-match`) → review workflow → finance close.

---

## Verified Status Against the Attached Review (rev. 3 catalog)

### ✅ Verified FIXED/CLOSED on this checkout
| ID | Finding — verified in code |
|----|---------------------------|
| **L2** | **Doc-vs-code gap CLOSED.** `hooks.ts` exposes real domain hooks (`useAdminTransactions`, `useMerchantTransactions`, `useResolveTransactionMutation`, `usePortalDashboardSummary`, `useAdminDashboardCharts`, `useMerchantDashboardCharts`, `useUnmatchedReconciliationRecords`, `useCandidateTransactions`, `useAutoMatchMutation`, `useManualMatchMutation`, `useAdminAuditTrail`, `useMerchantAuditTrail`, `useAdminMerchantStatement`, `useMerchantOwnStatement`, `useRecordMerchantTransactionMutation`, `useImportStatementMutation`). `ModuleDashboard.jsx`, `ModuleTransactions.jsx`, `MerchantModuleTransactions.jsx` all consume them. |
| **L3** | `ModuleAuditTrail.tsx`, `ModuleMerchantsAccount.tsx`, `ModuleReconciliation.tsx` + merchant variants are typed function components with tests. |
| **B7** | Per-gateway transaction timeout settings (`transaction_timeout_minutes_<gateway_id>`). |
| **B9** | 0 raw `new Thread()` in `Common`; `ManagedAsyncTasks` manages async work. |
| **B10** | Callback double-apply guard in `updateTx` (`status NOT IN (SUCCESSFUL, FAILED)` + rows-affected short-circuit). |
| **C1** | DB-backed provider tokens; no plaintext token files. |
| **C4** | Outbound HTTP centralized in `RestClientOutboundHttpExecutor`, configurable timeouts, Micrometer timings. |
| **C5** | No skip-verify TLS flag; production profile locks gateway state + nonce store. |
| **C9** | `YoPaymentsCallbackVerifier` HMAC-SHA256 verified; enforced in `ProviderEndpointExecutionService`. |
| **E1/P3** | Password reset hardened (V13, single-use tokens). |
| **E2** | Merchant MFA + **step-up MFA for high-value payouts** (`requireStepUpMfaIfOverThreshold`, fails closed). |
| **E7** | SHA-256 hex correct `%064x`. |
| **E9** | `AdminMfaService.isEnabled()` fails closed. |
| **E10** | CSP/HSTS/frameOptions/referrer/contentType headers; production ships no localhost CSP carve-out. |
| **E12** | Callback signing per-merchant (`callbackSecretService.activeSecret(merchantId)`). |
| **F2/F3/F5/F7** | Flyway V1–V30; `TransactionLogArchivalService`; `OperationalDataCleanupScheduler` + retention props + absolute session cap. |
| **F6** | Dashboard/statement date queries bind `java.sql.Timestamp` via parameters. |
| **F8** | Audit chain hash (prevHash + SHA-256). |
| **G1** | ShedLock `V30__shedlock.sql` + `@SchedulerLock` on all active schedulers incl. both money crons. |
| **G4** | `server.shutdown=graceful` + `SHUTDOWN_PHASE_TIMEOUT`. |
| **G5** | No raw threads; managed async. |
| **H1** | `jul-to-slf4j` + structured JSON logging + `X-Request-ID` correlation filter. |
| **I1/I7** | Risk wired into v2 + cross-border + portal payin; read audit for sensitive reads. |
| **J8** | Dependencies current (org.json 20260719, POI 5.5.1, Bucket4j 8.0.1, shedlock 7.7.0). |
| **K1/K6** | Test growth: `CommonRecordStatementTxTest`, `CommonRiskAuthorizationTest`, `CommonIdempotentReplayTest`, `TransactionsLogControllerStepUpMfaTest`, `CrossBorderTransferServiceTest`, `FxQuoteServiceTest`, e2e/, gateway/, ledger/, compliance/ suites. |
| **M5** | Server-side `TabularExportService` wired into merchant statements + admin statement export. |
| **N4/N6** | Settlement scheduling + merchant webhook self-service (register/list/rotate/replay/delivery log). |
| **O4/O5/O7** | Impersonation (audited), SettingsRegistry, FeatureFlagService + V5. |
| **P2/P5** | Provider callback dedup guard (B10); SMS non-2xx → `REJECTED` + refund. |

### ⚠ Still open / partial (verified on this checkout) — the actionable list
| ID | Finding | Where | Effort |
|----|---------|-------|--------|
| **A1/B1** | **Legacy money path still not fully on the ledger.** `/api/v1/doMobileMoneyPayIn/Out` → `Common.doPayIn/doPayOut` still writes only snapshot balance columns; no ledger dual-write, no risk check, no reservation. (v2 compat + portal payin + batch cron *do* have ledger parity now.) | `ApiV1Controller.java`, `Common` | H/L |
| **A4** | `Transaction` + `Balance` + `DoPayGateway` charge math still `Double`. | `Model/`, `DoPayGateway.java` | H/M |
| **A7** | `recordStatementTx` near-duplicated between `Common` and `TransactionsLogController`; `recordStatementTxWithoutTransaciton` typo remains. | both | M/S |
| **A8 (partial)** | Reserve-then-capture on v2 payout + batch cron only; collection and `/api/v1` do not reserve. | `PaymentOrchestrationService.payout`, `paymentsPayCron` | M/M |
| **B2** | Transaction statuses remain free-string literals; no enum/state machine. | throughout | M/S |
| **B4 (partial)** | `ChannelRoutingRegistry` reads DB prefixes but legacy classes keep hardcoded prefix fallback. | `gateway/`, `Model/*PaymentGateway` | M/S |
| **B5 (partial)** | Breaker wired only into `ProviderEndpointExecutionService`; legacy monolith calls bypass it. | `DoPayGateway`, `Model/*PaymentGateway` | H/M |
| **B8** | No batch-level status aggregation API / partial-batch retry UI. | `batch/BatchPayoutStatusService.java` | M/M |
| **C2** | Token refresh TTL-based; 401-refresh single-flight exists for Airtel/MTN/Safaricom only. | `ProviderTokenStoreService`, gateway classes | M/M |
| **C3** | Legacy provider monoliths still live: `SafariComPaymentGateway` (~1,088 lines), `MTNMoMoPaymentGateway` (~793), Airtel variants. | `Model/` | H/L |
| **C7/C8** | Per-merchant credentials exist, but sandbox/live is still a global `CUSTOM_GATEWAYSTATE` switch. | `merchant/MerchantEnvironmentService.java` | H/M |
| **D1** | Legacy v1 money endpoints lack idempotency/nonce/signing (only `Deprecation`/`Sunset` headers). | `ApiV1Controller` | H/M |
| **D4** | Cursor pagination documented but not implemented in `api/v2`. | `api/v2` | M/M |
| **E3 (partial)** | `@PreAuthorize` on admin/recon controllers; legacy portal checks still in-controller privilege flags. | `SecurityConfig` + flat controllers | M/M |
| **E6 (partial)** | Channel credentials encrypted; **merchant RSA private keys still used in plaintext** (`merchant.getPrivate_key()` in `testCheckstatusCron`). | `TransactionsLogController`, `Common` | H/M |
| **E8** | `LoginRateLimiter` in-memory + IP-keyed (per-node). | `security/LoginRateLimiter.java` | M/S |
| **F4** | Dashboard `GROUP BY` queries still hit the transactional DB. | `TransactionsLogController` | M/M |
| **G6** | HTTP-triggered-cron pattern remains (`testCheckstatusCron`, `testSendPendingSmsCron` POST endpoints). | `TransactionsLogController` | M/S |
| **I2/I8** | No real sanctions/watchlist screening on payouts or cross-border; **cross-border has no compensation path for a failed target-leg payout** (P6). | `compliance/`, `crossborder/` | H/M |
| **I3** | KYC single-tier; no tiers, document upload, or review workflow. | `merchant/`, `compliance/KycService.java` | H/L |
| **I4** | No PII masking/deletion workflow; payer MSISDNs flow into logs. | throughout | M/M |
| **J1** | ~37 string-concatenation SQL sites remain (incl. live `merchant_id='"+…+"'` in dashboard queries; `ColumnAllowlist` covers search categories). | `TransactionsLogController`, `Common` | M/S |
| **J2** | God classes: `TransactionsLogController` (~6,000+ lines), `Common` (~2,900). | both | H/L |
| **J3** | Bespoke RowMappers duplicated across `TransactionsLogController`. | both | M/S |
| **J5/K4** | Typos persist (`recordStatementTxWithoutTransaciton`, `imploadStringArray`, `getTransaciton`, `updaed_on`). Frontend tests don't cover largest legacy modules. | `Common`, `TransactionsLogController`, `Model/` | M/M |
| **L1/L4** | `rc-easyui` references only in comments (no imports ✓), but largest modules (`ModuleSettings.jsx`, `ModuleMerchants.jsx`, `ModuleAdmins.jsx`, payments/SMS/channels variants, forgot-password) remain `.jsx`/class + hand-rolled `fetch`. | `Clientside/src/components/modules/` | H/L |
| **M2** | ~9 module files use `locale.js`; rest hardcode English. | modules | M/M |
| **M4** | No loading skeletons; some charts re-fetch on tab switches. | dashboard | M/S |
| **N1** | ⚠ **The single largest merchant product gap:** backend `checkout/PaymentLinkService` + `InvoiceService` + `/checkout/{token}` exist and work, but **no merchant UI to create/manage links and invoices**. | backend exists; frontend missing | H/L |
| **N7** | `MerchantRole.java` scaffold; owner/finance/developer/viewer enforcement not wired. | `merchant/` | M/M |
| **O1/O2** | Recon manual-match workbench **closed** on this checkout (`ModuleReconciliation.tsx`); per-provider parsers exist. |
| **P1** | Payin crash-mid-sequence repair: `LegacyLedgerRepairService` covers *successful* rows only; no general outbox. | `ledger/LegacyLedgerRepairService.java` | M/M |
| **P4** | Email verification exists (V26) but portal login is not gated on verified email. | `merchant/`, `AuthenticationController` | M/S |
| **F-1 (frontend stub)** | `Clientside/src/features/OperationsConsole.jsx` is a **bare scaffold**: channels list + two buttons (`Run Callbacks`, `Auto Match`); route `/operations` wired. This is the clearest single open implementation in the frontend — needs the real admin ops surface (alerts, parked callbacks, readiness, operating controls). | `Clientside/src/features/OperationsConsole.jsx` | H/L |

### Concerning findings verified additionally
- Runtime artifacts leak into the repo root: `check_tx.lock`, `tmp*.lock`, `hs_err_pid*.log`, `replay_pid*.log` — should be gitignored/cleaned.
- `MerchantModuleTransactions.jsx` still uses the **client-side `ReactExport` Excel shim** (`shared/export/ExcelExport`) for its Download button even though server-side export exists — a partial M5 gap.

---

## Non-Obvious Behaviors & Design Decisions

1. **The v2 "compat" engine still calls the legacy engine.** `PaymentOrchestrationService.collect/payout` deliberately route through `Common.doPayIn/doPayOut` (with `skipRiskCheck=true`), then add ledger + webhook on top. The adapter-native experiment lives under `/api/v2/native/**`. Do not "simplify" without a migration plan.
2. **Risk is double-gated by design.** v2 orchestrator runs risk itself and passes `skipRiskCheck=true`, so one transaction never produces two `risk_decisions` rows. New callers of `Common.doPayIn/doPayOut` must choose the overload deliberately.
3. **The ledger is a parallel system, not the system of record.** Snapshot balance columns remain the source of truth for the legacy path; ledger entries are posted *around* it. `LedgerOperationsScheduler` + `LegacyLedgerRepairService` reconcile the two for successful rows — the A1 long-pole.
4. **Provider sandbox behavior is deterministic and account-driven.** Without a configured endpoint URL, `ProviderEndpointExecutionService` fabricates outcomes by MSISDN suffix (`…000002` fail, `…000003` pending, `…000004` timeout, `…000005` unsupported).
5. **`ProviderErrorTranslator` is a hard merchant-safety rule.** Raw provider bodies and raw exception messages must never reach a merchant-facing field; raw detail lives only in `provider_endpoint_runs` and logs. Yo! SIGNATURE_INVALID is a distinct non-retryable code.
6. **Money-movement crons are double-locked.** ShedLock (authoritative) + legacy `FileLock` (same-JVM defense-in-depth) guard `testCheckstatusCron` and `paymentsPayCron`; the file locks were kept deliberately because mid-method early returns make removal risky.
7. **Step-up MFA fails closed.** A merchant with no MFA enrolled is blocked outright for batch payouts above `step_up_mfa_payout_threshold`.
8. **Merchant channel credentials are encrypted at rest** (`MerchantChannelCryptoService`), returned masked only, required for native v2 execution.
9. **Idempotency includes environment.** Native v2 idempotency keys append the resolved environment to the body so sandbox vs production attempts never collide.
10. **Frontend legacy-envelope handling is centralized.** `postLegacyJson` throws `SessionExpiredError` ("107"), `AccessDeniedError` ("110"), or `LegacyRequestError` (with original code); the `withRouter`/`useHistory` shim remains only for legacy class components.
11. **`OperationsConsole.jsx` is the intended home for the operations workspace** (per `features/Notes.md`) but is still the barest stub in the codebase — the clearest next implementation target.

---

## Module Reference

| File | Purpose |
|------|---------|
| `InitializrSpringbootProjectFresh/src/main/java/net/citotech/cito/PaymentOrchestrationService.java` | v2 compat engine: risk → legacy money movement → ledger → webhook |
| `.../Common.java` | Legacy god class: `doPayIn`/`doPayOut`, `recordStatementTx`, `updateTx`, HTTP bridge, JSON/merchant helpers |
| `.../TransactionsLogController.java` | Legacy god class: tx/SMS/payment lists, dashboards, money crons, step-up MFA, uploads |
| `.../DoPayGateway.java` | Legacy per-provider dispatch + Double charge math |
| `.../ApiV1Controller.java` | Live v1 endpoints |
| `.../gateway/PaymentChannelAdapter.java` + `PaymentChannelRegistry.java` | Adapter contract + discovery |
| `.../gateway/ProviderEndpointExecutionService.java` | Endpoint execution, sandbox scenarios, circuit breaker, Yo! verify, run records, error translation |
| `.../gateway/ProviderErrorTranslator.java` | Provider→merchant-safe error taxonomy |
| `.../gateway/RestClientOutboundHttpExecutor.java` | Spring RestClient bridge replacing hand-rolled HttpURLConnection |
| `.../gateway/ChannelRoutingRegistry.java` + `ChannelRoutingService.java` | DB-backed MSISDN prefix→gateway routing with fallback |
| `.../security/V2RequestSecurityService.java` | v2 signing/nonce/idempotency verification |
| `.../security/MerchantMfaService.java` + `AdminMfaService.java` | TOTP MFA (merchant step-up + admin) |
| `.../ledger/DoubleEntryLedgerService.java` | Idempotent double-entry posting |
| `.../ledger/LegacyLedgerPostingService.java` | Ledger parity for legacy call sites |
| `.../ledger/LegacyLedgerRepairService.java` | Backfill sweep for successful rows missing ledger postings |
| `.../callback/CallbackTaskService.java` | Claim-based signed merchant callbacks |
| `.../webhook/MerchantWebhookService.java` | Webhook endpoints/deliveries/replay/rotation |
| `.../reconciliation/` | 5 provider parsers + import/validate/match/review/finance-close |
| `.../checkout/PaymentLinkService.java` + `InvoiceService.java` | Payment links + invoices (backend done; merchant UI missing) |
| `.../crossborder/CrossBorderTransferService.java` + `FxQuoteService.java` | Transfer intents, corridor limits, atomic quote claiming |
| `.../scheduler/` | All ShedLock-guarded scheduled jobs |
| `.../admin/ReadinessDashboardService.java` | Platform + per-merchant go-live readiness |
| `.../export/TabularExportService.java` | Reusable CSV/XLSX export |
| `.../config/SecurityConfig.java` | CSP/HSTS/CORS/CSRF, `@PreAuthorize`, admin+actuator users |
| `Clientside/src/shared/api/hooks.ts` | TanStack domain hooks (L2 closed) |
| `Clientside/src/components/modules/ModuleDashboard.jsx` | Admin operations dashboard (converted to hooks) |
| `Clientside/src/components/modules/ModuleTransactions.jsx` / `merchant/MerchantModuleTransactions.jsx` | Admin/merchant tx lists (converted to hooks) |
| `Clientside/src/components/modules/ModuleReconciliation.tsx` | Recon manual-match workbench |
| `Clientside/src/features/OperationsConsole.jsx` | ⚠ Stub: the open implementation target |

---

## Suggested Reading Order

1. `Readme.md` + `Claude.md` — system intent, commands, sensitive areas.
2. `PaymentOrchestrationService.java` — the clean v2 seam: risk → legacy engine → ledger → webhook; explains how old and new coexist.
3. `gateway/ProviderEndpointExecutionService.java` — adapter-native execution, sandbox scenarios, circuit breaker, Yo! verification, error translation.
4. `TransactionsLogController.java` (skim `testCheckstatusCron`, `paymentsPayCron`, `requireStepUpMfaIfOverThreshold`) — legacy money crons, ShedLock + FileLock, statement-write sequences.
5. `ledger/DoubleEntryLedgerService.java` + `LegacyLedgerPostingService.java` — the parallel-ledger model and the A1 long-pole.
6. `Clientside/src/shared/api/hooks.ts` — the frontend data-access pattern all new module work should follow.
7. `Docs/Architecture/Overview.md` + `Docs/Process-flow-controls.md` — cross-system flows and the control map.
8. The attached review catalog — the authoritative open-items list; use the Verified vs. Open tables in this report as the working status.

---

## How to Proceed With "Complete All Open Implementations"

This is **Explore Mode** — I can analyze and document but cannot modify the tree. To implement the open items, switch to **Act Mode** (mode selector at the bottom of the chat). Recommended implementation order based on risk/leverage:

1. **Frontend quick wins (M/S):** replace `OperationsConsole.jsx` stub with the real ops surface (reuse `hooks.ts` + `ModuleDashboard` patterns); remove runtime lock/crash files from repo root; move `MerchantModuleTransactions` Download onto server-side export.
2. **Legacy ledger unification (H/L, A1/B1):** dual-write `/api/v1` path through `LegacyLedgerPostingService` + risk check; then decompose `Common.doPayIn/doPayOut`.
3. **Merchant product surface (H/L, N1):** expose payment-link + invoice management UI (backend is done) and the webhook log/replay/rotation UI.
4. **Compliance (H/M–H/L, I2/I8/I3):** sanctions-screening hook + corridor-limit enforcement on cross-border payouts; KYC tiers.
5. **God-class decomposition (H/L, J2/C3):** move per-provider logic out of `Model/*PaymentGateway` and out of `TransactionsLogController` into services behind the adapter boundary.
6. Each change must follow `Contributing.md` (new Flyway migration for any schema change, `ProviderErrorTranslator` for provider text, tests for money paths, `mvn verify` + `npm run typecheck && npm test && npm run build` gates).

The full report has been saved to **`project_info__1.md`** in the project root.