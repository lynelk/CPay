# CPay Vending Platform

## Purpose

The Vending Platform adds machine/device commerce to CPay without creating a second payment gateway beside CPay. It is a multi-tenant domain rooted in the existing CPay merchant, payment orchestration, feature registry, transaction store, risk controls and ledger.

Power-bank rental is the first supported operating profile. The domain model is deliberately generic enough for lockers, rental cabinets, dispensers, ticketing devices, parking/charging assets and other machine-led vending workflows.

## Design principles

1. **Merchant is the tenant.** Every vending-owned table carries `merchant_id`, and merchant-scoped repository access binds `TenantScopeGuard.TENANT_PARAM`.
2. **CPay remains the money system of record.** Deposit collection and refund/payout use `PaymentOrchestrationService`; vending does not call a mobile-money provider directly and does not maintain a competing financial ledger.
3. **Vendor hardware is an adapter.** Physical machine operations use `VendingConnectorAdapter` and `VendingConnectorRegistry`, mirroring CPay's payment-provider adapter approach.
4. **Asynchronous payment status is explicit.** A mobile-money request is not treated as cash merely because it was submitted. A rental stays `PAYMENT_PENDING` until the CPay transaction is successful.
5. **State changes are auditable.** `vending_events` and `vending_commands` retain operational evidence while CPay's normal transaction, risk, webhook, billing and ledger records retain financial evidence.
6. **Sensitive customer data is minimised.** Rentals store a tenant-scoped SHA-256 customer hash, a masked display value and encrypted MSISDN. The clear MSISDN is only recovered when CPay must address a refund payout.
7. **Feature rollout is reversible.** `vending-platform` is globally disabled by V50 and can be enabled per merchant through the existing feature registry.

## Source operating models

### Supplied power-bank prototype

The first profile preserves the supplied prototype's commercial logic:

- deposit: UGX 20,000
- hourly rate: UGX 2,000
- minimum one billable hour
- round a partial billing block upward
- on return, refund the unused part of escrow
- if usage exceeds escrow, carry the shortfall as a customer surcharge
- on a later rental, a new deposit first settles an existing surcharge and only the remainder protects the new rental
- an authorised operator may waive all or part of a surcharge; a waiver is a write-off, not a money movement

Unlike the prototype, the implementation does **not** reduce an old surcharge before the new deposit has actually succeeded. The planned split is stored on the pending rental and applied only when CPay reports the collection successful.

### ChargeNow management model

The supplied ChargeNow management manual informed the generic model:

- stores/locations and business hours
- pricing policies with deposit, free duration, unit price, billing block, daily cap, overtime amount and overtime days
- charging cabinets/devices, slots/assets, online/offline/heartbeat state
- rental lifecycle and exception states
- manual settlement, billing suspension and refund operations
- agent/store/device reporting and operational monitoring

The manual is an **operations guide**, not a manufacturer wire/API specification. It demonstrates background actions such as cabinet placement and power-bank pop-up/ejection, but does not provide the authenticated request/response contract needed to implement those actions against real hardware. For that reason, the repository contains a `SIMULATED` connector and a production adapter contract, not invented ChargeNow HTTP endpoints.

## Schema

Flyway V50 introduces:

| Table | Purpose |
|---|---|
| `vending_locations` | Tenant-owned stores/sites/venues. |
| `vending_pricing_policies` | Reusable device/location pricing rules. |
| `vending_devices` | Cabinets, vending machines and other controllable devices. |
| `vending_assets` | Rentable/dispensable inventory or slots. |
| `vending_customer_balances` | Tenant-scoped surcharge/debt and block state. |
| `vending_rentals` | Rental/order state plus CPay transaction references and pricing snapshot values. |
| `vending_commands` | Manufacturer command evidence and provider references. |
| `vending_events` | Append-only operational audit history. |

No foreign keys are used, matching the loose-coupling convention already used by several CPay domains. Cross-tenant relationships are instead validated in repository operations before binding rows.

## Pricing engine

`VendingPricingEngine` supports:

- deposit amount
- free minutes
- unit price
- billing block minutes
- minimum billing blocks
- daily cap
- overtime/final purchase amount
- overtime days
- excluded billing-suspension seconds

Free-time behaviour follows the supplied ChargeNow description: if the session is returned inside the free window it is free; once the window is exceeded, normal billing uses the whole rental duration rather than subtracting the free window.

A power-bank policy matching the supplied prototype can be created with:

```json
{
  "policyCode": "POWERBANK_UG",
  "name": "Uganda power-bank standard",
  "currency": "UGX",
  "depositAmount": "20000",
  "freeMinutes": 0,
  "unitPrice": "2000",
  "billingBlockMinutes": 60,
  "minimumBillingBlocks": 1,
  "refundMode": "ORIGINAL_ROUTE"
}
```

## Rental state machine

```text
PAYMENT_PENDING
   | CPay collection successful
   v
READY_TO_RELEASE
   | manufacturer RELEASE_ASSET accepted
   v
ACTIVE
   | return/settlement
   +--------------------------+
   |                          |
   v                          v
SETTLED                  REFUND_PENDING
                              |
                              | CPay payout successful
                              v
                           SETTLED

Failure paths:
PAYMENT_FAILED
RELEASE_FAILED -> retry release
REFUND_FAILED  -> operator review/retry
```

The rental start timestamp is taken when a successful collection is promoted to `READY_TO_RELEASE`, not when the first payment request is merely submitted.

## Payment flow

### Start

1. Resolve the tenant's device and pricing policy.
2. Normalise/protect the customer identifier.
3. Reject another open rental for the same tenant/customer.
4. Read any existing surcharge.
5. Calculate the planned deposit split: old surcharge first, remainder to current escrow.
6. Store `PAYMENT_PENDING` rental.
7. Submit the full deposit through `PaymentOrchestrationService.collect` using a deterministic `VEND-COLLECT-*` reference.
8. `POST .../sync` reconciles the CPay transaction. Only `SUCCESSFUL/SUCCESS/COMPLETED` advances the rental and applies the planned old-surcharge reduction.
9. Dispatch `RELEASE_ASSET` through the configured manufacturer adapter.

### Return

1. Rate the active rental using its pricing policy and billable duration.
2. If usage is below escrow, the difference becomes `refund_amount`.
3. If usage exceeds escrow, the shortfall is added to `vending_customer_balances.surcharge_balance`.
4. Persist settlement and audit event.
5. Submit any refund through `PaymentOrchestrationService.payout` using `VEND-REFUND-*`.
6. `POST .../sync` marks the rental settled when the CPay refund payout becomes successful.

This means the existing CPay adapter routing, merchant permissions, risk rules, payout controls, provider transactions, core ledger and billing usage hooks remain in force for vending money movement.

## Merchant API

All current vending routes are under the existing merchant session boundary:

```text
GET  /api/v2/merchant-self-service/vending/overview
GET  /api/v2/merchant-self-service/vending/locations
POST /api/v2/merchant-self-service/vending/locations
GET  /api/v2/merchant-self-service/vending/pricing
POST /api/v2/merchant-self-service/vending/pricing
GET  /api/v2/merchant-self-service/vending/devices
POST /api/v2/merchant-self-service/vending/devices
GET  /api/v2/merchant-self-service/vending/rentals?limit=100
POST /api/v2/merchant-self-service/vending/rentals/start
POST /api/v2/merchant-self-service/vending/rentals/{reference}/sync
POST /api/v2/merchant-self-service/vending/rentals/{reference}/release
POST /api/v2/merchant-self-service/vending/rentals/{reference}/return
POST /api/v2/merchant-self-service/vending/surcharges/waive
```

A customer-facing QR/hosted rental page should be added as a separate tokenised surface, analogous to hosted checkout, instead of weakening the merchant-session or v2 signing boundaries. That is intentionally not smuggled into this first backend slice.

## Manufacturer integration

Implement a real manufacturer connector by adding a Spring component:

```java
@Component
public final class ChargeNowVendingConnectorAdapter implements VendingConnectorAdapter {
    @Override
    public String connectorCode() { return "CHARGENOW"; }

    @Override
    public VendingCommandResult execute(VendingCommand command) {
        // map RELEASE_ASSET / LOCK_SLOT / HEARTBEAT etc. to the manufacturer's documented API
    }
}
```

Before writing that adapter, obtain the manufacturer's actual integration specification covering at least:

- base URLs and environments
- authentication/signature/token rules
- device/station identifiers
- release/eject command
- return/slot event callback
- heartbeat/status format
- callback authentication
- idempotency/correlation identifiers
- timeout/retry semantics
- error/status catalogue
- sandbox/test device details

Inventing these details from screenshots in an operations manual would be the traditional software-development technique known as “making tomorrow's incident report today.” The adapter seam exists specifically so the real contract can be inserted without changing rental or payment logic.

## Next production slices

1. Real manufacturer adapter and signed device-event callback endpoint.
2. Customer QR/hosted rental journey with rate limiting and anti-abuse controls.
3. Asset/slot assignment and return-event reconciliation.
4. Billing suspension/resume and manual settlement UI actions already represented by schema/pricing concepts.
5. Tenant-aware admin and merchant portal screens, dashboards and exports.
6. Distributed-lock worker for automatic pending-payment/refund synchronization instead of manual sync calls.
7. Testcontainers coverage for tenant isolation, concurrency, duplicate callbacks and full collect -> release -> return -> refund flow.
8. Reconciliation views joining vending rental references to CPay transaction, ledger and provider statement evidence.
