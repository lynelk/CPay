# Merchant Self-Service Onboarding and Channel Setup

This document explains the merchant self-service features added to CPay.

## Purpose

Merchants can now begin onboarding without an administrator creating the account manually. After registration, a merchant can log in and configure supported payment channels from the merchant portal.

## Merchant signup

Public signup is available through the merchant portal at:

```text
/signup
```

The signup form captures:

- business name
- short name
- primary contact name
- email address
- phone number
- password

After registration, the merchant receives an account number and the account is created in a pending approval state. Production activity should remain blocked until business approval is completed.

## Merchant payment channel setup

Logged-in merchants can open:

```text
Merchant Dashboard -> Payment Channels
```

The page lists supported channels such as:

- MTN MoMo
- Airtel Money
- Airtel OpenAPI
- Safaricom M-Pesa

For each channel, the merchant can:

1. enter setup values for the provider channel
2. save the channel setup
3. run a sandbox readiness check
4. submit the channel for approval

Saved values are stored in the backend and returned to the user only as masked values.

## Backend endpoints

```text
POST /api/v2/merchant-self-service/signup
GET  /api/v2/merchant-self-service/channels
POST /api/v2/merchant-self-service/channels/save
POST /api/v2/merchant-self-service/channels/test
POST /api/v2/merchant-self-service/channels/submit
```

## Gateway integration

Native v2 payments now check whether the merchant has configured and tested the selected channel before allowing collect or payout execution through that channel.

This means a merchant cannot use a native v2 channel unless that channel exists in their merchant channel setup records.

## Security controls

The implementation adds the following controls:

- merchant-owned channel records
- encrypted storage for channel setup values
- masked display values
- merchant session ownership checks
- audit records for channel actions
- sandbox readiness status
- approval status before production enablement

## Remaining manual checks

The software flow is implemented, but production activation still requires real-world checks:

- provider sandbox validation
- provider approval
- merchant callback verification
- finance signoff where applicable
- production monitoring setup
