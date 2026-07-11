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
- idempotency keys for safer retries
- merchant signup rate limiting
- protected admin routes under `/api/v2/admin/**`
- protected monitoring routes under `/actuator/**`
- JDBC-backed admin and merchant portal sessions with a 15 minute timeout
- CSRF tokens for browser routes through `GET /auth/csrf`
- route-specific CSRF exemptions for legacy/API integration routes instead of a global CSRF disable
- restricted trusted origins for API access
- signed callback messages
- claim-based callback processing for scaled workers
- encrypted merchant channel setup values
- masked display values in the merchant portal
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
- balance synchronization
- provider statement validation
- operating-control review
- finance review posting
- reconciliation daily close
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
- `CUSTOM_SSL_SKIP_VERIFY` is `false`
- `SPRINGDOC_API_DOCS_ENABLED` and `SPRINGDOC_SWAGGER_UI_ENABLED` remain `false` unless a controlled environment explicitly enables them
- clustered deployments use shared nonce storage such as `CPAY_SECURITY_NONCE_STORE=jdbc`
- dependency and code checks pass or have documented exceptions
- operating-control review is available to administrators
- security, finance, compliance, and business owners have approved launch readiness

## Related documents

- `README.md`
- `INSTALLATION.md`
- `CONTRIBUTING.md`
- `docs/production-code-controls.md`
- `docs/readiness/market-readiness-gates.md`
- `docs/runbooks/security-and-access-control.md`
- `docs/runbooks/production-incident-response.md`
