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

### Changed

- Made Flyway the documented canonical migration path and gated the legacy XML DB change runner behind `CPAY_LEGACY_DBCHANGES_ENABLED`.
- Removed safe unused Java imports, locals, and an empty application shell class.
- Made transaction timeout scans and timeout minutes configurable.
- Enabled graceful shutdown defaults.
- Tightened CORS headers and added standard security headers.

### Fixed

- Fixed SHA-256 hex formatting to emit full 64-character hashes.
- Fixed client IP extraction to avoid concatenating proxy headers and null values.
- Added visible spreadsheet upload limits for size, type, and row count.

### Operational

- Added `docs/schema/snapshots/2026-07-16-cpayadmin.sql` as the current no-data schema snapshot.
