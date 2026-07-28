# API v2 Examples

## Collect

Adapter-native route:

`POST /api/v2/native/payments/collect`

Compatibility route:

`POST /api/v2/payments/collect`

```json
{
  "merchantNumber": "256770000000",
  "amount": "50000",
  "currency": "UGX",
  "country": "UG",
  "channel": "mtn_momo",
  "payer": {
    "type": "msisdn",
    "value": "256771234567"
  },
  "reference": "TXN-001",
  "description": "Order payment",
  "callbackUrl": "https://example.com/webhook"
}
```

## Payout

Adapter-native route:

`POST /api/v2/native/payments/payout`

Compatibility route:

`POST /api/v2/payments/payout`

```json
{
  "merchantNumber": "256770000000",
  "amount": "50000",
  "currency": "UGX",
  "country": "UG",
  "channel": "airtel_money",
  "payee": {
    "type": "msisdn",
    "value": "256751234567"
  },
  "reference": "PAYOUT-001",
  "description": "Merchant payout",
  "callbackUrl": "https://example.com/webhook"
}
```

## Channels

`GET /api/v2/channels`

Returns the registered adapter-backed channels and their supported capabilities.
