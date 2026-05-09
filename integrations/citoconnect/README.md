# CitoConnect ↔ CPay Integration Bundle

This directory holds everything that CitoConnect (and other Node/JS
clients) need in order to call CPay as the Core Payments Service Engine.

- [`cpay-client.js`](./cpay-client.js) — reference Node.js client that
  signs requests and verifies webhook callbacks. CitoConnect's `cpay`
  base44 function uses the same shape.
- [`../../docs/citoconnect-integration.md`](../../docs/citoconnect-integration.md) — protocol
  contract, channel matrix, and configuration checklist.

## Getting started

1. Register the merchant in CPay (`merchant_number`).
2. Generate an RSA keypair on the merchant side. Upload the **public**
   key into CPay's merchant settings and keep the **private** key secret.
3. Get CPay's RSA **public** key for verifying inbound webhooks.
4. Configure environment variables on the merchant side:

   ```
   CPAY_BASE_URL=https://cpay.citotech.net
   CPAY_MERCHANT_NUMBER=256770000000
   CPAY_SIGNING_KEY_PEM="-----BEGIN PRIVATE KEY-----…"
   CPAY_PUBLIC_KEY_PEM="-----BEGIN PUBLIC KEY-----…"
   CPAY_DEFAULT_CALLBACK_URL=https://merchant.example/cpay-webhook
   ```

5. Use `createCPayClient(...)` to call `collect`, `payout`, `status`, and
   `balances`.

## Mapping to CitoConnect

Inside CitoConnect, the equivalent surface is the `@/lib/cpay` SDK and
the `base44/functions/cpay` engine — see CitoConnect's
`docs/cpay-integration.md`.
