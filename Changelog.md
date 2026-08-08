# Changelog

All notable CPay changes should be recorded here before a release or production deployment.

Use calendar-versioned tags such as `2026.07.16` for releases. Keep entries grouped by Added, Changed, Fixed, Security, and Operational.

## Unreleased

### Added

- Added provider-specific statement parsers for MTN, Airtel, Airtel OpenAPI, Safaricom, and Yo! Payments statement imports with shared CSV/XLSX parsing support.
- Added a ledger-refreshed channel-balance read model so dashboard balance views no longer need to recompute every card directly from statement rows.
- Added request correlation IDs and structured JSON console logging for backend observability.
- Added bounded operational cleanup for `api_rate_limits`, stale callback task claims, password reset tokens, terminal webhook deliveries, completed callback tasks/signatures, provider run logs, and sessions past their absolute lifetime.
- Added documentation for backend architecture, schema snapshots, alert runbooks, ADRs, retention, and observability.
- Added API versioning, error catalog, pagination, webhook event, SDK, provider, security, testing, ledger, process-flow, and reliability docs.
- Added Docker Compose onboarding with MySQL and backend services.
- Added production profile guardrails for gateway mode and SSL verification.
- Added deprecation headers for legacy `/api/v1/**` and `/api/do*` routes.
- Added signed v2 merchant account-validation and statement-export endpoints with read auditing.
- Added merchant-facing feature and compliance-risk control roadmaps.
- Added risk/fraud authorization to the legacy pay-in/pay-out path, matching the coverage the v2 orchestration path already had.
- Added double-entry ledger posting for legacy pay-in, pay-out, and batch-payout call sites, matching the v2 path's ledger coverage.
- Added a step-up MFA requirement for merchant payout batches over a configurable amount threshold.
- Added forced token refresh and a single retry when a provider rejects an Airtel OpenAPI request with an unexpected 401.
- Added response signature verification for Yo! Payments provider responses before trusting them.
- Added method-level authorization (`@PreAuthorize`) across admin controllers as defense-in-depth alongside existing path-based access rules.
- Added distributed locking (ShedLock, database-backed) for scheduled jobs so multi-instance/HA deployments cannot process the same batch, timeout scan, settlement sweep, cleanup, reporting aggregate, webhook delivery, ledger, float-alert, invoice-expiry, or provider-routing refresh twice.
- Added a database-backed store for Safaricom payout-callback conversation-reference lookups, replacing a local, per-instance plaintext file.
- Added error catalog codes `148` (risk-declined) and `149` (step-up MFA required).
- Added Testcontainers-based database integration tests, WireMock-based provider API mocking tests, and a first end-to-end test suite (opt-in, Docker-gated — see `Contributing.md`).
- Added a Gatling load-testing toolchain with baseline and signed-request simulations (opt-in, not part of the default build).
- Added a shared `useAuth` hook and TanStack Query hooks (`src/shared/api/hooks.ts`) for admin/merchant portal server state.
- Added merchant self-service endpoints for webhook secret rotation, failed-delivery replay, and a delivery log — previously only available to admins on the merchant's behalf.
- Added a Provider→CPay→merchant-safe error taxonomy so raw provider response text can no longer reach a merchant-facing response; internal exceptions now get a stable, non-sensitive reason code instead of collapsing into a generic error with the cause only in logs.
- Added a distinct, non-retryable translation for provider responses that fail CPay's own signature verification, so that case is no longer indistinguishable from an ordinary provider decline.
- Added forced token refresh, retry, and single-flight locking on 401 for the MTN MoMo and Safaricom adapters, matching the Airtel OpenAPI adapter's existing behavior.
- Added a uniform, reusable server-side CSV/XLSX export service, replacing a client-side-only, current-page-only CSV export on the merchant account screen.
- Added a Spotless (`google-java-format`, AOSP style) formatting check to `mvn verify`/CI, ratcheted against `origin/main` so existing legacy files are not force-reformatted.
- Added `.github/dependabot.yml` (Maven, npm, and GitHub Actions ecosystems) for automatic dependency-update pull requests.
- Migrated the admin dashboard and admin/merchant transaction list modules onto the TanStack Query hooks added earlier — the first real consumers of that infrastructure.
- Added a reconciliation manual-match workbench (admin UI) that pairs unmatched provider statement rows with a candidate CPay transaction, backed by a new `GET /api/v2/admin/reconciliation/candidate-transactions` search endpoint; built on the TanStack Query hooks pattern from the start.
- Added method-level authorization (`@PreAuthorize("hasRole('ADMIN')")`) to the reconciliation statement-import, statement-check, and auto-match endpoints, matching the existing reconciliation review/finance controllers.
- Added a per-merchant go-live readiness checklist (`GET /api/v2/admin/readiness/merchants/{merchantId}`) alongside the existing platform-wide readiness dashboard, scoped to each merchant's configured channels, callback secret, and compliance records.
- Added Micrometer gauges for parked/pending callback tasks, failed/pending merchant webhook deliveries, and open operations alerts, with Prometheus alert rules for callback and webhook backlog visibility.
- Added a capped legacy-ledger repair sweep that backfills missing normalized ledger postings for successful legacy pay-in/pay-out rows before daily trial balance work.
- Added atomic FX quote claiming for cross-border transfer intents so an active quote cannot be replayed or bound twice by concurrent requests.
- Migrated the audit trail and merchant-account statement modules (admin and merchant portal) onto typed TanStack Query hooks, following the dashboard/transactions/reconciliation precedent: loading/error/empty states, inline field validation, and mutation-driven cache invalidation instead of manual refetches.
- Added a session-authenticated admin statement export (`GET /api/v2/admin/merchants/{merchantNumber}/statements`), so the admin account-statement screen downloads a real server-rendered CSV/XLSX for the full requested range instead of building an Excel file client-side from whatever rows were already on screen.
- Extended `locale.js` string coverage across the login, signup, forgot-password, and authenticated-shell (top bar/menu) screens.
- Added a responsive card-layout fallback to the shared `Table` component below small viewport widths, so every table-based module gets a usable mobile layout for free.
- Added a merchant portal webhook manager UI (`Merchant Dashboard -> Webhooks`, Operations → Webhooks in the navigation): register/update endpoints per catalog event type with https validation, rotate signing secrets (each shown exactly once), inspect the delivery log with per-attempt detail, and replay failed or delivered deliveries — built on the TanStack Query hooks pattern with loading/error/empty states.
- Added `/api/v2/merchant-self-service/webhooks` to the portal session-authorization filter path gates, so the webhook self-service routes are covered by the same defense-in-depth 107-session envelope as channels and batches.
- Encrypted the legacy per-merchant `hmac_secret` field at rest (previously read in plaintext) and `merchant_callback_secrets.secret_value` (previously stored in plaintext, unlike the equivalent webhook secret), both with a decrypt-with-legacy-fallback so existing rows keep working until next rotation.
- Added a shared `SpreadsheetUploadValidator` (size/extension/content-type checks) to the reconciliation statement-import and statement-check admin endpoints, which previously accepted a multipart upload with no validation at all.
- Added `GET /api/v2/admin/callback-admin/secret-status`, reporting how many merchants still rely on the shared fallback callback-signing secret and how many active secrets remain legacy plaintext.
- Tightened the CSP: dropped `script-src 'unsafe-inline'` (the built SPA loads its bundle via an external `<script type="module">`, no inline script); made the dev-only `connect-src` localhost carve-out an externalized property, blank in production.
- Added v1 (`/api/do*`) request idempotency, matching the v2 path's existing `IdempotencyService` coverage.
- Added a required email-verification gate before first merchant login (`VerifyEmail.tsx`, backend verification endpoint), closing a signup path that previously let an unverified email reach the portal.
- Extended method-level authorization (`@PreAuthorize("hasRole('ADMIN')")`) across the remaining admin controllers that only had path-based protection.
- Added a dedicated merchant RSA-key encryption key (`CPAY_KEY_ENCRYPTION_KEY`, falling back to `MERCHANT_CHANNEL_ENCRYPTION_KEY` for existing installs) and a background re-encryption sweep (`MerchantKeyReencryptionService`) that migrates legacy plaintext/shared-key merchant keys onto it.
- Added merchant self-service batch-payout status and retry-failed endpoints (`GET/POST /api/v2/merchant-self-service/batches/{batchId}[/retry-failed]`).
- Wired KYC tier (`compliance_profiles.tier`) into `RiskDecisionService`'s cap evaluation, so a merchant's effective transaction/daily limits depend on their KYC tier rather than only the flat global caps.
- Added maker-checker approval on reconciliation daily close and settlement batch close (`FinanceWorkflowService`/`SettlementOpsService`, `POST .../close/approve` and `.../close/reject` on `ReconFinanceController`/`SettlementOpsController`) — a close now requires a second admin to approve before it takes effect.
- Added a payout limits and approval queue (`PayoutControlService`, `GET/POST /api/v2/admin/payout-approvals/**`): payouts over a configurable threshold are held for a second admin to approve, reject, or cancel instead of executing immediately.
- Added an admin webhook test-callback endpoint (`POST /api/v2/admin/webhooks/merchants/{merchantId}/test-callback`) and audited admin delivery replay, so an admin can verify a merchant's webhook endpoint without waiting for a real event.
- Added a treasury position read API (`GET /api/v2/admin/treasury/positions[/{currency}]`) summarizing current channel/currency balances for cross-border/FX oversight.
- Added new admin frontend modules for the above: `ModuleFinanceClose`, `ModuleSettlementClose`, `ModulePayoutApprovals`, `ModuleWebhookOps`.
- Added an EFRIS e-receipt outbox/scheduler (`net.citotech.cito.efris`) that queues a receipt record after a successful Ugandan pay-in — an honest extension point (logs "would issue e-receipt," configurable on/off via runtime settings) pending real EFRIS/URA business registration and API credentials, not a certified integration.
- Added BoU-style regulator reporting (`net.citotech.cito.reporting.RegulatorReportingService`, `GET /api/v2/admin/regulator/daily-cash-flow[/csv]`, `/reports`, `/pii-inventory`) generating a transaction/FX summary off the ledger — the exact regulator-required schema/frequency still needs compliance/BoU confirmation before any report is actually submitted.
- Added a reusable PII-masking utility (`net.citotech.cito.security.PiiMasking`) and wired it into the highest-traffic payer-number logging call sites.
- Added a payer-velocity risk rule to `RiskDecisionService`, capping how often the same payer identifier can transact in a rolling window.

### Changed

- Removed `common.base_url` (a dead, always-empty legacy config field) from every frontend call site in favor of `shared/config.ts`'s `apiUrl()`, and centralized the ad-hoc `localStorage` admin/merchant user read behind a shared `readStoredUser(portal)` helper for the class components that can't use the `useAuth()` hook directly.
- Chart line/point colors now resolve through the same CSS-variable helper as the rest of the dashboard charts, instead of a hardcoded hex color and a bare white point-border that looked wrong in dark mode.

- Routed legacy outbound provider HTTP calls through a Spring-managed `RestClient` executor with central timeout, error, and metrics handling.
- Updated Docker Compose onboarding to use the canonical backend path and the current local sandbox defaults.
- Made Flyway the documented canonical migration path and gated the legacy XML DB change runner behind `CPAY_LEGACY_DBCHANGES_ENABLED`.
- Removed safe unused Java imports, locals, and an empty application shell class.
- Made transaction timeout scans and timeout minutes configurable.
- Enabled graceful shutdown defaults.
- Tightened legacy SMS scheduling to compare `send_time` with `java.time` while preserving the existing `yyyy-MM-dd HH:mm:ss` API format.
- Tightened CORS headers and added standard security headers.
- The pre-existing local scheduler file locks are now a secondary safeguard behind ShedLock's distributed lock, rather than the only protection against concurrent cron execution.
- Bumped `org.json` (20240303 → 20260719), Apache POI (5.2.5 → 5.5.1), and Bucket4j (7.6.0 → 8.0.1, switched from the now-relocated `com.github.vladimir-bukhtoyarov` Maven coordinates to the current `com.bucket4j` ones) after a dependency-currency review.

### Fixed

- Fixed SHA-256 hex formatting to emit full 64-character hashes.
- Fixed client IP extraction to avoid concatenating proxy headers and null values.
- Added visible spreadsheet upload limits for size, type, and row count.
- Removed the runtime TLS verification bypass path; local development should use trusted test certificates or provider sandboxes instead of a global trust-all switch.
- Added a legacy callback terminal-state/provider-reference guard to avoid re-applying the ledger/statement path when a provider redelivers the same terminal callback.
- Fixed SMS provider rejection handling so non-2xx provider responses are marked `REJECTED` and refunded instead of being recorded as sent.

### Operational

- Confirmed the schema documentation currently tracks Flyway through V30 and points operators to regenerate a real `mysqldump` snapshot from a migrated database before release tagging.
- Added Flyway migrations `V29__provider_conversation_references.sql` and `V30__shedlock.sql`.
- Added Flyway migrations `V31__merchant_key_encryption.sql`, `V32__kyc_tier_limits.sql`, `V33__maker_checker_finance_close.sql`, `V34__payout_controls.sql`, `V35__efris_regulator_pii.sql`, `V36__feature_registry.sql`, `V37__identity_verification.sql`, and `V38__billing_tenancy_core.sql`. Flyway is now at `V38`; the schema snapshot under `Docs/Schema/snapshots/` still reflects `V30` and needs regenerating from a freshly migrated database before the next release tag.
- Added a per-merchant feature registry (`merchant_feature_flags`, V36) with a server-side resolution service (`FeatureRegistryService`), tenant-scoped via `TenantScopeGuard`, and an admin surface under `/api/v2/admin/feature-registry/**` for reversible per-merchant feature rollout.
- Added a GnuGrid NIN identity-verification pilot (`identity/`, V37) gated by the `identity-gnugrid` feature flag: consent-mandated requests, PII-safe storage (NIN/full-name/MSISDN stored only as hashes + masks), provider callback endpoint, and an admin surface under `/api/v2/admin/identity/**`.
- Added billing tenancy core (V38, ADR 0003): `billing_tenants`/`billing_customers`/`billing_accounts` with a 1:1 backfill for existing merchants, laying the scoping root for the future billing engine.
- Added admin self-service configuration of `payout_controls` rows (`PayoutConfigService`/`PayoutConfigController` under `/api/v2/admin/payout-controls/**`, gated by the `payout-controls-config` flag) and the `ModulePayoutControls` admin screen; a row saved here is enforced immediately by the v2 payout path.
- Added `ModuleFinanceClose` with a historical daily-close view (`GET /api/v2/admin/recon-finance/history`) alongside the existing summary/pending-submissions surface.
- Added a balance-monitoring pilot surface (`BalanceMonitoringService`/`BalanceMonitoringController` under `/api/v2/admin/balance-monitoring/overview`, gated by the `balance-monitoring` flag) combining gateway float balances, treasury positions, and the latest nightly float snapshots.
- Added `FeatureKeys` as the canonical feature-flag key catalog referenced by feature consumers.
- Added `TenantScopeGuard` cross-tenant isolation guardrail with unit tests proving merchant-scoped registry access always binds the tenant key.
