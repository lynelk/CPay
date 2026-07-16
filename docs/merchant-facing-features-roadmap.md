# Merchant-Facing Feature Roadmap

This roadmap tracks product gaps that affect merchant adoption and self-service.

## Implemented Foundation

| Capability | Route | Notes |
|---|---|---|
| Account name validation | `POST /api/v2/accounts/validate` | Signed v2 route; requires `ACCOUNT_VALIDATION`. |
| Statement export | `GET /api/v2/statements` | Signed v2 route; JSON by default, CSV with `format=csv`; requires `STATEMENT_EXPORT`. |
| Read audit | `merchants_audit_trail` | Account validation and statement export reads are recorded. |

## Remaining Product Tracks

| Area | Target |
|---|---|
| Payment links and hosted checkout | Add payment request/link objects that create a hosted checkout URL and call v2 collections. |
| Per-merchant test mode | Persist merchant/channel environment preferences so one merchant can test while another remains live. |
| Settlement scheduling | T+1 sweeps with minimum-balance retention and settlement statements. |
| Notifications | Merchant-configurable email/SMS preferences for failed callbacks, low float, and large payouts. |
| Webhook manager | Merchant UI/API to see attempts, replay failed events, rotate callback URLs, and rotate secrets. |
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
