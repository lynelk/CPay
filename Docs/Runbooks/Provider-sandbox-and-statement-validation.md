# Provider Sandbox and Statement Validation Runbook

## Purpose

This runbook explains the operational checks required before a provider channel is approved for live use.

## Provider sandbox checks

For each provider channel, confirm:

1. Merchant channel setup exists.
2. Collect endpoint URL is configured.
3. Payout endpoint URL is configured.
4. Channel-specific setup values are present.
5. Sandbox readiness check has passed.
6. Collect request is accepted in provider sandbox.
7. Payout request is accepted in provider sandbox.
8. Status check returns the expected state.
9. Provider callback is received and mapped.
10. Failure cases are mapped clearly.

## Statement validation checks

For each provider statement file, confirm:

- file format is supported
- required columns are present
- transaction references are present
- duplicate rows are detected
- amounts and currencies are valid
- provider references can be matched or flagged
- invalid rows are reported clearly

## Evidence to retain

- sandbox run id
- provider name
- channel code
- merchant account number
- request reference
- provider response summary
- callback evidence
- statement validation run id
- validation errors if any
- approval owner
- approval date

## Approval rule

A provider channel should not be enabled for live traffic until sandbox evidence, statement validation evidence, callback evidence, and approval evidence are complete.
