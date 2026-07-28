# CPay SDKs

These SDK files implement the merchant-facing CPay API v2 signing contract from `docs/api-v2-signing.md` and provide small client wrappers for common payment operations.

They are intentionally small and dependency-light so merchants can copy them into existing integrations or package them into their own build system.

## Files

| Runtime | File | Purpose |
|---|---|---|
| Node.js | `node/cpay-signing.js` | Canonical request signing and idempotency headers. |
| Node.js | `node/cpay-client.js` | Signed collect, payout, account validation, statement, and payment-link calls. |
| Python | `python/cpay_signing.py` | Canonical request signing and webhook verification primitives. |
| Python | `python/cpay_client.py` | Signed collect, payout, account validation, statement, and payment-link calls. |
| PHP | `php/CPaySigning.php` | Canonical request signing for merchant integrations. |
| PHP | `php/CPayClient.php` | Signed collect, payout, account validation, statement, and payment-link calls. |

All helpers return the required v2 headers:

- `X-CPay-Merchant-Number`
- `X-CPay-Signature-Version`
- `X-CPay-Timestamp`
- `X-CPay-Nonce`
- `X-CPay-Signature`
- `X-CPay-Idempotency-Key` when supplied or generated

## Node.js

```js
const { CPayClient } = require("./node/cpay-client");

const client = new CPayClient({
  baseUrl: "https://cpay.example.com",
  merchantNumber: "1000003",
  privateKeyPem: process.env.CPAY_PRIVATE_KEY
});

await client.collect({
  amount: "1000",
  currency: "UGX",
  country: "UG",
  reference: "INV-100",
  description: "Invoice 100",
  callbackUrl: "https://merchant.example.com/callback",
  payer: { type: "MSISDN", value: "256770000000" }
});
```

## Python

```python
from cpay_client import CPayClient

client = CPayClient(
    base_url="https://cpay.example.com",
    merchant_number="1000003",
    private_key_pem=open("private.pem").read(),
)
client.payout({
    "amount": "1000",
    "currency": "UGX",
    "country": "UG",
    "reference": "PAY-100",
    "description": "Supplier payout",
    "callbackUrl": "https://merchant.example.com/callback",
    "payee": {"type": "MSISDN", "value": "256770000000"},
})
```

## PHP

```php
$client = new CPayClient('https://cpay.example.com', '1000003', file_get_contents('private.pem'));
$client->createPaymentLink([
    'amount' => '1000',
    'currency' => 'UGX',
    'country' => 'UG',
    'description' => 'Invoice 100',
    'callbackUrl' => 'https://merchant.example.com/callback',
]);
```
