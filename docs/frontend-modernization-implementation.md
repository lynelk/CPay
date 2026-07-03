# Frontend Implementation Summary

This tranche adds feature-based frontend scaffolding without removing the existing React portal.

## Added modules

- `clientside/src/shared/api/v2Client.js`
- `clientside/src/features/gateway-admin/GatewayAdminDashboard.jsx`
- `clientside/src/features/reconciliation/ReconciliationQueue.jsx`
- `clientside/src/features/merchant-self-service/MerchantBalances.jsx`

## Approach

The current frontend uses an older React and Ant Design stack. The safer path is additive:

1. Add new feature folders.
2. Add screens that consume `/api/v2`.
3. Wire them into existing navigation behind a feature flag.
4. Upgrade React and build tooling after backend CI and v2 APIs are stable.

## Follow-up wiring

The next frontend commit should add route entries for the new screens in the current router/menu structure after confirming the active portal navigation files.
