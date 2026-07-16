# Provider Integration Roadmap

The gateway adapter layer already has `PaymentChannelAdapter`, provider adapter classes, and `PaymentOrchestrationService`. The remaining work is to finish moving legacy monolith behavior out of `Common` and provider model classes.

## Target Adapter Contract

Each provider adapter owns:

- supported country, currency, and channel code
- provider request and response parsing
- token acquisition through a shared token store
- provider health checks
- provider error mapping to the CPay error catalog
- timeout, retry, and circuit-breaker settings

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
4. Remove `custom.ssl.skip-verify` from all production paths.
5. Store phone-prefix routing in data once the adapter migration is complete.

## Health Visibility

Every channel should expose:

- last successful provider request
- last failed provider request
- average latency
- configured currency
- sandbox or production mode
- degraded/unavailable status
