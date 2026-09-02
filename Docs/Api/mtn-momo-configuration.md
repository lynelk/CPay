# MTN MoMo configuration and treasury accounts

This runbook covers the CPay `mtn_momo` adapter. It follows MTN's official
[API documentation](https://momoapi.mtn.com/api-documentation),
[API collections](https://momoapi.mtn.com/API-collections), and the linked MTN pages for
API-user/key management, callbacks, sandbox use cases, common errors, and best practices.

## Product subscriptions and credentials

Collection and Disbursement are different MTN products. Never place their credentials in one
generic username/key field and never use one product's bearer token for the other product.

| CPay field | MTN meaning | Required | Secret |
| --- | --- | --- | --- |
| `baseUrl` | MTN API origin; paths are derived by CPay | Yes | No |
| `targetEnvironment` | `X-Target-Environment` header | Yes | No |
| `baseCurrency` | Currency submitted to MTN | Yes | No |
| `callbackHost` | Host registered against the API user | Yes | No |
| `callbackUrl` | CPay MTN callback base URL; CPay appends the request UUID | Yes | No |
| `collectionApiUser` | Collection OAuth Basic username | Yes | Yes |
| `collectionApiKey` | Collection OAuth Basic password | Yes | Yes |
| `collectionSubscriptionKey` | Collection primary `Ocp-Apim-Subscription-Key` | Yes | Yes |
| `collectionSecondarySubscriptionKey` | Collection secondary key for controlled rotation | No | Yes |
| `disbursementApiUser` | Disbursement OAuth Basic username | Yes | Yes |
| `disbursementApiKey` | Disbursement OAuth Basic password | Yes | Yes |
| `disbursementSubscriptionKey` | Disbursement primary `Ocp-Apim-Subscription-Key` | Yes | Yes |
| `disbursementSecondarySubscriptionKey` | Disbursement secondary key for controlled rotation | No | Yes |

The MTN Partner Portal issues production credentials. The sandbox Provisioning API creates sandbox
API users and API keys. Do not copy sandbox credentials into the production scope. Keep all secret
fields blank in source control and enter them only through CPay's encrypted credential editor.

## Environment values

| Scope | Base URL | Target environment | Currency |
| --- | --- | --- | --- |
| MTN sandbox | `https://sandbox.momodeveloper.mtn.com` | `sandbox` | `EUR` |
| Uganda production | Issued/confirmed during onboarding | `mtnuganda` | `UGX` |

CPay rejects an MTN sandbox credential stored with a non-EUR currency, a production Uganda
credential whose target is not `mtnuganda`, non-HTTPS endpoints, and callback URLs whose hostname
does not match `callbackHost`.

## Runtime request contract

- Collection token: `POST /collection/token/`; request: `POST /collection/v1_0/requesttopay`.
- Disbursement token: `POST /disbursement/token/`; request: `POST /disbursement/v1_0/transfer`.
- OAuth uses the product API user/API key as Basic authentication and the product subscription key.
- CPay caches each product/credential token separately until shortly before MTN's `expires_in`.
- Every payment receives a new UUID v4 in `X-Reference-Id`; merchant references remain in
  `externalId` and are correlated server-side.
- HTTP 202 means accepted and pending, not successful. Final status comes from the callback or an
  operational status/reconciliation process.
- CPay sends a transaction-specific callback URL by appending the UUID to `callbackUrl`. Configure
  the base as `https://<callbackHost>/api/v2/provider-callbacks/mtn` and allow both POST and PUT at
  the edge. MTN invokes a callback only once, so pending transactions must remain visible for
  status polling and reconciliation.

Never log Authorization headers, subscription keys, API keys, bearer tokens, or full MSISDNs.
Production egress IPs must be whitelisted for Disbursement during MTN onboarding, and MTN callback
source IPs should be restricted at the load balancer/firewall.

## Default account topology

Each provider/country/currency/environment scope has one non-posting `MASTER` control account and
two operational sub-accounts:

| Account role | Used for | Initial balance | Prefund |
| --- | --- | --- | --- |
| `MASTER` | Scope grouping and control visibility | Zero | No |
| `COLLECTION` | Confirmed collection inflows and pending receivables | Zero | No |
| `DISBURSEMENT` | Payout reservations, pending outflows, and settlement | Zero | Required |

The migration creates these sub-accounts for the existing provider scopes and adds an MTN sandbox
UG/EUR scope. It does not copy a master balance into children or fabricate funds. Before enabling
shared-provider payouts, post a maker-checker `CREDIT` adjustment to the applicable
`DISBURSEMENT` sub-account using the bank/provider funding reference and evidence. Reconcile each
operational sub-account independently to the matching provider product statement.

## Activation checklist

1. Subscribe to both Collection and Disbursement in the correct MTN environment.
2. Obtain each product's API user, API key, primary key, and secondary key.
3. Register the CPay callback host and configure the exact callback base URL above.
4. Save the encrypted credential set; a different administrator approves it.
5. Restrict production callback source IPs and whitelist CPay's production egress IP for payouts.
6. Run a real MTN sandbox collection and payout; retain request UUID, HTTP evidence, callback, and
   final status evidence.
7. Reconcile the Collection and Disbursement sub-accounts separately.
8. Credit the production Disbursement sub-account only after MTN confirms the prefunded wallet.
