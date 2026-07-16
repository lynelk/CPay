# Observability

CPay exposes structured logs, request correlation, actuator health, Prometheus metrics, and operational alert data.

## Structured logs

Backend logs are emitted as JSON through `logback-spring.xml`.

Important fields:

- `service`
- `level`
- `logger`
- `thread`
- `message`
- `request_id`
- `http_method`
- `http_path`

Stack traces remain attached to error events.

## Request correlation

`RequestCorrelationFilter` reads `X-Request-ID` from incoming requests or generates one when missing. The same value is returned in the response header and placed in MDC so every backend log line in the request can be correlated.

Gateway and callback callers should forward `X-Request-ID` where possible.

## Metrics

Prometheus metrics are available through Spring Actuator when authorized:

```text
GET /actuator/prometheus
```

Business counters include:

- `cpay_callback_delivery_total`
- `cpay_gateway_error_total`
- `cpay_rate_limit_exceeded_total`
- `cpay_transaction_completed_total`
- `cpay_transaction_initiated_total`

Keep metric labels low-cardinality. Do not tag metrics with merchant IDs, phone numbers, transaction IDs, or raw references.

## Alert definitions

Operational alerts should map to one of these runbooks:

| Alert | Source | Runbook |
|---|---|---|
| Float below threshold | float scheduler / dashboard | `docs/runbooks/operations-alerts.md` |
| Callback parked | `callback_tasks.task_status='PARKED'` | `docs/runbooks/operations-alerts.md` |
| Retry queue growth | callback/retry tables | `docs/runbooks/operations-alerts.md` |
| Reconciliation exceptions | `reconciliation_records` | `docs/runbooks/reconciliation-finance-daily-close.md` |
| Provider outage | gateway adapters / endpoint runs | `docs/runbooks/production-incident-response.md` |

## Dashboard baseline

The first production dashboard should include:

- transaction success rate by channel
- failed transactions by reason
- callback delivery status
- parked callback count
- retry queue count
- provider endpoint run status
- rate-limit exceeded count
- oldest unprocessed callback task age
- float runway by channel
