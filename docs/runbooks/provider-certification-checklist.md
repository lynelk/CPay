# Provider Certification Checklist

## Purpose

This checklist defines the evidence required before enabling production traffic for any provider channel.

## Supported channels

- MTN MoMo
- Airtel Money
- Airtel OpenAPI
- Safaricom M-Pesa

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

## Required artifacts

- Sandbox run ids.
- Request and response summaries.
- Provider statement sample files.
- Reconciliation validation report.
- Callback verification evidence.
- Signoff from engineering.
- Signoff from finance operations.
- Signoff from business owner.

## Production enablement rule

A provider channel should not be enabled for live merchant traffic until every required scenario has a pass result or an approved exception.
