# Payment Channel Schema Target

The next database migration should normalize channel configuration away from scattered gateway settings.

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
