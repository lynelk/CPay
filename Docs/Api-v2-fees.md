# Cito v2 Fee Schedule and Simulation API

The legacy-compatible v2 fee API exposes effective-dated fee schedule lookup and read-only fee simulation.

## Supported charging methods

`FLAT_FEE` and `PERCENTAGE` are supported by this fee-schedule surface.

`TIER` is not accepted by this legacy fee schedule implementation. Cito fails closed instead of interpreting a tier schedule as a flat fee. Genuine graduated/marginal tier pricing belongs to the billing rating engine and must not be inferred from this endpoint.

## Calculation precision

Authoritative simulation calculations use the Cito canonical monetary policy: `BigDecimal`, four decimal places, `HALF_UP`. API presentation of the transaction amount and fee may be formatted to two decimals, but display formatting does not alter the calculation value used internally.

For percentage pricing:

```text
fee = transaction amount × percentage / 100
```

The result is normalized to four decimals before presentation.

## Validation

Fee schedules reject:

- null/non-positive fee amounts;
- percentage rates above 100%;
- unsupported charging methods;
- missing gateway/creator identity on schedule creation.

Effective-dated history is retained and a merchant-specific active schedule takes precedence over the global schedule for the same gateway/service/charge type.

## Endpoints

The existing controller routes remain:

```text
POST /api/v2/fees/simulate
GET  /api/v2/fees/schedules/current
```

No route or request-shape change is introduced by the September 2026 financial-correctness pass; the change tightens calculation precision and prevents unsupported fee models from producing a misleading result.

See `Financial-correctness-and-data-integrity.md` for the platform-wide money policy.
