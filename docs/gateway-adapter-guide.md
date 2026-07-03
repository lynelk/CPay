# Gateway Adapter Guide

This guide defines the preferred way to add new gateways and channels to CPay without expanding the existing hardcoded routing logic.

## Goal

New channels should be added as adapters that describe their capabilities and implement a common contract for collections, payouts, balance checks, status checks, and callbacks.

The legacy `DoPayGateway` class still supports the current native integrations, but new work should move toward the `net.citotech.cito.gateway` package.

## Adapter contract

Every native channel should implement `PaymentChannelAdapter`.

```java
@Component
public class ExampleGatewayAdapter implements PaymentChannelAdapter {
    @Override
    public String channelCode() {
        return "example_gateway";
    }

    @Override
    public String displayName() {
        return "Example Gateway";
    }

    @Override
    public String countryCode() {
        return "UG";
    }

    @Override
    public String currencyCode() {
        return "UGX";
    }

    @Override
    public GatewayCapabilities capabilities() {
        return GatewayCapabilities.mobileMoneyDefaults();
    }

    @Override
    public boolean supportsAccount(String accountIdentifier) {
        return accountIdentifier != null && accountIdentifier.startsWith("256");
    }
}
```

## Required channel metadata

Each channel should define:

- channel code
- display name
- country support
- currency support
- supported operations
- callback verification method
- provider reference mapping
- retry behaviour
- timeout behaviour
- min and max amount limits
- charge model
- reconciliation format

## Recommended routing order

1. Explicit `channel` supplied in the API request.
2. Merchant-level preferred route.
3. Country/currency match.
4. Account identifier or MSISDN prefix fallback.
5. Provider availability and health.
6. Cost or success-rate based routing.

## Testing checklist

For each new channel, add tests for:

- successful collect
- successful payout
- failed collect
- failed payout
- pending status
- callback success
- callback failure
- duplicate callback idempotency
- timeout handling
- balance check
- disabled channel routing
- merchant-specific charge override

## Migration path from legacy routing

1. Create adapters for MTN, Airtel, and Safaricom that wrap the existing gateway classes.
2. Add read-only capability discovery using `PaymentChannelRegistry`.
3. Add `/api/v2/payments/collect` and `/api/v2/payments/payout` using adapters.
4. Keep `/api/v1` endpoints unchanged for backwards compatibility.
5. Move charges and route preferences into structured channel tables.
6. Retire hardcoded balance columns after a ledger migration.
