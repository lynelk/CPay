# API Versioning and Legacy Deprecation

CPay treats `/api/v2/**` as the merchant-facing API surface for new integrations. Legacy money-moving routes such as `/api/doPayIn`, `/api/doPayOut`, and old `/api/v1/**` routes remain available during migration but now emit deprecation metadata.

## Response Headers

Legacy responses include:

| Header | Purpose |
|---|---|
| `Deprecation: true` | Signals the route is supported only for migration. |
| `Sunset` | Configured by `CPAY_LEGACY_API_SUNSET_DATE`; default `2027-01-31`. |
| `Link` | Points merchants to the v2 signing and migration documentation. |

The headers are added by `LegacyApiDeprecationHeaderFilter`.

## Migration Policy

1. New payment, payout, balance, and native-provider capabilities must be added to `/api/v2/**` first.
2. Legacy endpoints may receive security and correctness fixes, but should not gain new product behavior.
3. Merchants should receive a dated migration window before the configured sunset date changes.
4. Any route removal must be reflected in `Changelog.md` and the OpenAPI contract.

Current v2 merchant-facing additions include `POST /api/v2/accounts/validate` and `GET /api/v2/statements`.

## Acceptance Checks

- Legacy routes return the deprecation headers.
- v2 routes do not return deprecation headers.
- The OpenAPI document and Postman collection stay aligned with implemented v2 routes.
