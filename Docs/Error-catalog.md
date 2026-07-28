# Error Catalog

The public API should return stable machine-readable error codes instead of numeric strings with ad hoc messages. This catalog defines the target shape for v2 and the compatibility mapping for legacy responses.

## Error Shape

```json
{
  "code": "PAYMENT_INSUFFICIENT_FUNDS",
  "category": "business_rule",
  "message": "The merchant balance is not sufficient for this payout.",
  "retryable": false,
  "request_id": "b971f3d2-cf1d-4db0-bd84-6a0c690f2e84",
  "docs_url": "https://github.com/lynelk/CPay/blob/main/Docs/Error-catalog.md#payment_insufficient_funds"
}
```

## Categories

| Category | Meaning | Retry Guidance |
|---|---|---|
| `authentication` | Signature, credential, nonce, or token failure. | Retry only after credentials are corrected. |
| `validation` | Request shape or field value is invalid. | Correct the request before retrying. |
| `business_rule` | The request is valid but blocked by balance, status, or policy. | Retry only after state changes. |
| `provider` | Provider rejected or did not complete the request. | Retry according to provider state and idempotency rules. |
| `system` | CPay could not complete the operation. | Retryable when marked true. |

## Initial Stable Codes

| Code | Category | Retryable | Legacy Equivalent |
|---|---|---:|---|
| `AUTH_SIGNATURE_INVALID` | `authentication` | false | `121`, signature failed |
| `AUTH_NONCE_REPLAYED` | `authentication` | false | duplicate nonce |
| `REQUEST_INVALID` | `validation` | false | `102`, missing or invalid fields |
| `PAYMENT_INSUFFICIENT_FUNDS` | `business_rule` | false | insufficient float or merchant balance |
| `PAYMENT_DUPLICATE_REFERENCE` | `business_rule` | false | duplicate transaction reference |
| `ACCOUNT_VALIDATION_REJECTED` | `business_rule` | false | account name lookup failed or merchant not allowed |
| `STATEMENT_EXPORT_REJECTED` | `business_rule` | false | statement export request failed or merchant not allowed |
| `PROVIDER_TIMEOUT` | `provider` | true | gateway timeout |
| `PROVIDER_DECLINED` | `provider` | false | provider failed transaction |
| `SYSTEM_UNAVAILABLE` | `system` | true | uncaught server failure |

New v2 endpoints should return these names rather than inventing new numeric values.
