# Cito Security Architecture

## Purpose

Cito uses a layered, fail-closed security model for a multi-tenant platform. CPay is the payments module inside Cito and retains payment-specific security contracts such as `X-CPay-*` signing headers, payment nonces, payment idempotency keys, provider credentials, ledger controls, and payment callback verification.

Security controls are designed so that compromise or misconfiguration of one layer does not automatically bypass the remaining layers.

## Security layers

### 1. Edge and transport

- Production application URLs must use HTTPS.
- Production session cookies are `Secure`, `HttpOnly`, and `SameSite=Lax`.
- HSTS is enabled for one year, includes subdomains, and advertises preload eligibility.
- CORS is allowlist-based. Production must explicitly configure HTTPS origins and may not use wildcard or loopback origins.
- Forwarded client-IP data is trusted only from explicitly configured proxy IPs through the existing CPay trusted-proxy configuration.

### 2. Browser isolation and response hardening

The primary Spring Security chain sends:

- Content Security Policy with `default-src 'self'` and `object-src 'none'`.
- `X-Content-Type-Options: nosniff`.
- `Referrer-Policy: same-origin`.
- `X-Frame-Options: SAMEORIGIN` plus CSP `frame-ancestors`.
- `Permissions-Policy` disabling camera, microphone, geolocation and USB while permitting the payment feature only from the same origin.
- `Cross-Origin-Opener-Policy: same-origin`.
- `Cross-Origin-Resource-Policy: same-origin`.
- `X-Permitted-Cross-Domain-Policies: none`.

Framework error responses are configured not to expose stack traces, exception classes, binding details, or internal messages.

### 3. Authentication and credential protection

- New operator and merchant passwords are BCrypt hashes at cost 12.
- Legacy SHA-256 hashes remain verifiable only for migration compatibility and are upgraded on successful legacy login through the existing migration flow.
- Existing lower-cost BCrypt hashes remain verifiable, and `PasswordUtils.needsRehash` can identify them for progressive re-costing.
- Login attempts are rate-limited in the shared database, not in one application process.
- Account and source-IP budgets are separate. A successful login resets the account budget but cannot clear the network spraying budget.
- MFA services remain available for administrators and merchant users.
- Password changes/resets revoke indexed Spring Sessions through the existing session revocation service.

### 4. Session security

- Spring Session JDBC remains the shared session store for multi-instance deployments.
- The primary chain creates sessions only when required rather than for signed API traffic.
- Spring Security migrates the session identifier during authentication, while legacy login flows retain their explicit invalidate-and-recreate protection.
- Existing absolute session lifetime enforcement remains in force in addition to inactivity timeout.
- Production cookies cannot be sent over plaintext HTTP.

### 5. Authorization and tenancy

- URL authorization remains deny-by-default.
- Method security remains enabled.
- Cito merchant feature entitlements are evaluated inside the Spring Security filter chain.
- The legacy-session bridge remains narrowly responsible for translating established portal sessions into Spring Security identities.
- Admin-only API groups require `ROLE_ADMIN`; actuator endpoints require `ROLE_ACTUATOR`.
- Tenant-scoping and permission services remain separate from authentication so identity alone does not imply access to another merchant's data.

### 6. CPay payment request security

Payment-facing compatibility contracts stay under CPay. They include:

- `X-CPay-Merchant`
- `X-CPay-Signature`
- `X-CPay-Timestamp`
- `X-CPay-Nonce`
- `X-CPay-Environment`
- `X-CPay-Idempotency-Key`
- durable nonce/replay protection
- callback signature verification
- provider credential encryption
- payment idempotency
- payment risk, payout approval, reconciliation and ledger controls

These names must not be rebranded merely for cosmetic consistency because they are integration contracts and correctly belong to the CPay payments module.

### 7. Secrets and production fail-closed checks

When a `prod` or `production` Spring profile is active, startup now fails if any of the following are true:

- gateway state is not `PRODUCTION`;
- nonce storage is in-memory;
- `APP_BASE_URL` is not an absolute HTTPS URL;
- production CORS origins are missing, wildcard, non-HTTPS, or loopback;
- actuator/admin API passwords are shorter than 16 characters, obvious placeholders, or equal to their usernames;
- actuator and admin API identities or passwords are shared;
- callback signing or merchant encryption secrets are shorter than 32 characters or obvious placeholders.

This turns dangerous configuration from an operator warning into a deployment failure.

## Operational controls already present and retained

The review found and retained several strong existing controls:

- request correlation IDs;
- structured logging;
- PII masking helpers;
- JDBC nonce storage and replay protection;
- callback verification;
- idempotency services;
- merchant key encryption/re-encryption support;
- HSM configuration hooks;
- MFA services;
- session revocation;
- audit services and audit-chain support;
- distributed scheduler locking;
- provider circuit breaking;
- production/sandbox environment controls;
- maker-checker controls in financial workflows.

The implementation deliberately evolves these controls instead of replacing them with duplicate mechanisms.

## Remaining security evolution

These are architectural follow-ons rather than reasons to postpone the current hardening:

1. Move human administrator access completely away from shared HTTP Basic credentials and toward per-user identity plus MFA/passkeys. Keep the Basic path only as a time-bounded compatibility mechanism until dependent automation is migrated.
2. Add WebAuthn/passkey support for privileged users and step-up authentication for high-risk actions such as payout approval, key rotation, role changes, production promotion and impersonation.
3. Move all production secrets to managed secret/KMS/HSM-backed storage and automate rotation with overlap windows.
4. Add risk-based authentication using device/session context and impossible-travel/anomalous-login signals without making IP geolocation a sole decision factor.
5. Add explicit security-event schemas and SIEM export for authentication failures, MFA changes, key lifecycle, privilege changes, impersonation and payment-signature failures.
6. Add dependency/SBOM, secret scanning, SAST and container scanning as required CI gates, with signed build provenance for production artifacts.
7. Run periodic tenant-isolation, authorization and payment-signature penetration tests against a production-like staging environment.

## Validation expectations

Every security change should pass:

```bash
cd InitializrSpringbootProjectFresh
mvn test
mvn verify
```

Frontend changes should pass:

```bash
cd clientside
npm ci
npm run typecheck
npm run lint
npm test
npm run build
```

Production deployment must additionally verify HTTPS, secure cookie behavior, CORS allowlists, session invalidation, MFA, signed CPay requests, replay rejection, tenant isolation, audit generation, callback signatures and payment idempotency.
