# Security and Authentication Roadmap

This document tracks high-impact controls that require coordinated backend, frontend, and operational rollout.

## Controls Already Started

| Control | Current State |
|---|---|
| Nonce store | Defaults to JDBC through `CPAY_SECURITY_NONCE_STORE=jdbc`. |
| Security headers | Spring Security now sets CSP, HSTS, frame, content-type, and referrer headers. |
| CORS | API headers are explicit; wildcard headers are no longer used. |
| Production guard | Production profiles reject sandbox mode and SSL verification bypass. |
| Upload validation | Spreadsheet uploads enforce file type, MIME type, size, and row-count limits. |
| Webhook self-service scoping | Merchant webhook register/rotate/replay/delivery-log routes are merchant-session-scoped and covered by the portal session-authorization filter (107 envelope when unauthenticated). |

## Next Controls

| Area | Target |
|---|---|
| Password reset | Replace six-digit reusable codes with single-use URL-safe tokens hashed at rest. |
| MFA | Require TOTP for admin users first, then optional merchant MFA. |
| Permissions | Convert flat privilege strings to server-enforced roles and permissions. |
| Sessions | Add absolute lifetime, device list, and revoke-all on password change. |
| Merchant RSA keys | Encrypt private keys at rest or move signing to an HSM-backed service. |
| Account lockout | Add progressive delay and lockout notification on login and reset endpoints. |

## Acceptance Checks

- Reset tokens cannot be replayed.
- Admin MFA enrollment and recovery are auditable.
- Permission checks are enforced server-side for v2 admin APIs.
- Password change invalidates existing sessions.
