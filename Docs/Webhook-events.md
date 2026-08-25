# Webhook Event Registry

Webhook payloads should be versioned and event-driven instead of exposing raw status strings. Providers can still return their own codes internally, but merchant callbacks should use CPay event names.

## Envelope

```json
{
  "event_id": "evt_01J0Z000000000000000000000",
  "event_type": "payment.succeeded",
  "event_version": 1,
  "created_at": "2026-07-16T09:30:00Z",
  "merchant_id": "10003482",
  "data": {
    "transaction_id": "tx_01J0Z000000000000000000000",
    "merchant_reference": "order-123",
    "status": "SUCCESSFUL",
    "amount": {
      "currency": "UGX",
      "minor_units": 2480000
    }
  }
}
```

## Events

| Event | When Emitted |
|---|---|
| `payment.pending` | Collection is accepted for processing. |
| `payment.succeeded` | Collection has completed successfully. |
| `payment.failed` | Collection cannot complete. |
| `payout.pending` | Payout is accepted for processing. |
| `payout.succeeded` | Payout has completed successfully. |
| `payout.failed` | Payout cannot complete. |
| `refund.pending` | Refund is accepted. |
| `refund.succeeded` | Refund has completed successfully. |
| `refund.failed` | Refund cannot complete. |
| `invoice.issued` | One-off request-to-pay invoice is sent to the customer. |
| `callback.parked` | Delivery retries are exhausted or require operator action. |

## Delivery Contract

- Delivery must pass through the callback task queue.
- Payloads are signed with the merchant callback secret.
- Retries must preserve the same `event_id`.
- Provider callback handlers must deduplicate by provider reference plus terminal status.
- Event schemas are registered in code by event type/version; do not add a webhook event without a JSON schema and an example payload.

## Callback signature verification

New callback deliveries expose a versioned, independently verifiable HMAC contract.

Required callback headers:

- `X-CPay-Signature-Version: callback-v1`
- `X-CPay-Signature`: base64 HMAC-SHA256 signature
- `X-CPay-Timestamp`: Unix epoch seconds
- `X-CPay-Nonce`: unique callback-delivery nonce
- `X-CPay-Callback-Task-Id`: callback task id
- `X-CPay-Merchant-Id`: CPay merchant id
- `X-CPay-Reference`: merchant/payment reference used when the task was queued

For `callback-v1`, reconstruct the exact canonical string below using the raw HTTP request body as received:

```text
CALLBACK_TASK_ID
MERCHANT_ID
REFERENCE
TIMESTAMP
NONCE
RAW_REQUEST_BODY
```

Calculate `HMAC-SHA256(canonical_string, active_merchant_callback_secret)`, base64-encode the result,
and compare it to `X-CPay-Signature` using a constant-time comparison.

Receivers should also:

- reject unsupported signature versions;
- reject timestamps outside their configured replay window;
- reject a reused nonce/event id within the replay window;
- verify that the merchant id/reference agree with the receiving integration context;
- parse the body only after signature verification where practical;
- preserve the raw request bytes until verification is complete.

The signing-context headers are metadata rather than secrets and are required so receivers can
independently reconstruct and verify the callback signature.
