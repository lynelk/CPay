# Production Code Controls

This document records code controls added for production operation and regulated payment readiness.

The controls focus on:

- provider endpoint execution
- provider execution evidence records
- callback processing at scale
- merchant onboarding controls
- operational event records
- rate limiting
- restricted CORS configuration
- formal compliance reporting endpoints

## Provider endpoint execution

Native provider adapters now call the configured provider endpoint path when a merchant channel setup includes endpoint values such as:

- `collectUrl`
- `payoutUrl`
- optional `authHeaderName`
- optional `authHeaderValue`

In production mode, provider endpoint values are required. In sandbox mode, the adapter can still accept the request for controlled testing when an endpoint has not yet been configured.

## Callback scaling

Callback processing now uses `callback_task_claims` so multiple workers can claim callback tasks without intentionally processing the same task at the same time. Claims are released after processing.

## Rate limiting

Merchant self-service signup uses database-backed rate limiting through `api_rate_limits`.

## Compliance reporting endpoints

Admin users can access reporting under:

```text
GET  /api/v2/admin/compliance/summary
GET  /api/v2/admin/compliance/report?from=YYYY-MM-DD&to=YYYY-MM-DD
POST /api/v2/admin/compliance/events/{id}/review?reviewedBy=name
```

These endpoints summarize and expose:

- open operating control events
- high-severity events
- provider endpoint runs
- failed provider endpoint runs
- parked callbacks
- merchant channel approval status
- reconciliation daily-close status

## Compliance note

These code controls support operational evidence and regulator-facing reporting, but legal and regulator approval still require human review, formal documentation, and regulator signoff.
