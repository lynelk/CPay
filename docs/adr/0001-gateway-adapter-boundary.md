# ADR 0001: Gateway Adapter Boundary

Date: 2026-07-16
Status: Accepted

## Context

CPay supports several payment providers and legacy routes. Adding each provider directly into controllers makes routing, testing, certification, and provider-specific error handling harder to reason about.

## Decision

New provider work should use the `net.citotech.cito.gateway` adapter boundary. Controllers and orchestration services select a channel; adapters describe capabilities and handle provider-specific execution details. Existing `/api/v1` behavior remains available while new `/api/v2/native/payments/*` work moves through adapters.

## Consequences

- Provider-specific code stays close to the provider adapter.
- Channel capability discovery can be exposed without reading controller internals.
- Merchant channel setup and endpoint validation can be shared across providers.
- Legacy routes can be retired gradually after merchants migrate.

## Follow-ups

- Continue moving provider charge settings and route preferences into structured tables.
- Add provider-specific statement parsers when formats diverge.
- Track adapter health and certification evidence per channel.
