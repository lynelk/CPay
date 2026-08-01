# Merchant-Facing Feature Roadmap

This roadmap tracks product gaps that affect merchant adoption and self-service.

## Implemented Foundation

| Capability | Route | Notes |
|---|---|---|
| Account name validation | `POST /api/v2/accounts/validate` | Signed v2 route; requires `ACCOUNT_VALIDATION`. |
| Statement export | `GET /api/v2/statements` | Signed v2 route; JSON by default, CSV with `format=csv`; requires `STATEMENT_EXPORT`. |
| Payment links and hosted checkout | `POST /api/v2/payment-links`, `GET /checkout/{token}` | Signed creation route; tokenized checkout calls v2 collection orchestration. |
| Settlement scheduling | `POST /api/v2/admin/reconciliation/settlements/schedule` | Hourly scheduler opens batches while retaining configured minimum balance. |
| Webhook manager | `Merchant Dashboard -> Webhooks` | Register/update endpoints per event type, rotate signing secrets (shown exactly once), inspect the delivery log, and replay failed deliveries — backed by `/api/v2/merchant-self-service/webhooks`. |
| Read audit | `merchants_audit_trail` | Account validation and statement export reads are recorded. |

## Remaining Product Tracks

| Area | Target |
|---|---|
| Per-merchant test mode | Persist merchant/channel environment preferences so one merchant can test while another remains live. |
| Notifications | Merchant-configurable email/SMS preferences for failed callbacks, low float, and large payouts. |
| Merchant roles | Owner, finance, developer, and viewer defaults enforced server-side. |
| Requests-to-pay | Payment request objects delivered through SMS or USSD. |
| Merchant analytics | Failure-reason analytics by payer, provider, merchant reference, and channel. |

## API Notes

New merchant-facing endpoints should:

- use `/api/v2/**`
- require canonical request signing
- require an explicit API privilege
- emit request IDs
- audit reads of sensitive financial or account data
- avoid exposing raw provider payloads unless explicitly marked diagnostic
