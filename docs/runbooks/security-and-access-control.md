# Security and Access Control Runbook

## Purpose

This runbook defines the operational security controls required for CPay before broad market launch.

## Merchant API security

- v2 merchant requests must include signature version, timestamp, nonce, and signature headers.
- The canonical signature string must include method, path, canonical query, timestamp, nonce, and body hash.
- Nonces must be rejected after first use within the configured replay window.
- Idempotency keys should be used for payment submission retries.
- Merchant signup is rate limited and should be monitored for repeated attempts.

## Merchant channel setup security

- Merchant channel setup is managed through the merchant dashboard.
- Channel setup values are stored server-side and returned only in masked form.
- Channel setup must include endpoint URLs before readiness can be marked.
- Production execution should fail when required endpoint URLs are missing.
- Channel setup changes should remain auditable.

## Admin API security

- Admin routes are under `/api/v2/admin/**`.
- Admin credentials must not be shared between users.
- Sensitive admin actions should record audit events.
- Admin access must be restricted in production by network and operational role.
- Actuator access must remain separate from admin access.
- Operating-control summary access must be limited to authorized administrators.

## Browser and origin controls

- API access from browsers must be limited to configured trusted origins.
- Production deployments must set `CORS_ALLOWED_ORIGINS` to the approved merchant and admin portal origins.
- Wildcard browser origins should not be used in production.

## Callback security

- Callback delivery includes signature, nonce, and timestamp headers.
- Merchant callback receivers must reject stale timestamps and reused nonces.
- Callback signing values should be rotated before production onboarding and after any suspected exposure.
- Parked callbacks should only be requeued after the underlying issue is resolved.
- Callback workers use task claims before delivery to reduce duplicate processing in scaled deployments.

## Access-value handling

- Production access values must be stored in approved managed storage.
- Access values must not be committed to source control.
- Database-stored signing values should be encrypted or protected using database-level controls before broad launch.

## Release gate

A release is not security-ready until CI passes, access rules are reviewed, access values are confirmed outside source control, trusted origins are configured, operating-control review is available, and production admin access is limited to approved operators.
