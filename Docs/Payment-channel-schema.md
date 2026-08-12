# Payment Channel Schema Target

This document describes the target shape for channel configuration. Parts of this model now exist in
`merchant_channel_credentials`, `merchant_environment_preferences`, channel routing, and normalized
balance read models; keep new work aligned to this shape instead of adding more provider-specific
columns or settings.

## payment_channels

- id
- code
- name
- type
- country
- currency
- status
- supports_collect
- supports_payout
- supports_balance
- supports_status_check
- supports_refund
- supports_callback
- adapter_class
- priority
- display_label (for example `Yo! Payments`)
- created_at
- updated_at

## payment_channel_routes

- id
- merchant_id
- channel_code
- country
- currency
- min_amount
- max_amount
- priority
- enabled
- environment (SANDBOX or PRODUCTION)
- created_at
- updated_at

## merchant_channel_balances

- id
- merchant_id
- channel_code
- currency
- available_balance
- ledger_balance
- updated_at

The existing statement table still stores fixed balances for the legacy channels. New channel work should use a normalized balance table so adding a gateway no longer requires adding new columns.

## Environment rules

- Sandbox and production credentials must be separate rows.
- API callers may request an environment with `X-CPay-Environment`; otherwise CPay falls back to the
  merchant/user preference.
- Production calls are capped by default through `production_transaction_limit_enabled` and
  `production_transaction_limit_count`.
- The UI label for the Yo channel is `Yo! Payments`.
