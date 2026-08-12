# Provider Certification Checklist

## Purpose

This checklist defines the evidence required before enabling production traffic for any provider channel.

## Supported channels

- MTN MoMo
- Airtel Money
- Airtel OpenAPI
- Safaricom M-Pesa
- Yo! Payments

## Required channel setup evidence

For each merchant and provider channel, confirm that:

1. The merchant channel setup exists.
2. The channel has a collect endpoint URL.
3. The channel has a payout endpoint URL.
4. Required channel-specific setup values are present.
5. Stored values are masked in the merchant portal.
6. The channel has passed sandbox readiness checks.
7. The channel has been submitted for approval.
8. Production approval is recorded before live use.
9. The merchant's sandbox/production environment preference is correct.
10. The production transaction cap has an approved value for launch.

## Required provider evidence

For each channel, record evidence for:

1. Collect accepted.
2. Payout accepted.
3. Status check returns expected state.
4. Provider callback received and mapped.
5. Duplicate merchant reference handled safely.
6. Invalid account rejected.
7. Insufficient funds response mapped.
8. Provider timeout handled.
9. Provider unavailable response handled.
10. Statement file validated.
11. Statement import tested.
12. Reconciliation match tested.
13. Reconciliation exception tested.
14. Daily close dry run completed.
15. Payment-link or invoice checkout flow tested when that channel is enabled for hosted payments.

## Required artifacts

- Sandbox run ids.
- Endpoint setup evidence.
- Request and response summaries.
- Provider statement sample files.
- Reconciliation validation report.
- Callback verification evidence.
- Signoff from engineering.
- Signoff from finance operations.
- Signoff from business owner.

## Production enablement rule

A provider channel should not be enabled for live merchant traffic until every required scenario has a pass result or an approved exception. In production mode, missing endpoint URLs should block live execution.
