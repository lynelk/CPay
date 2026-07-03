# Frontend Feature Modules

New work should be added under feature folders instead of increasing the older route/component sprawl.

Initial modules in this branch:

- `gateway-admin/` — channel visibility, webhook processing, reconciliation actions
- `merchant-self-service/` — merchant channel balances and future key/webhook management
- `reconciliation/` — unmatched provider record review
- `shared/api/` — API client wrappers for `/api/v2`

These modules are intentionally additive. Existing frontend routes remain untouched so the current portal can continue operating while the modern screens are wired into navigation and tested.
