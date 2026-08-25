# API v2 Examples

All signed merchant examples use the v2 signing headers from `Docs/Api-v2-signing.md`.
When a merchant has both sandbox and production credentials, include one of:

```http
X-CPay-Environment: SANDBOX
X-CPay-Environment: PRODUCTION
```

Production requests are capped by default at 10 transactions per day while
`production_transaction_limit_enabled=true`; operations can change the cap in the admin portal.

## Collect

Adapter-native route:

`POST /api/v2/native/payments/collect`

Compatibility route:

`POST /api/v2/payments/collect`

```json
{
  "merchantNumber": "256770000000",
  "amount": "50000",
  "currency": "UGX",
  "country": "UG",
  "channel": "mtn_momo",
  "payer": {
    "type": "msisdn",
    "value": "256771234567"
  },
  "reference": "TXN-001",
  "description": "Order payment",
  "callbackUrl": "https://example.com/webhook"
}
```

## Payout

Adapter-native route:

`POST /api/v2/native/payments/payout`

Compatibility route:

`POST /api/v2/payments/payout`

```json
{
  "merchantNumber": "256770000000",
  "amount": "50000",
  "currency": "UGX",
  "country": "UG",
  "channel": "airtel_money",
  "payee": {
    "type": "msisdn",
    "value": "256751234567"
  },
  "reference": "PAYOUT-001",
  "description": "Merchant payout",
  "callbackUrl": "https://example.com/webhook"
}
```

## Channels

`GET /api/v2/channels`

Returns the registered adapter-backed channels and their supported capabilities.

Expected merchant-facing channel labels include `MTN MoMo`, `Airtel Money`, `Airtel OpenAPI`,
`Safaricom M-Pesa`, and `Yo! Payments`.

## Payment link

Create a tokenized hosted-checkout link:

`POST /api/v2/payment-links`

```json
{
  "merchantNumber": "256770000000",
  "amount": "25000",
  "currency": "UGX",
  "country": "UG",
  "reference": "LINK-001",
  "description": "Deposit payment",
  "channel": "yo_payments",
  "callbackUrl": "https://merchant.example.com/webhook",
  "expiresAt": "2026-08-13T12:00:00Z"
}
```

The response includes a `checkoutUrl`. The public hosted checkout route accepts customer payer
details:

`POST /api/v2/checkout/{token}/pay`

```json
{
  "payerAccount": "256770000000"
}
```

## Invoice / request-to-pay

Create an invoice:

`POST /api/v2/invoices`

```json
{
  "merchantNumber": "256770000000",
  "reference": "INV-2026-001",
  "customerName": "Acme Stores",
  "customerEmail": "finance@example.com",
  "amount": "75000",
  "currency": "UGX",
  "country": "UG",
  "description": "August subscription",
  "dueAt": "2026-08-20T17:00:00Z"
}
```

Operational routes:

- `GET /api/v2/invoices?merchantNumber=256770000000`
- `POST /api/v2/invoices/{reference}/actions/send`
- `POST /api/v2/invoices/{reference}/actions/cancel`

The public pay route is token based:

`POST /api/v2/invoices/pay/{token}`

```json
{
  "payerAccount": "256770000000",
  "channel": "mtn_momo"
}
```

---

## Maker-checker: reconciliation daily close

Requires admin credentials (Basic Auth per the OpenAPI admin security scheme).
The maker submits; a different actor must approve.

Submit (maker):

`POST /api/v2/admin/recon-finance/close?date=2026-08-02&currency=UGX&submittedBy=finance-maker`

```json
12
```

Approve (checker — must differ from the maker):

`POST /api/v2/admin/recon-finance/close/approve?date=2026-08-02&currency=UGX&approvedBy=finance-checker`

```json
{
  "code": "000",
  "closeDate": "2026-08-02",
  "currency": "UGX",
  "status": "CLOSED"
}
```

Reject (checker):

`POST /api/v2/admin/recon-finance/close/reject?date=2026-08-02&currency=UGX&rejectedBy=finance-checker&reason=statement%20missing`

```json
{
  "code": "000",
  "closeDate": "2026-08-02",
  "currency": "UGX",
  "status": "PENDING_APPROVAL"
}
```

Summary:

`GET /api/v2/admin/recon-finance/summary?currency=UGX`

## Maker-checker: settlement batch close

`POST /api/v2/admin/reconciliation/settlements/close?reference=SET-2026-08-02-001&closedBy=finance-maker`

`POST /api/v2/admin/reconciliation/settlements/close/approve?reference=SET-2026-08-02-001&approvedBy=finance-checker`

`POST /api/v2/admin/reconciliation/settlements/close/reject?reference=SET-2026-08-02-001&rejectedBy=finance-checker&reason=unmatched%20record`

## Payout approvals (maker-checker queue)

List the queue:

`GET /api/v2/admin/payout-approvals?limit=100`

Approve and re-execute the stored payout through the normal orchestrator path:

`POST /api/v2/admin/payout-approvals/42/approve?approvedBy=finance-checker`

Reject without executing:

`POST /api/v2/admin/payout-approvals/42/reject?rejectedBy=finance-checker&reason=duplicate`

Cancel before decision:

`POST /api/v2/admin/payout-approvals/42/cancel?cancelledBy=ops-checker`

## Webhook verification (callback test)

List a merchant's webhook endpoints:

`GET /api/v2/admin/webhooks/merchants/7`

Queue a synthetic TEST event (amount 0, status TEST) so a callback URL can be
verified before go-live:

`POST /api/v2/admin/webhooks/merchants/7/test-callback?eventType=payment.completed&actor=ops-checker`

```json
{
  "code": "000",
  "merchantId": 7,
  "eventType": "payment.completed",
  "queued": 1,
  "message": "Test event queued - watch the delivery log for this eventType"
}
```

Rotate a merchant's webhook signing secret (returned once):

`POST /api/v2/admin/webhooks/endpoints/12/rotate-secret`

Replay a failed/delivered webhook delivery:

`POST /api/v2/admin/webhooks/deliveries/99/replay?actor=ops-checker`
