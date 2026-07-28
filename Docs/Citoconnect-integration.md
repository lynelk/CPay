# CPay ↔ CitoConnect Integration

CitoConnect uses CPay as its **Core Payments Service Engine**. All
payment traffic in CitoConnect flows through CPay; this document is the
contract between the two systems.

## Roles

| System | Role |
|---|---|
| **CitoConnect** | Origin system: UI, transaction ledger, billing, reconciliation, and merchant-facing webhooks |
| **CPay** | Engine: channel routing, provider gateways, signed REST surface, provider callback handling, and network balance checks |

CitoConnect's `cpay` base44 function is the primary caller of CPay's
`/api/v1` endpoints. It signs outbound requests
with its merchant private key (PKCS#8 RSA, SHA256withRSA) and CPay
verifies them with the merchant's registered public key.

## Endpoint matrix used by CitoConnect

| CitoConnect operation | CPay endpoint | Direction |
|---|---|---|
| `collect` | `POST /api/v1/doMobileMoneyPayIn` | request → CPay |
| `payout` | `POST /api/v1/doMobileMoneyPayOut` | request → CPay |
| `status` | `POST /api/v1/doTransactionCheckStatus` | request → CPay |
| `balance` | `POST /api/v1/doGetBalances` | request → CPay |
| webhook reconciliation | `POST` to CitoConnect's `cpayWebhook` | callback ← CPay |

Channels handled natively today: `mtn_momo`, `airtel_money`, `safaricom_mpesa`.
Channels handled by CitoConnect adapter modules under CPay orchestration:
`yo_payments`, `stripe`, `flutterwave`, `pesapal`.

## Canonical request shape

```json
{
  "amount": 50000,
  "description": "Order #1234",
  "reference": "TXN-1715234-A1B2C",
  "merchant_number": "256770000000",
  "payer_number": "256771234567",
  "callback_url": "https://citoconnect.example/api/cpayWebhook",
  "signature": "<base64 RSA signature>"
}
```

## Current signature contract

CPay's current Java API verifies signatures using legacy concatenated field
values. Client libraries must use the exact field order below until the backend
is migrated to a versioned canonical-signature contract.

| Operation | Signed data |
|---|---|
| `collect` | `merchant_number + payer_number + amount + reference + description` |
| `payout` | `merchant_number + payee_number + amount + reference + description` |
| `status` | `merchant_number + reference` |
| `balance` | `merchant_number` |

Example collect signing string:

```text
25677000000025677123456750000TXN-1715234-A1B2COrder #1234
```

The reference client at `Integrations/Citoconnect/cpay-client.js` implements
this contract and should be treated as the source of truth for Node/JS callers.

## v2 migration path

CPay now exposes `/api/v2` and `/api/v2/native/payments/*` routes with a safer canonical signature string that includes method, path, query, timestamp, nonce, and request body hash. Existing CitoConnect calls can remain on `/api/v1` while the v2 migration is staged. New merchant integrations should prefer the documented v2 signing contract in `Docs/Api-v2-signing.md`.

## Webhook envelope back to CitoConnect

CPay normalises provider callbacks into a single envelope before forwarding to
the merchant `callback_url`:

```json
{
  "reference": "TXN-1715234-A1B2C",
  "transaction_status": "SUCCESS | FAILED | PENDING | PROCESSING",
  "provider": "mtn_momo",
  "provider_payload": { "...": "raw upstream body" },
  "signature": "<base64 RSA over reference + status>"
}
```

CitoConnect's `cpayWebhook` function verifies the signature with
`CPAY_PUBLIC_KEY_PEM`, looks up the `Transaction` by `reference`, and converges
the canonical status field.

## Configuration on the CitoConnect side

Set the following secrets in the base44 environment for CitoConnect:

- `CPAY_BASE_URL` – e.g. `https://cpay.coresynergi.es`
- `CPAY_MERCHANT_NUMBER` – the merchant identifier issued by CPay
- `CPAY_SIGNING_KEY_PEM` – PKCS#8 RSA private key used for signing
- `CPAY_PUBLIC_KEY_PEM` – CPay's RSA public key used for callback verification
- `CPAY_DEFAULT_CALLBACK_URL` – absolute URL of `cpayWebhook`

## Adding a new payment channel

The preferred path is to add native CPay support through a Java adapter and then
surface the channel to CitoConnect. For aggregator-only experiments, an adapted
channel can live in CitoConnect first and later graduate into CPay.

1. Decide whether the channel is **native** or **adapted**.
2. For native channels, implement a CPay adapter using the `gateway` package
   scaffold.
3. Register the channel code, supported countries, currencies, operations, and
   routing priority in the database/settings layer.
4. Add merchant-level credentials and charge overrides where required.
5. Add the channel to CitoConnect's provider/channel list.
6. Add contract tests for collect, payout, status, balance, callbacks, and
   failure handling.
7. Update this matrix and the merchant integration guide.

## Channel checklist

Every new channel should define:

- channel code, for example `yo_payments`, `flutterwave`, or `pesapal`
- country and currency support
- supported operations: collect, payout, balance, status, refund, callback
- sandbox and production credential names
- callback verification method
- provider reference mapping
- retry and timeout behaviour
- min/max amount limits
- merchant charge model
- reconciliation export format
