# CPay v2 Architecture Implementation

This branch introduces a parallel v2 architecture while preserving the existing `/api/v1` endpoints.

## Implemented boundaries

- `PaymentOrchestrationService` centralizes collect, payout, balance, merchant checks, and channel resolution.
- `PaymentChannelRegistry` is now used by orchestration instead of forcing new channels into controller logic.
- Legacy channels are wrapped using `LegacyGatewayAdapter` implementations.
- `/api/v2/payments/collect` and `/api/v2/payments/payout` accept explicit `channel`, `country`, and `currency` fields.
- `/api/v2/channels` exposes registered channel capabilities.
- `/api/v2` requests use versioned signing headers.

## Compatibility

The v2 orchestration service still calls the legacy transaction engine for actual posting and provider interaction. This keeps live behaviour compatible while creating a cleaner extension path.

## Remaining migration work

- Move provider-specific charge settings into structured channel tables.
- Replace fixed balance columns with normalized merchant-channel balances.
- Implement full v2 transaction status using a repository/service instead of the v1 endpoint.
- Move nonce storage to Redis or a database table for clustered production.
