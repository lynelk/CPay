# Security and Access Control Runbook

## Purpose

This runbook defines the operational security controls required for CPay before broad market launch.

## Merchant API security

- v2 merchant requests must include signature version, timestamp, nonce, and signature headers.
- The canonical signature string must include method, path, canonical query, timestamp, nonce, and body hash.
- Nonces must be rejected after first use within the configured replay window.
- Idempotency keys should be used for payment submission retries.

## Admin API security

- Admin routes are under `/api/v2/admin/**`.
- Admin credentials must not be shared between users.
- Sensitive admin actions should record audit events.
- Admin access must be restricted in production by network and operational role.
- Actuator access must remain separate from admin access.

## Callback security

- Callback delivery includes signature, nonce, and timestamp headers.
- Merchant callback receivers must reject stale timestamps and reused nonces.
- Merchant callback signing secrets should be rotated before production onboarding and after any suspected exposure.
- Parked callbacks should only be requeued after the underlying issue is resolved.

## Secrets handling

- Production credentials must be stored in approved secret storage.
- Secrets must not be committed to source control.
- Database-stored merchant callback secrets should be encrypted or protected using database-level controls before broad launch.

## Release gate

A release is not security-ready until CI passes, access rules are reviewed, secrets are confirmed outside source control, and production admin access is limited to approved operators.
