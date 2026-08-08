# ADR 0006: Webhook Catalog Schema-Per-Type

Date: 2026-08-07
Status: Accepted

## Context

`WebhookEventCatalog` (`net.citotech.cito.webhook`) registers exactly 8 hardcoded event types in a
static block. Every registered type currently shares one envelope schema
(`envelopeSchema()`), which hard-requires `transactionId`, `amount`, and `currency` as required
fields — because every existing event type (`payment.*`, `payout.*`, `refund.*`) genuinely is a
transaction event. Billing needs to add non-transactional event types (a balance threshold
crossed, a usage meter recorded, a subscription state change, an invoice finalized) that do not
naturally have a `transactionId`. Registering them against the current shared schema would either
force a fake `transactionId` onto non-transactional events or silently violate the catalog's own
documented intent ("a fixed, versioned set," per `WebhookEventCatalogTest`'s javadoc).

## Decision

Change `WebhookEventCatalog.register(...)` to accept a schema per type (a schema value or a small
schema-builder callback) instead of always calling the one shared `envelopeSchema()` method. The
8 existing event types are migrated to pass their current schema explicitly, with **zero change in
the schema they produce** — this is a refactor of the registration mechanism, not a behavior
change to any existing event type. New non-transactional billing event types are registered
against their own, correctly-shaped schema only after this mechanism lands.

## Consequences

- Existing `WebhookEventCatalogTest`/`WebhookEventCatalogControllerTest` assertions on the 8
  current event types must stay green unmodified — only the construction call sites change.
- Billing event types can be added later as ordinary catalog entries, with no further catalog
  mechanism changes needed.
- `MerchantWebhookService`'s delivery/encryption/retry logic is untouched by this decision — this
  ADR only concerns the catalog's schema-registration shape.

## Follow-ups

- When billing event types are added (Phase 2), confirm merchant-facing webhook documentation
  (`Docs/Webhook-events.md`) is updated alongside the new catalog entries.
