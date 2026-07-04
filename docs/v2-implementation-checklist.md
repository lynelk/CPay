# v2 Implementation Checklist

## Completed in this branch

- Added `/api/v2/payments/collect`.
- Added `/api/v2/payments/payout`.
- Added `/api/v2/channels`.
- Added versioned request signing helpers.
- Added in-memory nonce protection for single-node deployments.
- Added orchestration service boundary.
- Added adapter wrappers for existing native channels.
- Added amount parsing utility that uses `BigDecimal` before handing off to legacy `Double` paths.
- Added docs for signing, examples, schema target, and architecture.

## Intentionally preserved

- Existing `/api/v1` endpoints.
- Existing legacy gateway calls.
- Existing merchant and statement tables.

## Still recommended after review

- Enable Flyway only after baseline schema reconciliation.
- Move nonce storage to Redis or database for clustered deployment.
- Migrate fixed gateway balances to normalized channel balances.
- Create a full v2 status service backed by a transaction repository.
