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
| `callback.parked` | Delivery retries are exhausted or require operator action. |

## Delivery Contract

- Delivery must pass through the callback task queue.
- Payloads are signed with the merchant callback secret.
- Retries must preserve the same `event_id`.
- Provider callback handlers must deduplicate by provider reference plus terminal status.
