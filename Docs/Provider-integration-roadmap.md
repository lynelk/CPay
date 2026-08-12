# Provider Integration Roadmap

The gateway adapter layer already has `PaymentChannelAdapter`, provider adapter classes, and
`PaymentOrchestrationService`. MTN, Airtel, Airtel OpenAPI, Safaricom, and Yo! Payments are visible
through the adapter/channel surfaces, with Yo! Payments labelled exactly as `Yo! Payments` in the UI
and certification flow. The remaining work is to finish moving legacy monolith behavior out of
`Common` and provider model classes.

## Target Adapter Contract

Each provider adapter owns:

- supported country, currency, and channel code
- provider request and response parsing
- token acquisition through a shared token store
- provider health checks
- provider error mapping to the CPay error catalog
- timeout, retry, and circuit-breaker settings
- sandbox and production credential selection
- provider certification evidence hooks

The orchestration layer owns:

- idempotency
- ledger writes
- state transitions
- callback queueing
- merchant-facing errors

## Migration Rules

1. Do not add new provider logic to `Common.doPayIn` or `Common.doPayOut`.
2. Move token caches from local JSON files to encrypted DB rows or Redis with TTL.
3. Replace hand-rolled HTTP calls with a configured Spring client.
4. Keep outbound TLS on the JVM trust store. `custom.ssl.skip-verify` has been removed from code and configuration.
5. Store phone-prefix routing in data and keep provider-class prefix arrays only as startup/unmigrated-database fallbacks.
6. Keep merchant-facing channel names stable; the Yo channel is displayed as `Yo! Payments`.
7. Record sandbox, callback, statement-validation, and approval evidence before any channel is enabled for live traffic.

## Health Visibility

Every channel should expose:

- last successful provider request
- last failed provider request
- average latency
- configured currency
- sandbox or production mode
- degraded/unavailable status
- certification status and last approved evidence id

## Current Channel Notes

| Channel | Notes |
|---|---|
| MTN MoMo | Adapter-backed channel with legacy compatibility paths still present. |
| Airtel Money | Legacy and OpenAPI support; OpenAPI 401 refresh/retry behavior exists. |
| Airtel OpenAPI | Uses the current Airtel collection/disbursement endpoint configuration and certification evidence flow. |
| Safaricom M-Pesa | STK/B2C compatibility remains, with provider conversation references stored in the database. |
| Yo! Payments | Native v2 adapter label, provider endpoint execution, response signature verification, and statement parser are present. |

Provider code should keep using the shared execution, error translation, metrics, token-store, and
certification services rather than creating new controller-level provider branches.
