# Follow-up Implementation Scope

This branch continues the clean follow-up work after PR #14 and keeps the implementation free of temporary marker files.

Implemented areas:

1. Safer v2 routing through legacy gateway id lookup to avoid prefix collisions.
2. Normalized merchant-channel balance tables and balance read service.
3. BigDecimal-backed internal money fields for `Transaction` and `Balance`, with legacy `Double` accessors retained at compatibility boundaries.
4. Callback task queue with retry scheduling and parked-final state.
5. Existing transaction callback scheduler now queues final transaction callbacks instead of directly retrying legacy callback delivery.
6. Reconciliation tables and endpoints for unmatched, auto-match, and operator match.
7. Provider statement parsers for MTN, Airtel, Airtel OpenAPI, and Safaricom CSV statement imports.
8. Reconciliation review workflow for maker-checker style request, approve, and reject decisions.
9. Merchant balance endpoint.
10. Gateway operations endpoints.
11. Admin API role enforcement on `/api/v2/admin/**`.
12. React 18 frontend baseline, modern root API, simplified app shell, operations route, and v2 API client.

Validation still required before production promotion:

- Run backend Maven tests and fix any compile failures.
- Run frontend install/build because dependency upgrades can expose hidden old-component incompatibilities.
- Run provider sandbox callbacks and statement import trials with real provider files.
- Confirm database migrations against a staging copy before enabling Flyway in production.
