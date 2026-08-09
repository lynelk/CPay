# CPay Vending Platform

## Purpose

The Vending Platform adds machine/device commerce to CPay without creating a second payment gateway beside CPay. It is a multi-tenant domain rooted in the existing CPay merchant, payment orchestration, feature registry, transaction store, risk controls and ledger.

Power-bank rental is the first supported operating profile. The domain model is deliberately generic enough for lockers, rental cabinets, dispensers, ticketing devices, parking/charging assets and other machine-led vending workflows.

## Design principles

1. **Merchant is the tenant.** Every vending-owned table carries `merchant_id`, and merchant-scoped repository access binds `TenantScopeGuard.TENANT_PARAM`.
2. **CPay remains the money system of record.** Deposit collection and refund/payout use `PaymentOrchestrationService`; vending does not call a mobile-money provider directly and does not maintain a competing financial ledger.
3. **Vendor hardware is an adapter.** Physical machine operations use `VendingConnectorAdapter` and `VendingConnectorRegistry`, mirroring CPay's payment-provider adapter approach.
4. **The manufacturer's wire contract is configuration.** OEM URLs, authentication, JSON request template and response/callback field mappings are tenant-scoped data. CPay does not hard-code endpoint guesses from an operations manual.
5. **Asynchronous payment status is explicit.** A mobile-money request is not treated as cash merely because it was submitted. A rental stays `PAYMENT_PENDING` until the CPay transaction is successful.
6. **Device callbacks are authenticated before state mutation.** HMAC, timestamp window, nonce replay protection and external-event idempotency run before a heartbeat, release or return event is trusted.
7. **State changes are auditable.** `vending_events`, `vending_commands` and `vending_device_callbacks` retain operational evidence while CPay's normal transaction, risk, webhook, billing and ledger records retain financial evidence.
8. **Sensitive customer data is minimised.** Rentals store a tenant-scoped SHA-256 customer hash, a masked display value and encrypted MSISDN. The clear MSISDN is only recovered when CPay must address a refund payout.
9. **Public QR sessions are opaque and revocable.** Physical station QR tokens are cryptographically random; per-rental status tokens are stored only as SHA-256 hashes and expire after 24 hours.
10. **Feature rollout is reversible.** `vending-platform` is globally disabled by V50 and can be enabled per merchant through the existing feature registry.

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

The manual is an **operations guide**, not a manufacturer wire/API specification. It demonstrates background actions such as cabinet placement and power-bank pop-up/ejection, but does not provide the authenticated request/response contract needed to implement those actions against real hardware.

CPay now contains a production-capable `CHARGENOW` HTTP adapter, but the adapter is **contract-provisioned**. Operators enter the actual endpoint, authentication mode, release JSON template and response/callback mappings from the manufacturer's integration pack. This is a real transport and callback implementation without pretending that undocumented OEM details are known. It is not considered OEM-certified until those real manufacturer details, sandbox credentials and test hardware have been validated end to end.

## Schema

Flyway V50 introduces the base vending estate:

| Table | Purpose |
|---|---|
| `vending_locations` | Tenant-owned stores/sites/venues. |
| `vending_pricing_policies` | Reusable device/location pricing rules. |
| `vending_devices` | Cabinets, vending machines and other controllable devices. |
| `vending_assets` | Rentable/dispensable inventory or slots. |
| `vending_customer_balances` | Tenant-scoped surcharge/debt and block state. |
| `vending_rentals` | Rental/order state plus CPay transaction references and pricing snapshot values. |
| `vending_commands` | Manufacturer command evidence and provider references. |
| `vending_events` | Append-oriented operational audit history. |

Flyway V51 adds the manufacturer/hosted-rental layer:

| Table | Purpose |
|---|---|
| `vending_connector_configs` | Tenant-scoped OEM URL, auth, request template, response mappings and encrypted callback secret. |
| `vending_callback_nonces` | Replay-protection store for authenticated manufacturer callbacks. |
| `vending_device_callbacks` | External-event idempotency, signature state, processing state and raw callback evidence. |
| `vending_hosted_sessions` | Hash-only, expiring public rental status sessions. |

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
   | authenticated return event / operator return
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
8. Rental sync reconciles the CPay transaction. Only `SUCCESSFUL/SUCCESS/COMPLETED` advances the rental and applies the planned old-surcharge reduction.
9. Dispatch `RELEASE_ASSET` through the configured manufacturer adapter.

### Return

1. Receive an authenticated manufacturer return callback or a controlled operator return action.
2. Rate the active rental using its pricing policy and billable duration.
3. If usage is below escrow, the difference becomes `refund_amount`.
4. If usage exceeds escrow, the shortfall is added to `vending_customer_balances.surcharge_balance`.
5. Persist settlement and audit event.
6. Submit any refund through `PaymentOrchestrationService.payout` using `VEND-REFUND-*`.
7. Rental sync marks the rental settled when the CPay refund payout becomes successful.

The existing CPay adapter routing, merchant permissions, risk rules, payout controls, provider transactions, core ledger and billing usage hooks therefore remain in force for vending money movement.

## Manufacturer integration

### ChargeNow adapter

`ChargeNowVendingConnectorAdapter` is a real outbound HTTP adapter. It deliberately contains **no guessed OEM endpoint**. A tenant supplies the actual contract under `vending_connector_configs`.

Supported outbound authentication modes are:

- `BEARER`
- `API_KEY_HEADER`
- `BASIC`
- `HMAC_SHA256_TS_BODY`
- `NONE` for controlled sandbox use

The release request is a JSON template. Text nodes may contain:

```text
{{externalDeviceId}}
{{commandReference}}
{{rentalReference}}
{{merchantId}}
{{deviceId}}
```

CPay parses the template as JSON before replacement so inserted values remain JSON-escaped rather than being concatenated into an unsafe raw body.

Example configuration shape:

```json
{
  "commandBaseUrl": "https://oem.example/api",
  "releasePath": "/station/release",
  "releaseRequestTemplate": "{\"stationId\":\"{{externalDeviceId}}\",\"requestId\":\"{{commandReference}}\",\"rentalReference\":\"{{rentalReference}}\"}",
  "authMode": "BEARER",
  "authValue": "<token from OEM>",
  "callbackSecret": "<shared HMAC secret>",
  "responseSuccessField": "code",
  "responseSuccessValue": "0",
  "responseReferenceField": "data.commandId",
  "responseMessageField": "message",
  "callbackEventTypeField": "eventType",
  "callbackEventIdField": "eventId",
  "callbackDeviceField": "deviceId",
  "callbackRentalField": "rentalReference",
  "callbackAssetField": "assetCode",
  "callbackAvailableCountField": "availableCount",
  "active": true
}
```

Secrets are encrypted using CPay's merchant-channel AES-GCM service and are not returned by connector list/view APIs. Callback secrets can be rotated, with the new cleartext value returned once.

### Authenticated device callbacks

Manufacturer callbacks are received at:

```text
POST /api/v2/vending/device-callbacks/{connectorCode}/{merchantId}
```

Default callback headers:

```text
X-CPay-Vending-Signature
X-CPay-Vending-Timestamp
X-CPay-Vending-Nonce
```

For `HMAC_SHA256_TS_NONCE_BODY`, the signed base string is:

```text
TIMESTAMP
NONCE
RAW_REQUEST_BODY
```

The HMAC is SHA-256 and Base64 encoded. CPay also supports `HMAC_SHA256_BODY` where required by an OEM contract.

Before any device/rental mutation CPay checks:

1. active tenant/connector contract;
2. required signature/timestamp/nonce headers;
3. timestamp within a five-minute skew window;
4. constant-time HMAC match;
5. nonce has not previously been consumed for that tenant/connector;
6. external event id has not already been processed.

Normalized event processing currently handles heartbeat/online inventory, device offline, asset released and asset returned. Unknown but authenticated events are retained as `MANUFACTURER_EVENT_UNMAPPED` rather than silently trusted as a known state transition.

## Customer QR / hosted rental

Each physical station can be given a cryptographically random public token. The QR code points to:

```text
GET /vending/rent/{publicToken}
```

The responsive hosted page:

1. loads the station/location/pricing and live available count;
2. collects the customer's mobile-money number and optional network selection;
3. creates the rental through the same `VendingRentalService` used by the portal API;
4. submits the deposit through CPay;
5. receives an opaque per-rental status token;
6. polls status while the customer approves the mobile-money request;
7. shows release success only once the rental reaches `ACTIVE`.

Public API routes behind that page are:

```text
GET  /api/v2/vending/hosted/stations/{publicToken}
POST /api/v2/vending/hosted/stations/{publicToken}/start
GET  /api/v2/vending/hosted/sessions/{statusToken}
```

Hosted starts are rate-limited in the existing DB-backed rate limiter. Status tokens are high-entropy random values, only their SHA-256 hashes are persisted, and sessions expire after 24 hours.

## Merchant API and UI

Merchant vending operations remain under the existing merchant-session boundary:

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
GET  /api/v2/merchant-self-service/vending/connectors
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}/rotate-callback-secret
POST /api/v2/merchant-self-service/vending/devices/{deviceCode}/rotate-public-token
```

`MerchantModuleVending` exposes location setup, pricing, device registration, manufacturer contract configuration, QR-target rotation and rental visibility inside the merchant portal. The `LegacySessionAuthorizationFilter` now explicitly includes the vending self-service prefix.

## Admin operations and UI

Admin routes are protected by both `/api/v2/admin/**` role matching and class-level `@PreAuthorize("hasRole('ADMIN')")`:

```text
GET  /api/v2/admin/vending/overview[?merchantId=...]
GET  /api/v2/admin/vending/events[?merchantId=...]
GET  /api/v2/admin/vending/callbacks[?merchantId=...]
GET  /api/v2/admin/vending/commands[?merchantId=...]
GET  /api/v2/admin/vending/connectors/{merchantId}
POST /api/v2/admin/vending/connectors/{merchantId}/{connectorCode}
POST /api/v2/admin/vending/connectors/{merchantId}/{connectorCode}/rotate-callback-secret
POST /api/v2/admin/vending/devices/{merchantId}/{deviceCode}/rotate-public-token
```

`ModuleVending` gives operations a tenant-filterable view of estate counts, active/pending rentals, offline devices, failed callbacks, manufacturer commands and operational events.

## Security boundaries

- Merchant vending configuration is protected by the existing portal session filter and normal browser CSRF mechanism.
- Public hosted/customer routes do not use a merchant session and are narrowly scoped to opaque station/status tokens.
- Manufacturer callback POSTs are CSRF-exempt because they are server-to-server, but they are independently authenticated using the configured HMAC contract, timestamp and nonce.
- Admin vending routes inherit the existing `hasRole('ADMIN')` path rule and add method-level `@PreAuthorize`.
- Customer MSISDNs remain hash/mask/encrypted-at-rest as in the base V50 design.
- Manufacturer auth values and callback secrets are encrypted at rest and redacted from list/view responses.
- Raw manufacturer error bodies are not copied into merchant-facing messages.

## Validation

The PR CI now validates both frontend and backend rather than merely exercising the React build:

- React/Vite build and Vitest suite on Node 20.x and 22.x;
- Java 21 backend package/compile against the active `InitializrSpringbootProjectFresh/pom.xml`;
- `VendingPricingEngineTest` for tariff/free-window/cap/overtime behaviour;
- `VendingCallbackSecurityServiceTest` for valid HMAC acceptance, invalid signature rejection, stale timestamp rejection and nonce replay rejection.

The current validation is intentionally not described as OEM certification. It proves the CPay implementation compiles and its local security/rating invariants pass; real cabinet certification still requires the manufacturer integration pack, sandbox credentials and test hardware.

## Required OEM production pack

Before enabling `CHARGENOW` for production, populate and certify the real manufacturer's contract covering at least:

- production and sandbox base URLs;
- authentication/signature/token rules;
- device/station identifiers;
- release/eject endpoint and exact JSON body;
- success/error response mapping;
- return/slot event callback fields;
- heartbeat/inventory callback fields;
- callback HMAC/signature contract;
- idempotency/correlation identifiers;
- timeout/retry semantics;
- error/status catalogue;
- sandbox/test device details.

The implementation is intentionally ready to receive these facts as configuration. It does not convert an operations screenshot into a fictional API, because payment/device incident reports already have enough material without our help.

## Remaining production slices

1. Add a distributed-lock/claim worker for automatic payment/refund synchronization instead of relying on portal/customer polling.
2. Add an atomic release-command claim so concurrent status polls can never dispatch the same physical release twice across application instances.
3. Extend asset/slot assignment reconciliation for OEM callbacks where the hardware returns a concrete slot and asset identifier.
4. Add billing suspension/resume and manual settlement UI actions already represented by schema/pricing concepts.
5. Add Testcontainers coverage for tenant isolation, callback replay, concurrent release, and the full collect -> release -> return -> refund flow.
6. Add reconciliation views joining vending rental references to CPay transaction, ledger and provider statement evidence.
7. Run real OEM sandbox certification once the manufacturer integration pack and test cabinet are available.
