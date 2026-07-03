# Adapter, Finance Operations, and Production Controls Tranche

## Implemented

### Adapter-native v2 execution

- Adds executable provider adapter behavior instead of default throw-only adapters.
- Adds `AdapterNativePaymentService`.
- Adds `/api/v2/native/payments/collect` and `/api/v2/native/payments/payout`.
- Keeps the older `/api/v2/payments/*` path available while the native path is tested.

### Provider sandbox and statement validation

- Adds `provider_sandbox_runs` persistence.
- Adds sandbox run service and endpoint.
- Builds on existing statement validation from prior hardening work.
- Adds provider validation runbook covering MTN, Airtel, Airtel OpenAPI, and Safaricom.

### Callback security

- Adds merchant callback secrets.
- Updates callback signing to use merchant-specific secrets.
- Adds callback admin controls for secret rotation and requeueing parked tasks.
- Adds callback verification and requeue runbook.

### Reconciliation finance workflow

- Adds daily close records.
- Adds finance workflow service for posting approved reviews, daily close, and summary reports.
- Adds reconciliation finance endpoint group.
- Adds finance daily close runbook.

### Frontend wiring

- Adds an operations API client.
- Expands the operations console to call dashboard, sandbox, callback, and finance APIs.

### Operations dashboard and alerts

- Adds operations alerts table.
- Adds operations alert service and dashboard endpoint.
- Dashboard summary covers open alerts, parked callbacks, unmatched reconciliation records, and statement validation failures.

## Validation required

- Backend Maven build and tests.
- Frontend build.
- Staging database migration test.
- Provider sandbox runs for all supported channels.
- Real statement-file validation using provider samples.
- Merchant callback signature verification in sandbox.
- Finance daily close dry run on staging data.

## Known rollout note

The adapter-native endpoints are intentionally exposed under `/api/v2/native/payments/*` so they can be tested beside the existing v2 endpoints before routing all production traffic to the native path.
