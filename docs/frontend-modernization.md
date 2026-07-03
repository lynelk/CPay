# Frontend Modernization

This branch introduces the backend API surfaces needed by the modern merchant and admin portal work:

- `GET /api/v2/merchant/balances`
- `GET /api/v2/admin/gateways/channels`
- `POST /api/v2/admin/gateways/webhooks/process-due`
- `POST /api/v2/admin/gateways/reconciliation/auto-match`
- `GET /api/v2/admin/reconciliation/unmatched`

## Modernization direction

The current portal remains in place for compatibility. The recommended frontend migration path is incremental:

1. Move new screens into a feature-based structure.
2. Add a typed API client around `/api/v2`.
3. Build new merchant self-service and gateway admin pages beside the existing UI.
4. Upgrade React and UI libraries after the new API surfaces are validated.

## Proposed feature folders

```text
clientside/src/features/merchant-self-service/
clientside/src/features/gateway-admin/
clientside/src/features/reconciliation/
clientside/src/features/webhook-operations/
clientside/src/shared/api/
```

## Screens to add first

- Merchant balances by channel
- Gateway/channel status
- Webhook retry queue
- Reconciliation unmatched queue
- Manual reconciliation action

Do not remove existing frontend routes until the new screens are tested and released behind a configuration flag.
