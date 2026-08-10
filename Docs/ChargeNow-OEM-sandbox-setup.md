# ChargeNow OEM sandbox setup

## Status

The CPay side of the ChargeNow/Bajie OEM sandbox setup is now packaged as a one-shot, tenant-scoped configuration workflow.

The supplied ChargeNow management guide documents cabinet operations, stores, pricing, orders, returns, monitoring and power-bank management, but it does not publish the private partner wire contract. Current public Bajie/ChargeNow material confirms OEM/white-label software, cloud-connected stations and API connectivity, but it likewise does not publish a sandbox base URL, credentials, authenticated operation paths, callback signature contract or event schema.

CPay therefore does not commit guessed manufacturer values. The OEM-issued values are applied at runtime and encrypted at rest.

## Merchant sandbox endpoints

The ChargeNow sandbox profile is managed through the merchant-session API:

```text
POST /api/v2/merchant-self-service/vending/connectors/CHARGENOW/sandbox/apply
GET  /api/v2/merchant-self-service/vending/connectors/CHARGENOW/sandbox/manifest
```

`POST .../apply` atomically applies:

- the ChargeNow sandbox base URL and outbound authentication contract;
- the `RELEASE_ASSET` operation and any additional OEM operations;
- response success/reference/message mappings;
- callback authentication, signature encoding and JSON field mappings;
- callback-to-rental correlation by rental reference, CPay command reference or OEM provider reference.

The response is redacted. Cleartext OEM credentials and callback secrets are not returned after they have been encrypted by `MerchantChannelCryptoService`.

## Before applying the OEM profile

The deployment must have:

1. `APP_BASE_URL` set to the externally reachable HTTPS CPay base URL. The sandbox manifest derives the manufacturer callback URL from this value.
2. `MERCHANT_CHANNEL_ENCRYPTION_KEY` configured, because OEM credentials and callback secrets are encrypted at rest.
3. Flyway migrations V51-V53 applied.
4. The tenant's `vending-platform` feature enabled through the existing feature-management process.
5. A ChargeNow OEM sandbox account and test cabinet/device assigned by the manufacturer.

Do not place OEM secrets in Git, application.properties, screenshots or issue comments. Human beings have already invented enough ways to leak API keys without adding another one.

## Partner values required from ChargeNow/Bajie

The OEM integration pack must supply these values before an external sandbox can be activated:

| Area | Required OEM value |
|---|---|
| Environment | Sandbox base URL |
| Credentials | Token/API key/username or public key, plus secret where required |
| Outbound auth | Authentication mode, header names, signature encoding and canonical/signing string if HMAC is used |
| Release | HTTP method, path, request JSON fields, acknowledgement status and OEM command/reference field |
| Idempotency | OEM idempotency header and replay semantics, if supported |
| Status | Read-only cabinet/status operation method, path and response schema |
| Callback auth | Signature/token mode, header names, encoding, timestamp format and nonce rules |
| Callback schema | Event id, event type, device id, release, return, offline/heartbeat and inventory field paths/values |
| Correlation | Rental reference, CPay command reference, or OEM provider-command reference echoed in callbacks |
| Asset data | Power-bank/slot identifiers and availability values |
| Reliability | Timeout, retry and duplicate-event rules |
| Errors | OEM status/error catalogue |

`Docs/chargenow-oem-sandbox-profile.example.json` is a deliberately non-runnable template for recording those values. Every `REPLACE_WITH_...` value must be replaced from the manufacturer partner pack.

## Applying the profile

Submit the completed JSON template while authenticated in the CPay merchant portal. The `connector` object contains host/auth/callback policy. The `operations` object contains the exact manufacturer operation mappings. `RELEASE_ASSET` is mandatory. `QUERY_STATUS` is strongly recommended because it gives operators a safe read-only hardware probe before money movement is tested.

The setup service copies the release operation's path, request template, completion mode, idempotency header and response mappings into the legacy release fields required by the existing connector configuration model. It then stores all operation-specific mappings in `vending_connector_operations`.

A callback correlation route is mandatory. At least one of these must be configured:

```text
connector.callbackRentalField
callbackCorrelation.callbackCommandReferenceField
callbackCorrelation.callbackProviderReferenceField
```

This prevents a sandbox configuration that can eject a power bank but cannot reliably associate the manufacturer's release/return callback with the correct CPay rental. That would be an unusually expensive form of optimism.

## Confirming the applied sandbox manifest

Call:

```text
GET /api/v2/merchant-self-service/vending/connectors/CHARGENOW/sandbox/manifest
```

The response includes:

```json
{
  "connectorCode": "CHARGENOW",
  "callbackPath": "/api/v2/vending/device-callbacks/CHARGENOW/{merchantId}",
  "callbackUrl": "https://your-cpay-host/api/v2/vending/device-callbacks/CHARGENOW/{merchantId}",
  "readiness": {
    "status": "READY_FOR_OEM_SANDBOX",
    "readyForSandbox": true
  },
  "connector": {},
  "operations": [],
  "callbackCorrelation": {}
}
```

The connector portion is redacted and reports whether secrets are configured rather than returning them.

`READY_FOR_OEM_SANDBOX` means CPay's internal contract is complete enough to start manufacturer testing. It is not an OEM certification result.

## Manufacturer callback URL

The callback registered with ChargeNow must be the `callbackUrl` returned by the sandbox manifest:

```text
POST https://<CPAY_HOST>/api/v2/vending/device-callbacks/CHARGENOW/{merchantId}
```

The exact callback authentication mode, header names, encoding and event field mappings must match the OEM partner pack. CPay currently supports:

- `HMAC_SHA256_TS_NONCE_BODY`
- `HMAC_SHA256_TS_BODY`
- `HMAC_SHA256_BODY`
- `STATIC_TOKEN_HEADER`
- Base64 or hexadecimal HMAC signatures

Timestamp-based modes enforce the configured five-minute freshness policy, and nonce mode stores/rejects replayed nonces.

## Sandbox certification sequence

After real OEM values are applied, execute the sandbox in this order:

1. Fetch the manifest and confirm `READY_FOR_OEM_SANDBOX`.
2. Register the returned callback URL in the ChargeNow partner console or have the OEM engineer register it.
3. Register a CPay vending device with the test cabinet's exact external OEM device id.
4. Configure `QUERY_STATUS` and run `POST /api/v2/merchant-self-service/vending/devices/{deviceCode}/probe` to confirm authenticated outbound connectivity without releasing hardware.
5. Verify a signed heartbeat/status callback is accepted once and a replay is rejected/idempotently ignored as appropriate.
6. Run one controlled rental using sandbox payment funds.
7. Confirm CPay reaches `READY_TO_RELEASE`, claims exactly one deterministic `RELEASE_ASSET` command and moves to `RELEASE_PENDING` after OEM acceptance.
8. Confirm the authenticated OEM release callback correlates to the rental and moves it to `ACTIVE`; `started_at` must be set only at this point.
9. Return the power bank and confirm the authenticated return callback triggers rental completion/refund calculation.
10. Reconcile CPay command evidence, callback evidence, transaction records and the OEM's order/device log.

Do not certify release as `IMMEDIATE` unless ChargeNow explicitly documents that a successful synchronous response proves physical ejection. For asynchronous hardware, use `CALLBACK` completion so customers are not billed while a cabinet is merely considering their request.

## Current external dependency

No authoritative ChargeNow sandbox URL or credential set is present in the supplied project files, connected Drive search, or public manufacturer documentation reviewed for this integration. Those values must be issued by ChargeNow/Bajie. Once received, no Java rental/payment rewrite is required: apply the partner bundle, register the callback URL, map the test cabinet, and run the certification sequence above.
