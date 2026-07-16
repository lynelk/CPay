# Pagination Standard

List APIs should use one cursor-based convention. Offset pagination can remain on old admin screens, but new API routes should not mix `LIMIT n`, `LIMIT offset,n`, and string-built page parameters.

## Request

```http
GET /api/v2/payments/collections?limit=50&cursor=eyJjcmVhdGVkX29uIjoiMjAyNi0wNy0xNlQwOTowMDowMFoiLCJpZCI6MTIzfQ
```

| Parameter | Rule |
|---|---|
| `limit` | Optional; default `50`, maximum `200`. |
| `cursor` | Optional opaque token returned by the previous page. |
| Filters | Typed parameters only; never string-concatenated SQL fragments. |

## Response

```json
{
  "data": [],
  "page": {
    "limit": 50,
    "next_cursor": null,
    "has_more": false
  }
}
```

## Ordering

Default ordering should be deterministic: `created_on DESC, id DESC`. Cursor payloads should contain the ordered columns and must be signed or encoded as opaque values so clients do not depend on internal schema.

## Acceptance Checks

- Every list endpoint validates `limit`.
- Cursor parameters are optional and opaque.
- Queries use bind parameters.
- The response includes `has_more` even when there is no next page.
