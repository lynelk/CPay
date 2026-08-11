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
InitializrSpringbootProjectFresh/   Active Spring Boot 4.1 backend (Java 21) — build/run/test from here
clientside/                         React 18 + Vite 8 admin/merchant portal (TypeScript)
Integrations/Citoconnect/           JS reference client / integration bundle
Docs/                                Architecture, API contracts, ADRs, runbooks, readiness docs
sdk/, deployment/, setup/            SDK assets, deployment scripts, local setup helpers
```

`InitializrSpringbootProject/` (without "Fresh") is an empty legacy scaffold — not in use, ignore it.

## Commands

### Backend (`InitializrSpringbootProjectFresh/`)

```bash
mvn clean package                 # build
mvn test                          # unit tests (Docker-gated tests excluded by default)
mvn verify                        # tests + verification bindings
mvn test -Dtest=ClassName                       # single test class
mvn test -Dtest=ClassName#methodName             # single test method
java -jar target/cito-fresh-0.0.1-SNAPSHOT.jar   # run the built jar
```

Runs on port `8081` by default (`HTTP_PORT`). Requires a MySQL 8 database and a `.env` populated from
`.env.example` (or exported env vars) — see `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and other keys
documented in the root `Readme.md`.

Tests tagged `"docker"` (Testcontainers-backed DB integration tests and the `HealthEndpointE2ETest`
end-to-end suite) are excluded from the default `mvn test`/`mvn verify` run via the
`docker.tests.excludedGroups` property, so an unavailable Docker daemon never breaks the build. Run
them explicitly in a Docker-capable environment with `mvn test -Ddocker.tests.excludedGroups=`.
WireMock-based provider mocking tests need no Docker and run as part of the normal suite. A separate,
fully opt-in Gatling load-testing toolchain lives under `src/test/java/.../loadtest/` — it has no
lifecycle binding, so it never runs as a side effect of `mvn test`/`mvn verify`/`mvn package`; invoke
it explicitly with `mvn gatling:test -Dgatling.simulationClass=net.citotech.cito.loadtest.<Simulation>`.
`mvn verify` also runs a Spotless (`google-java-format`, AOSP style) formatting check, ratcheted
against `origin/main` (`ratchetFrom`) so only files you've actually touched are enforced — existing
legacy files are grandfathered in. Run `mvn spotless:apply` to auto-fix a flagged file.

### Frontend (`clientside/`)

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
migrations live under `InitializrSpringbootProjectFresh/src/main/resources/db/migration`
(`V1__...` .. current head) and are the canonical migration path — the legacy XML DB-change runner is
gated behind `CPAY_LEGACY_DBCHANGES_ENABLED` and should only be used to rebuild an old, unreconciled
local database.

## Backend architecture

Base package: `net.citotech.cito`.

- **Legacy layer (root package, flat)** — `ApiV1Controller`, `DoPayGateway`, `PaymentOrchestrationService`,
  `MerchantsController`, `SettingsController`, etc., plus request/response models under `Model/`
  (`Transaction`, `Payment`, `Merchant`, `*PaymentGateway` classes per provider). This is the original
  v1 implementation — provider logic here is hardcoded per-provider rather than adapter-based. Keep
  `/api/v1` behavior stable; changes here need a migration plan. `Common.doPayIn`/`doPayOut` now run
  risk/fraud authorization (`RiskDecisionRegistry`) and post to the double-entry ledger
  (`LegacyLedgerPostingService`) directly, matching what `PaymentOrchestrationService` already did
  for v2 — callers that already run their own risk check (like `PaymentOrchestrationService`) must
  use the `skipRiskCheck` overload to avoid a duplicate `risk_decisions` row per transaction.
- **`gateway/`** — the adapter pattern that new/v2 work should use. Each provider implements
  `PaymentChannelAdapter` (see `Docs/Gateway-adapter-guide.md`) and is looked up through
  `PaymentChannelRegistry`; `GatewayExecutionService` executes the selected adapter, loading
  merchant-specific channel credentials for the active `CUSTOM_GATEWAYSTATE` (`SANDBOX`/`PRODUCTION`).
  Adapters: `MtnMomoAdapter`, `AirtelMoneyAdapter`, `AirtelOpenApiAdapter`, `SafaricomMpesaAdapter`,
  `YoPaymentsAdapter`, plus `LegacyGatewayAdapter` wrapping old provider classes for the transition
  period. `PaymentChannelAdapter.verifyCallback(...)` is a default no-op capability adapters can
  override to verify provider responses/callbacks are authentic (see `YoPaymentsCallbackVerifier`
  for the HMAC-based reference implementation, enforced in `ProviderEndpointExecutionService`).
  `ProviderConversationReferenceStoreService`/`Registry` map a provider's async callback
  correlation id (e.g. Safaricom's `ConversationID`) back to CPay's own transaction reference via
  the database — do not reintroduce a local-disk equivalent, it doesn't survive multiple instances.
  `AirtelMoneyOpenApiPaymentGateway`, `MTNMoMoPaymentGateway`, and `SafariComPaymentGateway` each
  force a fresh token and retry once on an unexpected 401 (a token our own TTL-based check still
  considered valid but the provider rejected), with a static, per-`gatewayId+segment+environment`
  `ReentrantLock` table so concurrent 401s on the same provider/segment only trigger one real
  token-refresh call rather than each caller independently hammering the provider's token endpoint.
  Legacy outbound HTTP now goes through `RestClientOutboundHttpExecutor`, so do not add new direct
  `HttpURLConnection` provider calls.
  `ProviderErrorTranslator` maps a raw provider HTTP failure (or a caught internal exception) to a
  merchant-safe `(stableCode, category, retryable, message)` result reusing `ErrorCatalog`'s shape —
  never hand `gwResponse.setMessage(rawProviderResponseBody)` or a raw exception message straight to
  a merchant-facing field; wired into `AirtelMoneyOpenApiPaymentGateway`/`ProviderEndpointExecutionService`
  today, with the same raw-passthrough pattern still present in the other legacy provider classes as
  a documented follow-up.
- **`api/v2/`** — v2 controllers/services: `PaymentsV2Controller` (compat `/api/v2/payments/*`),
  `NativePaymentsV2Controller` (adapter-backed `/api/v2/native/payments/*`), `V2RequestSecurityService`
  (request signing/nonce/idempotency enforcement), `IdempotencyService`, `PaymentStatusService`,
  `AccountValidationService`, `MerchantStatementExportService`. DTOs in `api/v2/dto/`.
- **`security/`** — request signing, nonce replay protection (in-memory or JDBC via
  `CPAY_SECURITY_NONCE_STORE`), admin MFA/TOTP, session/auth filters. `MerchantMfaService` also
  backs a step-up MFA check (`TransactionsLogController.requireStepUpMfaIfOverThreshold`) that
  blocks a merchant payout batch above a configurable amount threshold unless a fresh TOTP code is
  supplied — fails closed if the merchant has never enabled MFA at all.
- **`callback/`** — provider and merchant callback processing; uses claim-based task assignment so
  multiple workers don't double-deliver.
- **`webhook/`** — `MerchantWebhookService` backs both the admin-only `/api/v2/admin/webhooks/**`
  routes and the merchant self-service equivalents under
  `/api/v2/merchant-self-service/webhooks/**` (register, list, rotate secret, list deliveries,
  replay a failed delivery) — the merchant-scoped `rotateSecret(merchantId, endpointId)`/
  `replay(merchantId, deliveryId)` overloads scope their `UPDATE` to the caller's own `merchant_id`
  so one merchant can never rotate or replay another's webhook; do not call the unscoped
  single-arg overloads from a merchant-facing code path. `WebhookEventCatalog.register(type,
  version, description, jsonSchema)` takes an explicit schema per event type (ADR 0006) — the 8
  transactional types (`payment.*`/`payout.*`/`refund.*`) share one `TRANSACTIONAL_ENVELOPE_SCHEMA`
  constant requiring `transactionId`/`amount`/`currency`; `invoice.issued` (billing's first
  non-transactional type, wired into `InvoiceService.send()`) registers its own
  `INVOICE_ENVELOPE_SCHEMA` instead, requiring `invoiceId` in place of `transactionId`.
  `MerchantWebhookService.enqueue(...)` never assumes a payload has the transactional fields —
  only `WebhookEventCatalog.lookup(eventType)` must succeed.
- **`export/`** — `TabularExportService` is the one reusable place to render tabular data as CSV or
  XLSX (streaming `SXSSFWorkbook`, disposed correctly); callers remain responsible for
  bounding/paginating the underlying query. Wired into merchant statement export
  (`MerchantSelfServiceController`, `PaymentsV2Controller`) via a `?format=csv|xlsx` choice —
  prefer this over hand-building a CSV string or a client-side spreadsheet shim for any new export
  surface.
- **`reconciliation/`** — statement matching, settlement scheduling, finance daily-close support,
  and provider-specific CSV/XLSX statement parsers built on the shared tabular parser.
  `ReconController`/`StatementCheckController` carry `@PreAuthorize("hasRole('ADMIN')")` like the
  other reconciliation controllers; `GET /api/v2/admin/reconciliation/candidate-transactions` backs
  the admin manual-match workbench's transaction search
  (`clientside/src/components/modules/ModuleReconciliation.tsx`), which pairs unmatched provider
  statement rows (`GET /unmatched`) with a CPay transaction via `POST /manual-match`. Both
  `ReconController`/`StatementCheckController` file uploads go through
  `net.citotech.cito.upload.SpreadsheetUploadValidator` (shared size/extension/content-type
  checks) before the file reaches the parser. `FinanceWorkflowService`/`SettlementOpsService` now
  require maker-checker approval before a reconciliation daily close or settlement batch close
  takes effect — `POST .../close` opens the close request, `POST .../close/approve` or
  `.../close/reject` (a different admin than the opener) finalizes it, on `ReconFinanceController`
  (`/api/v2/admin/recon-finance`) and `SettlementOpsController`
  (`/api/v2/admin/reconciliation/settlements`).
- **`ledger/`** — double-entry ledger service (`DoubleEntryLedgerServiceTest` covers invariants).
  `reverse(originalTransactionReference, newTransactionReference, reason)` posts a mirror-image
  balanced group under a new reference (delegates to `post()` internally for idempotency/balance
  reuse) — the only way this ledger corrects a posting, per `Docs/Money-ledger-and-orchestration-
  roadmap.md`'s "propose matched corrections, never mutate" rule. `post()`/`reverse()` also reject
  a posting whose currency has an active `ledger_period_locks` (`V46`) row covering *today* —
  fail-open by construction (an empty lock table always allows), since `post()` has 9 real
  production dependents and an accidental lock must never silently halt payment processing
  platform-wide; the check only ever blocks today's postings, never a retroactive historical
  period, since neither method takes a backdating parameter.
- **`merchant/`** — merchant self-service signup, channel configuration.
  `MerchantChannelCryptoService` (AES-256-GCM) encrypts channel credentials and, via the
  `MerchantKeyCryptoRegistry` static bridge, the legacy `hmac_secret` field for static-utility
  `Common` code; `MerchantKeyEncryptionService` is a separate, dedicated encryption path
  specifically for merchant RSA private keys, keyed by `CPAY_KEY_ENCRYPTION_KEY` (falling back to
  `MERCHANT_CHANNEL_ENCRYPTION_KEY` for existing installs so a fresh key isn't required
  immediately), with `MerchantKeyReencryptionService` running as a background sweep that migrates
  legacy plaintext/shared-key rows onto the dedicated key over time.
- **`batch/`** — `BatchPayoutController` (`/api/v2/merchant-self-service/batches/{batchId}`,
  `.../retry-failed`) gives a merchant self-service visibility and retry over their own batch
  payout status, session-gated like the other merchant self-service routes.
- **`payout/`** — `PayoutConfigService`/`PayoutConfigController` (`/api/v2/admin/payout-controls/**`,
  gated by `payout-controls-config`) give operators self-service configuration of the
  `payout_controls` rows `PayoutControlService.evaluate` reads, so a saved limit is enforceable
  immediately on the v2 payout path. `PayoutControlService`/`PayoutApprovalController`
  (`/api/v2/admin/payout-approvals`) hold a payout above a configurable threshold in a maker-checker
  queue instead of executing it immediately: `GET` lists pending entries, `POST .../approve`,
  `.../reject`, and `.../cancel` resolve one, each by an admin other than whoever's action queued it.
- **`efris/`** — `EfrisReceiptService`/`EfrisReceiptScheduler` queue an e-receipt record after a
  successful Ugandan pay-in and sweep it on `CPAY_EFRIS_DELIVER_FIXED_DELAY_MS` (on/off and the
  actual endpoint URL are runtime settings, `cpay.efris.enabled`/`cpay.efris.endpoint`, so
  operators can configure EFRIS without a redeploy). This is an honest extension point, not a
  certified integration — it logs "would issue e-receipt for tx X" until real EFRIS/URA business
  registration and API credentials exist; do not treat pay-ins as EFRIS-compliant based on this
  code alone.
- **`reporting/`** — `RegulatorReportingService`/`RegulatorReportingController`
  (`/api/v2/admin/regulator/daily-cash-flow[/csv]`, `/reports`, `/pii-inventory`) generate a
  transaction/FX summary off the ledger for BoU-style periodic reporting. The exact regulator
  schema/frequency is not settled anywhere in this repo — this is a generator compliance can adapt,
  not a certified regulatory submission; confirm the real format before ever submitting a report
  produced by this code to a regulator.
- **`admin/`** — `ReadinessDashboardService.summary()` is the platform-wide go-live readiness view
  (provider sandbox/statement-validation/certification, callback secrets, operations alerts,
  compliance cases, etc.); `merchantSummary(merchantId)` is a separate, genuinely per-merchant
  checklist scoped to that merchant's configured channels (`merchant_channel_credentials`),
  callback secret, and `entity_type='MERCHANT'` compliance records — platform-wide-only checks
  (operations alerts, daily close, admin audit events) are intentionally excluded from the
  per-merchant view since they carry no merchant reference at all. `FeatureRegistryService`
  (backed by the `merchant_feature_flags` table, V36) resolves a feature for a merchant as the
  global `feature_flags` default overridden by a per-merchant row, always through
  `TenantScopeGuard`; the `/api/v2/admin/feature-registry/**` surface lets operators roll a
  feature out or back per merchant without a deploy. `AdminMerchantStatementController`
  (`GET /api/v2/admin/merchants/{merchantNumber}/statements`) is the session-authenticated admin
  counterpart to `MerchantStatementExportService#exportForPortal` (merchant self-service) and
  `PaymentsV2Controller#statements` (v2-signed API) — it calls the new `#exportForAdmin` method,
  which looks up the merchant server-side rather than resolving it from a caller's own session or
  signed request.
- **`identity/`** — GnuGrid NIN identity-verification pilot (S5, V37), gated by the
  `identity-gnugrid` feature flag: consent-mandated requests, PII-safe storage (NIN/full-name/
  MSISDN stored only as SHA-256 hashes plus masks), `IdentityVerificationService`/`Controller`
  under `/api/v2/admin/identity/**`, and a provider callback endpoint.
- **`balance/`** — `FloatBalanceReader` + `BalanceMonitoringService`/`Controller` expose the S5
  balance-monitoring view (`/api/v2/admin/balance-monitoring/overview`, gated by the
  `balance-monitoring` flag) combining current gateway float balances, treasury positions, and the
  latest nightly float snapshots.
- **`billing/`** — the billing engine (Phase 0-3 complete, `Docs/Adr/0003`-`0006`; merchant-scoped
  1:1 for now, schema-ready for a future multi-tenant BaaS model). `tenancy/` — `BillingTenantResolver`
  resolves `merchantId → billing_tenant_id` (throws if unmapped; every merchant is backfilled a
  tenant by `V38`). `usage/` — `UsageGatewayService.recordUsage(...)` writes an idempotent
  `billing_usage_events` row (dedup by `idempotency_key`, safe under concurrent retries);
  `UsageEventOutboxHandler` is the first real outbox consumer, turning a
  `PAYMENT_COLLECTION_SUBMITTED`/`PAYMENT_PAYOUT_SUBMITTED` outbox entry into one. `outbox/` —
  a classic transactional outbox (ADR 0005): `OutboxWriter.write(...)` inserts a `billing_outbox`
  row from inside the caller's own code path (no `@Transactional` of its own — joins whatever
  transaction is active, or auto-commits if none); `OutboxRelay` is a ShedLock-guarded `@Scheduled`
  poller (matching `scheduler/LedgerOperationsScheduler`'s pattern) that dispatches each row to
  **every** registered `OutboxEventHandler` whose `supports(eventType)` matches — not just the
  first, since independent side effects (usage recording, charge rating, cost rating) all need to
  run off the same entry — with exponential backoff and a poison-message-to-`FAILED` path; three
  handlers are wired today: `usage/UsageEventOutboxHandler`, `pricing/RatedChargeOutboxHandler`,
  `cost/ProviderCostOutboxHandler`. `integration/cpay/` — `PaymentUsageOutboxHook` is the one hook
  point in **both** `PaymentOrchestrationService.collect()` (after `Common.doPayIn` succeeds) and
  `.payout()` (after the ledger reservation is captured), gated by the `billing-usage-outbox`
  feature flag (global default off, per-merchant override via `merchant_feature_flags`) and never
  throws — mirrors `queueWebhook`'s own "payment submission remains authoritative" convention in
  the same class. `LegacyFeeSchedulePriceAdapter` is a read-only projection of
  `fees/FeeScheduleService` into the `billing_price_book_versions`/`billing_price_components`
  (`V43`) shape for future shadow-price comparison — flags a `TIER` schedule as not-yet-computable
  rather than reusing `FeeSchedule.apply()`'s flat-fallback (a known gap, not a real tier
  calculation); nothing consumes this specific projection yet. `pricing/` — a real rating engine:
  `TierCalculator` does genuine graduated/marginal tier math (each band's rate applies only to its
  own slice of the base amount, unlike legacy `FeeSchedule.apply()`, which never implemented
  `TIER`); `RatingEngine` folds a resolved price book's `FLAT`/`PERCENTAGE`/`TIER`/`MINIMUM`/
  `MAXIMUM` components (`billing_price_components`, `V43`) into one rated charge, `HALF_UP` scale
  2; `PriceResolver` mirrors `FeeScheduleService`'s tenant-override-then-global lookup;
  `PriceBookAuthoringService`/`PriceBookAdminController`
  (`/api/v2/admin/billing/price-books`, `hasRole('ADMIN')`) let operators publish a new price-book
  version without SQL, closing the previous version's `effective_to` rather than deleting it;
  `RatedChargeOutboxHandler` computes and persists a `billing_rated_charges` (`V44`) row async via
  the outbox — a no-op, not a failure, when no price book is configured yet for that
  tenant/service/meter; `ChargeShadowComparisonService` compares `RatingEngine`'s rated amount
  against the legacy `DoPayGateway` charge already on `merchant_transactions_log.charges` for
  observability only — making `RatingEngine` authoritative instead of the legacy computation is a
  deliberately separate, not-yet-scheduled slice (dual-charging risk). `cost/` —
  `ProviderCostOutboxHandler` computes what CPay pays the provider by reusing the **same**
  `RatingEngine`/`RatedChargeRepository` against `charge_type='PROVIDER_COST'` instead of a
  parallel schema — cost and price are independently effective-dated for free since they're just
  different rows in the same price-book tables. `metering/` — `MeterAggregationService` does MVP
  `COUNT`/`SUM` aggregation over `billing_usage_events` with an optional JSON dimension filter,
  driven by the meter's own `billing_meters.aggregation_type` (`V39`) rather than a
  caller-supplied flag. `reconciliation/` — `UsagePaymentReconciliationService` compares
  `merchant_transactions_log` against `billing_usage_events` for a trailing window (the Phase 1
  exit-criterion artifact); with the outbox handlers now wired, a merchant with the
  `billing-usage-outbox` flag on reconciles cleanly once the relay runs — proved end to end by
  `PaymentPipelineReconciliationTestcontainersTest`. `integration/cpay/BillingLedgerAccountTemplateService`
  is the only place in the billing module allowed to call `DoubleEntryLedgerService.post()`
  directly (ADR 0004) — implements 7 of the spec's 8 accounting patterns (postpaid charge with
  optional tax, provider cost accrual, prepaid top-up/consumption, invoice payment, credit note,
  BaaS platform fee) as pre-built balanced `LedgerEntryCommand` lists against a new
  `billing:{tenantId}:{ccy}:{purpose}` account-code namespace (owner_type `BILLING_TENANT`), then
  immediately records a `billing_ledger_links` (`V45`) row via `BillingLedgerLinkWriter` tagged
  with one of five link types (`CHARGE`/`INVOICE`/`PAYMENT`/`COST`/`REVERSAL`) so a ledger
  transaction traces back to its billing artifact without ever mutating `ledger_transactions`/
  `ledger_entries` directly. `invoicing/` — the periodic billing-statement domain
  (`billing_invoices`/`billing_invoice_lines`, `V47`), distinct from `checkout.InvoiceService`'s
  one-off request-to-pay invoices: `BillingInvoiceService.createDraft`/`stageCharges` (idempotent —
  `billing_invoice_lines.billing_rated_charge_id` is unique, so re-staging only picks up what's
  still unstaged) build a `DRAFT` invoice from `CUSTOMER_CHARGE` rated charges (never
  `PROVIDER_COST`); `finalizeInvoice` requires the completeness gate `APPROVED`, posts the invoice
  total via `BillingLedgerAccountTemplateService.postCustomerCharge` (invoice number as the
  idempotent charge reference — a concurrent double-finalize resolves to the same ledger
  transaction id and the loser's `status='DRAFT'`-guarded update throws, rolling back its whole
  transaction), then flips the row to `FINALIZED` — immutable from then on, since `stageCharges`
  rejects any non-`DRAFT` invoice. `reconciliation/BillingCompletenessGateService` is the maker-
  checker gate a `DRAFT` invoice must pass to finalize (`billing_completeness_gates`, `V49`),
  mirroring `FinanceWorkflowService`'s `requested_by<>approved_by` shape, with one deliberate
  difference: a `FAIL` completeness result doesn't block submission, but a checker approving a
  `FAIL` gate must supply a non-blank waiver reason. `billing_credit_notes`/
  `billing_payment_allocations` (`V48`) are schema-only so far — correcting/settling a finalized
  invoice without ever mutating it — with no service wired yet. `export/BillingTraceChainService`
  is read-only trace-chain infra (no controller, no FOCUS-style export format — that's a much
  later phase): `traceBySourceReference`/`traceByInvoice` join usage event → rated charge →
  invoice line → invoice → ledger entry in one query; one invoice finalize posts one ledger
  transaction with 2-3 entries, so a result legitimately fans out to one row per ledger entry, not
  one row per charge.
- **`compliance/`** — `RiskDecisionService` now also enforces a KYC-tier-aware cap
  (`compliance_profiles.tier` for `entity_type='MERCHANT'`) and a payer-velocity rule capping how
  often the same payer identifier can transact in a rolling window, alongside the existing blocklist
  and flat single-transaction/daily-merchant caps. The remaining operational packages are
  **`checkout/`** (payment links/hosted checkout), **`scheduler/`** (timeout scans, cleanup jobs),
  **`metrics/`**, **`portal/`**,
  **`config/`** (security/CORS/production-safety config, legacy deprecation header filter, and
  `SchedulerLockConfig` — the ShedLock `LockProvider` backing every active `@Scheduled` job. Keep
  any new scheduled job annotated with `@SchedulerLock`; the older local file locks are only
  secondary, same-instance safeguards on legacy cron code), **`repository/`**.

Config: `application.properties` (defaults, `SANDBOX` gateway state) and
`application-production.properties` (production profile guardrails — gateway mode and SSL verification
are locked down here). Most values are externalized as `${ENV_VAR:default}` — see the root `Readme.md`
"Key environment variables" table for the full list (DB, mail, actuator, admin API, callback signing,
merchant channel encryption key, etc.).

Operational cleanup is handled by `OperationalDataCleanupScheduler`; keep `Docs/Data-retention.md`,
`Installation.md`, `Readme.md`, and `compose.yaml` in sync when adding a new cleanup property. Current
cleanup coverage includes `api_rate_limits`, stale callback claims, `password_reset_tokens`, terminal
merchant webhook deliveries, completed callback tasks/signatures, provider run logs, and sessions past
their absolute lifetime. Money movement, statement, reconciliation, and audit-evidence tables remain
append-oriented unless finance/compliance explicitly approve archival.

`LedgerOperationsScheduler` also runs `LegacyLedgerRepairService` before daily trial balance work,
backfilling successful legacy pay-in/pay-out rows that are missing their idempotent
`payment:<tx_unique_id>` normalized ledger transaction. Keep the sweep capped by
`CPAY_LEDGER_REPAIR_LIMIT` and do not broaden it to failed or non-terminal rows without an explicit
accounting decision.

Schema snapshots live under `Docs/Schema/snapshots/`. Flyway is currently at `V60` (through
`V60__communication_billing_meters.sql`); do not mark a snapshot as a real release snapshot unless it was
generated from a freshly migrated database with the documented `mysqldump --no-data` command. The
committed snapshots still reflect `V30`/`V49` and must be regenerated before the next release tag.

## Frontend architecture

- `src/App.tsx` / `src/Routers.tsx` — app shell and routing (React Router v7 native hooks for new code;
  a `withRouter`/`useHistory` compat shim exists at `src/shared/router/compat.tsx` for legacy class
  components only — do not use it in new code).
- `src/shared/api/httpClient.ts` — all new HTTP calls go through this; `src/shared/api/hooks.ts` for
  TanStack Query-based server state (dashboard summaries, chart series, transaction lists/mutations,
  the reconciliation workbench's unmatched-records/candidate-transaction search/match mutations,
  audit-trail/merchant-account-statement queries and mutations, and the merchant webhook manager's
  endpoint/delivery queries and register/rotate/replay mutations). Consumed today by `ModuleDashboard.jsx`
  (a class-to-function conversion was required — hooks can't be used in class components),
  `ModuleTransactions.jsx`/`MerchantModuleTransactions.jsx`, `ModuleReconciliation.tsx`,
  `ModuleAuditTrail.tsx`/`MerchantModuleAuditTrail.tsx`/`ModuleMerchantsAccount.tsx`/
  `MerchantModuleMerchantsAccount.tsx`, and the merchant portal webhook manager
  (`MerchantModuleWebhooks.tsx` with its Endpoints/Deliveries panels at
  `clientside/src/components/modules/merchant/`); most other modules still hand-roll `fetch`/`useState`
  and are good candidates for the same migration (see `clientside/Migration.md`'s follow-ups).
  `LegacyRequestError` (carrying the original `code`) is thrown by `postLegacyJson` for any
  non-`"000"` legacy response code other than `"107"`/`"110"`.
- `src/shared/useAuth.ts` — centralized read of the logged-in admin/merchant principal out of
  `localStorage` (`useAuth('admin' | 'merchant')`), with a typed `hasPrivilege()` helper. Does not
  perform authentication itself; mirrors whatever `Login.tsx`/`LoginMerchant.tsx` last wrote. Also
  exports a plain (non-hook) `readStoredUser(portal)` for the class components that can't call a hook
  directly — prefer the full `useAuth()` hook in new/converted function components, and
  `readStoredUser()` only to centralize the read in a class component you aren't otherwise converting.
- `src/shared/csrfFetch.ts` — CSRF-aware fetch wrapper (backend CSRF token comes from `GET /auth/csrf`).
- `src/shared/config.ts` — `API_BASE` / `apiUrl()`; set via `VITE_API_BASE` (defaults to same-origin, so
  the dev proxy handles it). This is now the only base-URL helper — the legacy `Common.js` `base_url`
  field (always `""`) was removed after every call site was migrated onto `apiUrl()`.
- `src/ui/` — CPay iOS-style component primitives (design tokens in `src/index.css`); `rc-easyui` has
  been fully removed from the runtime dependency set (remaining source mentions are historical
  "replaces rc-easyui X" comments, not imports). `Table.tsx` renders a stacked key/value card per row
  instead of the table below a ~640px viewport via a `matchMedia`-backed hook, so every table-based
  module gets a mobile layout for free.
- `src/features/` — feature modules (in progress; see `Notes.md` there).
- Auth/session model: cookie-based (`credentials: 'include'`) against the Spring Boot backend, not
  token-based.

## Sensitive areas (per `Contributing.md`)

Treat as high-risk and require careful review/testing: request signing, callback signing, merchant
key handling, admin authentication, provider channel setup values, database access values, CORS/CSRF
config, Spring Session JDBC config, actuator access, audit logs, operating-control records, finance
approval/posting, and callback worker claiming. Do not bypass request signing, nonce checks,
idempotency, CSRF protection, merchant validation, channel readiness checks, or operating-control
records. Avoid floating-point arithmetic for money. Do not reintroduce a global TLS/SSL
skip-verification path; use trusted local certificates or provider sandbox endpoints.

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
