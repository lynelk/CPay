# Changelog

All notable CPay changes should be recorded here before a release or production deployment.

Use calendar-versioned tags such as `2026.07.16` for releases. Keep entries grouped by Added, Changed, Fixed, Security, and Operational.

## Unreleased

### Added

- Added request correlation IDs and structured JSON console logging for backend observability.
- Added bounded operational cleanup for `api_rate_limits` and stale callback task claims.
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
- Added distributed locking (ShedLock, database-backed) for the status-check and payout crons so multi-instance/HA deployments cannot process the same batch twice.
- Added a database-backed store for Safaricom payout-callback conversation-reference lookups, replacing a local, per-instance plaintext file.
- Added error catalog codes `148` (risk-declined) and `149` (step-up MFA required).
- Added Testcontainers-based database integration tests, WireMock-based provider API mocking tests, and a first end-to-end test suite (opt-in, Docker-gated — see `Contributing.md`).
- Added a Gatling load-testing toolchain with baseline and signed-request simulations (opt-in, not part of the default build).
- Added a shared `useAuth` hook and TanStack Query hooks (`src/shared/api/hooks.ts`) for admin/merchant portal server state.

### Changed

- Made Flyway the documented canonical migration path and gated the legacy XML DB change runner behind `CPAY_LEGACY_DBCHANGES_ENABLED`.
- Removed safe unused Java imports, locals, and an empty application shell class.
- Made transaction timeout scans and timeout minutes configurable.
- Enabled graceful shutdown defaults.
- Tightened CORS headers and added standard security headers.
- The pre-existing local scheduler file locks are now a secondary safeguard behind ShedLock's distributed lock, rather than the only protection against concurrent cron execution.

### Fixed

- Fixed SHA-256 hex formatting to emit full 64-character hashes.
- Fixed client IP extraction to avoid concatenating proxy headers and null values.
- Added visible spreadsheet upload limits for size, type, and row count.

### Operational

- Added `Docs/Schema/snapshots/2026-07-16-cpayadmin.sql` as the current no-data schema snapshot.
- Added Flyway migrations `V29__provider_conversation_references.sql` and `V30__shedlock.sql`.
