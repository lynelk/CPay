# CPay API Route Hardening - 25 August 2026

This note records route changes made after tracing Redocly ambiguous-path findings back to the actual Spring MVC mappings under `InitializrSpringbootProjectFresh/src/main/java/net/citotech/cito/`.

## Route changes

| Area | Previous route | Canonical route | Reason |
| --- | --- | --- | --- |
| Invoice send | `POST /api/v2/invoices/{reference}/send` | `POST /api/v2/invoices/{reference}/actions/send` | Prevents the merchant action route from overlapping the public `POST /api/v2/invoices/pay/{token}` route. |
| Admin webhook secret rotation | `POST /api/v2/admin/webhooks/{endpointId}/rotate-secret` | `POST /api/v2/admin/webhooks/endpoints/{endpointId}/rotate-secret` | Gives endpoint administration a static namespace and removes overlap with `/webhooks/merchants/{merchantId}`. |
| Compliance case decision | `POST /api/v2/admin/compliance/cases/{id}/decision` using a numeric internal ID | `POST /api/v2/admin/compliance/cases/{caseReference}/decision` | Retires the duplicate legacy mapping and standardizes the public administrative workflow on the auditable case reference. |

## Compatibility notes

The public invoice payment route `GET/POST /api/v2/invoices/pay/{token}` is unchanged. Existing invoice payment URLs therefore remain valid.

No compatibility aliases are retained for the replaced ambiguous routes because an alias would recreate the same path-template ambiguity that this change removes. Merchant and administrative clients using the previous invoice-send or webhook-secret-rotation route must move to the canonical routes above.

Compliance clients should use `caseReference` rather than the database row ID. The canonical compliance case endpoints support the richer case workflow and event history exposed by `ComplianceKybKycController`.

## Contract policy

The OpenAPI contract is updated in the same change as the Spring mappings. Redocly continues to treat unresolved references, ambiguous paths, duplicate operation IDs, missing operation IDs, missing summaries, invalid path declarations, and structural specification errors as blocking errors.
