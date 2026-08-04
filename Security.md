# CPay Security Policy

CPay handles payment gateway functions, so access control and safe operations are important. This document explains the security expectations for the project in clear language.

## What must be protected

| Area | Why it matters |
|---|---|
| Merchant API access | Only approved merchant systems should submit payment requests. |
| Provider channel setup | Provider connection details and endpoint setup values must be handled carefully. |
| Admin operations | Internal actions such as callback retry, balance sync, operating-control review, and daily close should be limited to approved users. |
| Callback messages | Merchants should be able to confirm that callback messages came from CPay. |
| Finance records | Reconciliation and daily close actions should be traceable. |

## Core controls

CPay uses several controls to support safe operation:

- signed merchant API requests
- timestamp and nonce checks for v2 requests
- idempotency keys for safer retries, on both the legacy `/api/do*` and v2 request paths
- required email verification before a merchant's first login
- merchant signup rate limiting
- risk/fraud authorization on both the legacy and v2 pay-in/pay-out paths, including a KYC-tier-aware
  transaction/daily cap and a payer-velocity rule capping how often the same payer identifier can
  transact in a rolling window
- step-up MFA (fresh TOTP code) required for merchant payout batches above a configurable amount threshold
- maker-checker approval for reconciliation daily close, settlement batch close, and payouts above a
  configurable threshold — each requires a second admin, different from whoever opened/queued it, to
  approve or reject before it takes effect
- protected admin routes under `/api/v2/admin/**`, enforced at both the URL-path level and the method level (`@PreAuthorize`) as defense-in-depth
- protected monitoring routes under `/actuator/**`
- JDBC-backed admin and merchant portal sessions with a 15 minute timeout
- CSRF tokens for browser routes through `GET /auth/csrf`
- route-specific CSRF exemptions for legacy/API integration routes instead of a global CSRF disable
- restricted trusted origins for API access
- signed callback messages
- verified provider responses (e.g. Yo! Payments) before they are trusted
- merchant-safe error messages: raw provider responses and internal exceptions are translated to a stable, generic message before reaching a merchant, never echoed back directly
- centralized outbound provider transport with configured timeouts and no global TLS verification bypass
- merchant self-service webhook management from the merchant portal (`Merchant Dashboard -> Webhooks`: register, rotate secret, view delivery log, replay a failed delivery), scoped so one merchant can never act on another's webhook, and gated by the portal session-authorization filter alongside the other merchant self-service routes
- an admin-only webhook test-callback endpoint and audited admin delivery replay, so a delivery issue can be verified without waiting for a real event
- claim-based callback processing for scaled workers
- distributed locking (ShedLock) so scheduled jobs cannot process the same work twice across multiple instances
- provider callback terminal-state and provider-reference guards to reduce duplicate ledger/statement application on redelivery
- scheduled cleanup of short-lived API, reset-token, callback, webhook, provider-run, and session records
- encrypted merchant channel setup values, merchant RSA private keys (on a dedicated encryption key, separate from channel credentials, with a background sweep migrating legacy plaintext/shared-key rows onto it), and callback-signing secrets — each tolerant of legacy plaintext rows via a decrypt-with-fallback until the value is next rotated
- a report of how many merchants still rely on the shared fallback callback-signing secret instead of their own rotated one
- uniform upload validation (size/extension/content-type) on every multipart admin upload endpoint
- a tightened Content-Security-Policy with no unnecessary `unsafe-inline` script allowance and no localhost carve-out in production
- masked display values in the merchant portal
- a reusable PII-masking utility applied to the highest-traffic payer-number logging call sites
- an honest EFRIS e-receipt extension point (logs intent, does not call a real EFRIS endpoint) pending real EFRIS/URA credentials — do not treat this as a certified regulatory integration
- disabled-by-default OpenAPI and Swagger UI endpoints
- operational and readiness records

## Reporting concerns

Do not report sensitive concerns in public GitHub issues.

Use a private reporting route, such as a private GitHub security advisory if available, or contact the repository owner or maintainer through an approved private channel.

A useful report should include:

- what was found
- which feature or endpoint is affected
- likely impact
- suggested fix, if known

Do not include live customer data, payment details, or production access values in a report.

## Handling access values

Do not commit the following to the repository:

- `.env` files
- database access values
- provider access values
- merchant signing values
- callback signing values
- merchant channel encryption keys
- admin usernames or passwords
- actuator usernames or passwords
- production-only configuration values

Use environment variables or approved managed storage instead.

## Merchant API expectations

Merchant API requests should follow the documented signing process. For v2 requests, include the required signing headers, timestamp, nonce, and idempotency key where needed.

Requests should be rejected when they are stale, repeated, unsigned, incorrectly signed, or submitted by a merchant that is not allowed to use the requested API.

## Merchant channel setup expectations

Merchant channel setup values are stored server-side and returned only in masked form. Production channel setup should require approval before live use.

Each configured channel should include endpoint URLs and the required channel-specific setup values. Missing endpoint URLs should block production execution.

## Admin API expectations

Admin APIs are internal operational APIs. Production admin access should use strong access controls, restricted network access where possible, and audit records for important actions. Admin browser flows should obtain a CSRF token from `/auth/csrf`; integration API route groups are exempted individually where backward compatibility requires it.

High-risk admin actions include:

- callback signing-value rotation
- callback retry
- webhook test-callback dispatch and delivery replay
- balance synchronization
- provider statement validation
- reconciliation statement import and manual/auto-matching
- operating-control review
- finance review posting
- reconciliation daily close and settlement batch close, including maker-checker approval/rejection
- payout approval, rejection, and cancellation above the configured maker-checker threshold
- treasury position review
- regulator report generation
- KYC tier changes
- provider configuration changes

## Callback expectations

Merchant callbacks should be signed and include timestamp and nonce headers. Merchant systems should verify callback messages before acting on them.

Callback processing uses task claims to reduce duplicate delivery when multiple workers are running.

## Release checklist

Before production launch, confirm that:

- provider access values are configured outside the repository
- admin access values are strong and unique
- monitoring access is separate from admin access
- callback verification has been tested
- database access is restricted
- CORS settings are limited to approved origins
- provider TLS verification is enforced; development environments should use trusted local certificates or provider sandbox endpoints
- `SPRINGDOC_API_DOCS_ENABLED` and `SPRINGDOC_SWAGGER_UI_ENABLED` remain `false` unless a controlled environment explicitly enables them
- clustered deployments use shared nonce storage such as `CPAY_SECURITY_NONCE_STORE=jdbc`
- clustered deployments share one database so distributed scheduler locking (ShedLock) has something to coordinate through
- retention settings are explicitly configured for the deployment and reviewed against finance/compliance needs
- dependency and code checks pass or have documented exceptions; Dependabot pull requests are reviewed and merged rather than left open indefinitely
- operating-control review is available to administrators
- security, finance, compliance, and business owners have approved launch readiness

## Related documents

- `Readme.md`
- `Installation.md`
- `Contributing.md`
- `Docs/Production-code-controls.md`
- `Docs/Readiness/Market-readiness-gates.md`
- `Docs/Runbooks/Security-and-access-control.md`
- `Docs/Runbooks/Production-incident-response.md`
