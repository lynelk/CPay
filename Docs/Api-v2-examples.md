# API v2 Examples

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

`POST /api/v2/admin/webhooks/12/rotate-secret`

Replay a failed/delivered webhook delivery:

`POST /api/v2/admin/webhooks/deliveries/99/replay?actor=ops-checker`
