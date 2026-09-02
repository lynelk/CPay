# Product experience and operational KPIs

## Product funnel

Track allowlisted events only: page view, CTA selected, signup started/completed, sandbox started, first test transaction, go-live requested and support case created. Actor/session references are one-way hashed; event properties are allowlisted and must not contain credentials, payer data or free-form request payloads.

Core funnel measures are visit-to-signup, signup completion, time to email verification, time to sandbox, first-test success, time to go-live request, approval lead time and production activation rate.

## Operations and finance

Measure payment success by provider/channel/country, provider latency, non-final ageing, callback backlog, reconciliation match rate, settlement exceptions, close timeliness, provider float runway and limit-parking volume. Values must cite their query source and `data_as_of` timestamp.

## Support and incidents

Measure case volume by category/severity, first-response SLA attainment, resolution SLA attainment, reopen rate, contact reason after activation step, incident detection/acknowledgement/recovery time, and affected merchant/transaction counts.

## Guardrails

Dashboards must show unavailable or stale states when their source fails. A zero is valid only when the live query completed and returned zero. Alerting thresholds are configured operationally and must not be embedded as unexplained UI sample values.
