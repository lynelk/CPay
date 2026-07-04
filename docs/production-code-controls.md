# Production Code Controls

This document records the code controls added to support production operation, scaling, and regulated readiness.

## Scope

These controls improve the software posture of CPay. They do not replace legal review, provider certification, finance signoff, external security review, or formal production approval.

## Implemented controls

| Area | Code support |
|---|---|
| Provider connection | Adapter-backed channels can call configured provider endpoint URLs through `ProviderEndpointClient`. |
| Merchant channel setup | Channel setup now requires endpoint URLs and channel-specific setup values before readiness can be marked. |
| Native v2 processing | Native v2 requests load merchant channel setup values into adapter metadata. |
| Callback scaling | Callback delivery uses `callback_task_claims` so workers claim tasks before processing. |
| Signup protection | Merchant signup uses database-backed request-window tracking through `api_rate_limits`. |
| Origin control | API routes use configured trusted origins. |
| Operating oversight | Admin users can review open operating-control counts through `/api/v2/admin/operating-controls/summary`. |

## Provider endpoint execution

Provider adapters for MTN, Airtel, Airtel OpenAPI, and Safaricom now call `ProviderEndpointClient`. The endpoint client:

- reads endpoint URLs from merchant channel setup
- applies connection and read timeouts
- sends a structured JSON request
- records the HTTP status in the gateway response
- rejects missing endpoint URLs in production mode

Real provider sandbox and production certification are still required before live traffic.

## Merchant channel setup

Each merchant channel setup must include:

- `collectUrl`
- `payoutUrl`
- channel-specific setup values
- optional request header name and value where a sandbox requires one

Stored setup values are encrypted server-side and returned to the portal only as masked values.

## Callback processing at scale

Callback workers now claim due callback tasks before delivery. The claim flow reduces duplicate processing when more than one application worker is running.

## Signup protection

Merchant signup is protected by database-backed request counting. The current signup limit is five attempts per minute per client source.

## Operating-control visibility

The admin operating-control summary endpoint reports open high, medium, low, and total operating-control events.

## Production note

The codebase now supports stronger production controls, but final launch still requires provider, security, compliance, finance, and regulator signoff. Code can support evidence. It cannot issue approvals.
