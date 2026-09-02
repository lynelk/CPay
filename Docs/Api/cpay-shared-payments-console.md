# CPay Shared Payments and Provider Operations Console

## Purpose

CPay Shared Payments is the default payment access method for an active merchant that has not yet
activated its own MTN MoMo or Airtel OpenAPI credentials. “CPay” is the credential and treasury
owner; the underlying payment rail remains visible as MTN or Airtel on every transaction,
reservation, journal entry, and provider test.

The feature does not create provider credentials, claim a provider balance, or prefund an account.
Migrations create only zero-value control structures and scoped merchant entitlements.

## Merchant experience

The first card on the merchant Payment Channels page is **CPay Shared Payments**. It shows, for the
merchant's selected environment:

- each underlying rail and operation;
- entitlement and platform-credential readiness;
- per-transaction, used-today, daily, and remaining-today limits;
- whether a payout is pending approval;
- direct navigation to the merchant-owned MTN and Airtel credential editors.

Active merchants receive Production collection entitlements for MTN and Airtel automatically.
Default limits are controlled by the `shared_provider_default_*` settings. Production payout
entitlements are created in `PENDING` state and require an independent admin approval plus
prefunded provider disbursement float. Existing, manually managed entitlement records are never
overwritten by the scheduled default provisioner.

An approved merchant-owned credential remains the normal first choice. A request may explicitly
select `credentialSource=PLATFORM_SHARED` or `MERCHANT`; therefore an administrative test can prove
the intended CPay-owned route without accidentally exercising a merchant secret.

## Provider balance dashboard

The Provider Treasury page presents MASTER, COLLECTION, and DISBURSEMENT accounts independently.
Filters cover environment, provider, currency, account role, and reconciliation state. Summary
cards show book, available, reserved, and pending totals for the selected rows; admins must select
one currency before treating a summary as a monetary total.

Each row exposes:

- CPay book balance;
- reserved payout amount;
- pending outgoing and incoming amounts;
- calculated available balance;
- last provider-reported balance and its age;
- low-float and reconciliation state;
- provider synchronization, threshold, and reconciliation controls.

Provider synchronization calls the appropriate MTN product wallet or Airtel balance endpoint with
an approved encrypted platform credential. A transport error, missing credential, non-2xx response,
or missing balance field stores `UNAVAILABLE` and a safe diagnostic. It never writes a numeric zero.
An actual provider response containing zero is stored as the valid number zero with status
`AVAILABLE`. Reconciliation remains the evidence-backed process for resolving book/provider
variance; synchronization alone does not post value.

## Live transaction tests

An admin selects an active merchant, COLLECT or PAYOUT, MTN or Airtel, environment, country,
currency, amount, international MSISDN, and idempotency key. Tests always force the
`PLATFORM_SHARED` credential path and use the same adapter, entitlement, daily-limit, reservation,
provider callback, and treasury journal services as normal API traffic.

The test table polls while a request is active and shows:

- CPay test reference and idempotency key;
- masked party value only;
- provider and treasury status;
- maker/checker identities;
- latest event and the complete server-side event history.

Production requests require an explicit real-money acknowledgement and a valid current admin TOTP.
The configurable `provider_live_test_max_amount` caps each request. Collection tests execute after
validation. Payout tests stop at `PENDING_APPROVAL`; a different administrator must supply their own
MFA code to approve and execute the request. The full MSISDN is encrypted at rest, MFA codes are
never persisted, provider secrets are never returned, and all high-risk endpoints require both an
admin session and a seeded permission code.

## Airtel credential contract

Airtel OpenAPI is provider-native and no longer uses generic collect/payout URLs or an arbitrary
authentication header. The credential fields are:

- base URL (`https://openapiuat.airtel.africa` for sandbox/UAT or
  `https://openapi.airtel.africa` for production);
- OAuth client ID and client secret issued by Airtel;
- country and currency matching the credential scope;
- disbursement API PIN and Airtel-issued RSA public key used to encrypt the PIN;
- OAuth, collection, disbursement, and balance paths, prefilled with supported API defaults.

The application obtains a bearer token at runtime and sends Airtel's `Authorization`, `X-Country`,
and `X-Currency` headers. No login email or CPay portal password belongs in a provider credential
field.

## Permissions

`PAYMENT_BALANCE_VIEW`, `PAYMENT_BALANCE_REFRESH`, `SHARED_PAYMENT_ENTITLEMENT_MANAGE`,
`SHARED_PAYMENT_LIMIT_APPROVE`, `LIVE_COLLECTION_TEST`, `LIVE_DISBURSEMENT_TEST`,
`LIVE_DISBURSEMENT_APPROVE`, `PROVIDER_CREDENTIAL_MANAGE`, `PROVIDER_CREDENTIAL_APPROVE`,
`RECONCILIATION_VIEW`, and `RECONCILIATION_MANAGE` are granted to the ADMIN role by migration.
`AdminPermissionService` verifies the database grant and records both allowed and denied actions.
