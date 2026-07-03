# CPay API v1 Compatibility Contract

The `/api/v1` endpoints are preserved for existing merchants and integrations.

## Stable endpoints

- `POST /api/v1/doMobileMoneyPayIn`
- `POST /api/v1/doMobileMoneyPayOut`
- `POST /api/v1/doTransactionCheckStatus`
- `POST /api/v1/doGetBalances`

## Compatibility rules

- Do not rename existing `/api/v1` endpoints.
- Do not remove existing `/api/v1` request fields.
- Do not change legacy signing rules for `/api/v1`.
- Do not change successful response shape without a separately versioned endpoint.
- Add new behavior under `/api/v2` first.
- Any shared service refactor must pass v1 compatibility tests before merge.

## Migration posture

`/api/v2` may evolve and gain stronger signing, idempotency, normalized responses, and adapter-driven routing. `/api/v1` remains supported until merchants migrate voluntarily.
