# CPay Vending Platform

## Purpose

The Vending Platform adds machine/device commerce to CPay without creating another payment stack beside it. Power-bank rental is the first profile, but the domain is intentionally reusable for rental cabinets, lockers, dispensers and other machine-led commerce.

The merchant remains the tenant. CPay remains the money system of record. Manufacturer hardware remains an adapter boundary.

## Source boundary

The supplied ChargeNow management material documents the operating model: stores, pricing, cabinets, power-bank release, heartbeat/online state, returns, monitoring and reporting. It does **not** provide the private manufacturer wire contract required to hard-code a production endpoint, authentication algorithm or payload schema.

Public Bajie/ChargeNow material also describes cloud-connected stations, remote unlock commands, real-time status and an Open API/Webhook capability. It still does not publish the tenant-specific partner endpoint and credential contract needed for certification.

For that reason CPay implements a real, configurable `CHARGENOW` HTTP adapter. OEM URLs, HTTP methods, authentication, signing templates, request templates, success mappings and callback field mappings are tenant configuration. The adapter is complete on the CPay side without pretending undocumented OEM details are known.

**OEM certification remains an external deployment gate** until the manufacturer provides the integration pack, sandbox credentials and a test cabinet.

## Flyway schema

Main now owns Flyway V50 for Communication Routing, so vending uses the next available versions:

- `V51__vending_platform.sql` - locations, pricing policies, devices, assets, customer balances, rentals, command evidence and operational events.
- `V52__vending_manufacturer_hosted.sql` - OEM connector contracts, callback replay/idempotency evidence and hosted-rental sessions.
- `V53__chargenow_adapter_completion.sql` - operation-specific OEM mappings, outbound HMAC configuration, callback signature encoding and callback command/provider-reference correlation fields.

Important tables are:

| Table | Purpose |
|---|---|
| `vending_locations` | Merchant-owned sites/stores. |
| `vending_pricing_policies` | Deposit/rate/cap/overtime policies. |
| `vending_devices` | Cabinets and other controlled devices. |
| `vending_assets` | Power banks/slots or other rentable inventory. |
| `vending_customer_balances` | Tenant-scoped surcharge/debt state. |
| `vending_rentals` | Rental lifecycle and CPay transaction references. |
| `vending_commands` | Atomic command claims plus OEM command evidence. |
| `vending_events` | Operational audit trail. |
| `vending_connector_configs` | Tenant OEM host/auth/callback configuration. |
| `vending_connector_operations` | Per-command HTTP method/path/template/response mapping. |
| `vending_callback_nonces` | Replay protection. |
| `vending_device_callbacks` | Authenticated callback evidence/idempotency. |
| `vending_hosted_sessions` | Hash-only customer status sessions. |

Every owned row is tenant-scoped by `merchant_id`; repository SQL uses `TenantScopeGuard`.

## Power-bank commercial profile

The supplied prototype is represented by the generic pricing engine rather than by one-off rental arithmetic:

- UGX 20,000 deposit;
- UGX 2,000 per hour;
- minimum one billable block;
- partial blocks round upward;
- unused escrow is refunded after return;
- usage above escrow becomes a customer surcharge;
- a later successful deposit first settles an existing surcharge;
- an authorised surcharge waiver is a write-off, not a money movement.

CPay collection/refund is always submitted through `PaymentOrchestrationService`, preserving provider routing, risk controls, payout controls, CPay transactions, ledger posting, webhooks and billing hooks.

## Rental state machine

The physical release state is deliberately separate from payment success:

```text
PAYMENT_PENDING
      |
      | CPay collection SUCCESS
      v
READY_TO_RELEASE
      |
      | atomic RELEASE_ASSET command claim
      v
RELEASE_PENDING
      |
      | authenticated OEM release callback
      | OR an operation explicitly certified as IMMEDIATE
      v
ACTIVE
      |
      | authenticated return / controlled return
      +-------------------------+
      |                         |
      v                         v
   SETTLED                 REFUND_PENDING
                                |
                                | CPay payout SUCCESS
                                v
                             SETTLED

Failure states:
PAYMENT_FAILED
RELEASE_FAILED
REFUND_FAILED
```

### Billing clock

`started_at` is now set when physical release is confirmed, not merely when the deposit succeeds. This avoids charging a customer for the time a cabinet spent waiting to eject an asset.

### Double-eject prevention

The release command reference is deterministic: `VEND-RELEASE-{rentalReference}`.

Before any OEM network call, CPay performs an atomic `INSERT IGNORE` command claim into `vending_commands`. The unique command reference is therefore a cross-instance idempotency gate. Two browser polls, worker races or application instances cannot both dispatch the same release command.

If the OEM accepts a callback-completed release, the rental remains `RELEASE_PENDING`. CPay does not repeatedly eject merely because another status poll arrives.

A failed deterministic release is not silently re-issued. Its command evidence remains claimed until an operator/OEM reconciliation determines whether another physical command is safe. This is preferable to discovering the meaning of “at least once” with two power banks on the floor.

## ChargeNow adapter

`ChargeNowVendingConnectorAdapter` executes configured OEM operations through CPay's shared outbound HTTP executor.

### Operation registry

Each operation is stored in `vending_connector_operations` and has:

- `command_type`, for example `RELEASE_ASSET` or `QUERY_STATUS`;
- HTTP method;
- command path;
- optional JSON request template;
- optional OEM idempotency header;
- response success field/value;
- response reference field;
- response message field;
- completion mode: `CALLBACK` or `IMMEDIATE`.

Paths and JSON text values can use:

```text
{{externalDeviceId}}
{{commandReference}}
{{rentalReference}}
{{merchantId}}
{{deviceId}}
```

Arbitrary command parameters are also available as placeholders. For example a status path can be configured as:

```text
/stations/{{externalDeviceId}}/status
```

JSON templates are parsed as JSON before string-node substitution. CPay is not constructing raw JSON by concatenating an unescaped device identifier and hoping everyone behaves.

### Outbound authentication

Supported contract modes are:

- `BEARER`
- `API_KEY_HEADER`
- `BASIC`
- `HMAC_SHA256_TS_BODY`
- `NONE`, only for localhost/loopback sandbox testing

HMAC mode supports:

- configurable signature header;
- configurable timestamp header;
- optional public/API-key header;
- `BASE64` or `HEX` signatures;
- configurable signing template.

Signing-template variables include:

```text
{{timestamp}}
{{method}}
{{path}}
{{body}}
{{commandReference}}
```

`{{path}}` is the **rendered** command path, not the unexpanded template.

OEM credentials and callback secrets are encrypted at rest with CPay's existing merchant-channel AES-GCM service. Connector list/view APIs return only configured/not-configured indicators, not clear secrets.

## Device callbacks

Manufacturer callbacks are received at:

```text
POST /api/v2/vending/device-callbacks/{connectorCode}/{merchantId}
```

Supported callback authentication modes are:

- `HMAC_SHA256_TS_NONCE_BODY`
- `HMAC_SHA256_TS_BODY`
- `HMAC_SHA256_BODY`
- `STATIC_TOKEN_HEADER`

HMAC signatures can be Base64 or hexadecimal. Timestamp-based modes enforce a five-minute skew window. Nonce mode persists a tenant/connector nonce and rejects replay. Signature/token comparison is constant-time.

Authenticated callbacks are also idempotent by manufacturer external event id and retained in `vending_device_callbacks` with the raw body hash, signature state, processing state and error detail.

Normalized events currently include:

- heartbeat/device-online/inventory;
- device offline;
- asset/power-bank released;
- asset/power-bank returned.

Unknown authenticated events are retained as unmapped evidence rather than coerced into a state transition.

## Callback correlation

A real OEM may not echo CPay's `rentalReference`. CPay therefore supports three ways to correlate release/return callbacks:

1. a configured rental-reference field;
2. a configured CPay command-reference field;
3. a configured OEM provider-reference field.

The latter two resolve through `vending_commands` to the tenant's rental. This keeps the callback engine compatible with partner contracts that return their own command ID instead of a CPay-specific rental identifier.

## Safe station diagnostics

Merchants can configure read-only OEM operations such as `QUERY_STATUS` and invoke them through the device probe API. The probe service whitelists only diagnostic command types:

```text
QUERY_STATUS
GET_STATUS
PING
INVENTORY_STATUS
```

`RELEASE_ASSET` is intentionally excluded. Physical release remains owned by the paid rental state machine.

Merchant endpoints include:

```text
GET  /api/v2/merchant-self-service/vending/connectors
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}
GET  /api/v2/merchant-self-service/vending/connectors/{connectorCode}/operations
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}/operations/{commandType}
GET  /api/v2/merchant-self-service/vending/connectors/{connectorCode}/callback-correlation
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}/callback-correlation
GET  /api/v2/merchant-self-service/vending/connectors/{connectorCode}/readiness
POST /api/v2/merchant-self-service/vending/connectors/{connectorCode}/rotate-callback-secret
POST /api/v2/merchant-self-service/vending/devices/{deviceCode}/probe
POST /api/v2/merchant-self-service/vending/devices/{deviceCode}/rotate-public-token
```

## Readiness gate

`GET .../connectors/{connectorCode}/readiness` evaluates whether CPay has enough configuration to enter an OEM sandbox. It checks the active connector, release operation, outbound auth policy and required callback mappings.

Possible states are:

- `NOT_CONFIGURED`
- `INCOMPLETE`
- `READY_FOR_OEM_SANDBOX`

`READY_FOR_OEM_SANDBOX` means CPay configuration is internally complete. It does **not** mean the manufacturer has certified the integration.

## Customer QR / hosted rental

Each station can be assigned a high-entropy public token. Its QR target is:

```text
GET /vending/rent/{publicToken}
```

The hosted journey:

1. loads location, pricing and current availability;
2. accepts the customer's mobile-money number/network;
3. starts the same tenant-scoped rental state machine used by merchant operations;
4. submits the deposit through CPay;
5. polls using a separate opaque status token stored only as a SHA-256 hash;
6. reports success only once the rental is `ACTIVE`.

Hosted starts use the database-backed rate limiter. Status sessions expire after 24 hours.

## Merchant and admin UI

The merchant Vending module now provides:

- locations and pricing policies;
- device registration and external OEM device ids;
- station QR target rotation;
- ChargeNow base URL and release operation configuration;
- Bearer/API-key/Basic/HMAC credentials;
- HMAC headers, encoding and signing template;
- OEM idempotency-header mapping;
- response and callback field mappings;
- callback command/provider-reference fallback mapping;
- sandbox readiness check;
- configurable `QUERY_STATUS` operation;
- per-device safe OEM status probe;
- rental list and synchronization.

The admin Vending module remains a cross-tenant monitoring surface for estate counts, rentals, offline devices, callbacks, commands and operational events.

## Security boundaries

- Merchant configuration remains under the existing merchant-session filter and browser CSRF protection.
- Admin vending routes inherit `/api/v2/admin/**` role enforcement and class-level `@PreAuthorize`.
- Provider callbacks are CSRF-exempt server-to-server calls but independently authenticated by the configured OEM callback contract.
- Public hosted routes expose only opaque station/session tokens and rate-limited actions.
- Customer MSISDN is hash/mask/encrypted-at-rest.
- OEM credentials/callback secrets are encrypted and redacted.
- Command evidence and callback evidence are retained for reconciliation and incident review.
- Physical release cannot be invoked through the generic diagnostic endpoint.

## Validation

The vending CI lane builds the frontend on Node 20 and 22, compiles/packages the Java 21 backend and runs targeted vending tests including:

- `VendingPricingEngineTest`
- `VendingCallbackSecurityServiceTest`
- `ChargeNowVendingConnectorAdapterTest`

The adapter tests cover configured release transport, callback-vs-immediate completion, OEM idempotency headers, rendered operation paths, Bearer authentication and HMAC-HEX authentication.

## Remaining external certification work

The CPay-side ChargeNow adapter is complete enough for the OEM sandbox. Production activation still requires the manufacturer's actual partner pack:

- sandbox and production base URLs;
- supported operations and exact paths;
- credential/token/signature rules;
- exact release request and acknowledgement contract;
- OEM idempotency semantics;
- status/diagnostic operation contract;
- callback authentication rules;
- event names and JSON paths;
- command/rental/provider reference echo behavior;
- return/asset/slot identifiers;
- timeout and retry rules;
- OEM error/status catalogue;
- sandbox account and test cabinet.

Once those are supplied, they are entered as tenant connector/operation configuration and tested against the cabinet. No rental/payment service rewrite should be required.

## Broader vending work after OEM certification

ChargeNow adapter completion does not magically finish every vending roadmap item, because software remains stubbornly composed of more than one checkbox. Separate follow-up work includes:

- distributed background synchronization for pending CPay collection/refund transactions;
- explicit operator reconciliation/retry workflow for ambiguous failed release commands;
- richer slot/asset inventory reconciliation where OEM callbacks expose concrete slot identifiers;
- billing suspend/resume and manual-settlement controls;
- Testcontainers/WireMock end-to-end tenant/concurrency coverage;
- vending-to-CPay ledger/provider-statement reconciliation views.
