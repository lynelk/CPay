# CPay ↔ CitoConnect Integration

CitoConnect uses CPay as its **Core Payments Service Engine**. All
payment traffic in CitoConnect flows through CPay; this document is the
contract between the two systems.

## Roles

| System         | Role                                                                |
|----------------|---------------------------------------------------------------------|
| **CitoConnect**| Origin — UI, transaction ledger, billing, reconciliation, webhooks  |
| **CPay**       | Engine — channel routing, provider gateways, signed REST surface    |

CitoConnect's `cpay` base44 function is the only caller of CPay's
`/api/v1` endpoints in the CitoTech stack. It signs outbound requests
with its merchant private key (PKCS#8 RSA, SHA256withRSA) and CPay
verifies them with the merchant's registered public key.

## Endpoint matrix used by CitoConnect

| CitoConnect operation | CPay endpoint                   | Direction       |
|-----------------------|---------------------------------|-----------------|
| `collect`             | `POST /api/v1/doMobileMoneyPayIn` | request → CPay |
| `payout`              | `POST /api/v1/doMobileMoneyPayOut`| request → CPay |
| `status`              | `POST /api/v1/doTransactionCheckStatus` | request → CPay |
| `balance`             | `POST /api/v1/doGetBalances`    | request → CPay |
| webhook reconciliation| `POST` to CitoConnect's `cpayWebhook` | callback ← CPay |

Channels handled natively: `mtn_momo`, `airtel_money`, `safaricom_mpesa`.
Channels handled by CitoConnect adapter modules under CPay
orchestration: `yo_payments`, `stripe`, `flutterwave`, `pesapal`.

## Canonical request shape

```json
{
  "amount":          50000,
  "description":     "Order #1234",
  "reference":       "TXN-1715234-A1B2C",
  "merchant_number": "256770000000",
  "payer_number":    "256771234567",
  "callback_url":    "https://citoconnect.example/api/cpayWebhook",
  "signature":       "<base64 RSA over canonical fields>"
}
```

The signature canonical string is `field1=value1&field2=value2&...` for
the fields above, in order, omitting any field that is null/empty. CPay
validates the signature in `SignatureVerificationService` before any
business logic runs.

## Webhook envelope back to CitoConnect

CPay normalises every provider callback into a single envelope before
forwarding to the merchant `callback_url`:

```json
{
  "reference":          "TXN-1715234-A1B2C",
  "transaction_status": "SUCCESS | FAILED | PENDING | PROCESSING",
  "provider":           "mtn_momo",
  "provider_payload":   { "...": "raw upstream body" },
  "signature":          "<base64 RSA over reference + status>"
}
```

CitoConnect's `cpayWebhook` function verifies the signature with
`CPAY_PUBLIC_KEY_PEM`, looks up the `Transaction` by `reference`, and
converges the canonical status field.

## Configuration on the CitoConnect side

Set the following secrets in the base44 environment for CitoConnect:

- `CPAY_BASE_URL`              – e.g. `https://cpay.citotech.net`
- `CPAY_MERCHANT_NUMBER`       – the merchant identifier issued by CPay
- `CPAY_SIGNING_KEY_PEM`       – PKCS#8 RSA private key (signing)
- `CPAY_PUBLIC_KEY_PEM`        – CPay's RSA public key (callback verify)
- `CPAY_DEFAULT_CALLBACK_URL`  – absolute URL of `cpayWebhook`

## Adding a new channel

1. Decide whether the channel is **native** (added as a Java
   `PaymentGateway` in CPay) or **adapted** (wired up inside the
   CitoConnect `cpay` function under a new `dispatchCollect`/
   `dispatchPayout` branch).
2. For native channels, register the gateway in CPay and surface its
   `merchant_number` mapping in the `Settings` table.
3. For adapted channels, add the channel name to
   `src/lib/cpay/channels.js` and `base44/entities/PaymentProvider.jsonc`,
   then implement the adapter functions in `base44/functions/cpay/entry.ts`.
4. Update both repos' docs and the channel matrix above.
