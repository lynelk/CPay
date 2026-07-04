# Production Incident Response Runbook

## Purpose

This runbook defines the standard operating response for production incidents affecting CPay payments, callbacks, provider connectivity, reconciliation, balances, or admin operations.

## Severity levels

### SEV1

Customer payments or payouts are broadly failing, balances are inconsistent, provider callbacks are stuck across many merchants, or finance close cannot proceed.

### SEV2

One provider, one merchant, or one operations workflow is degraded but there is a safe workaround.

### SEV3

A non-critical admin workflow, report, or dashboard is degraded without customer transaction impact.

## First response checklist

1. Confirm the incident type.
2. Check operations dashboard summary.
3. Check provider health and sandbox run history.
4. Check callback queue depth and parked tasks.
5. Check reconciliation unmatched and exception counts.
6. Check recent deployment or migration history.
7. Create or update an operations alert.
8. Assign an incident owner and next update time.

## Payment incident actions

- Pause broad merchant onboarding if failures affect many merchants.
- Confirm whether v1 or v2 traffic is affected.
- Check provider-level failures before changing application routing.
- Do not retry payouts blindly. Confirm provider idempotency behavior first.
- Capture transaction references, provider references, and merchant references.

## Callback incident actions

- Confirm merchant callback URL reachability.
- Confirm callback signing configuration.
- Requeue parked callbacks only after the root cause is corrected.
- Use merchant-level requeue for merchant-specific incidents.
- Use task-level requeue for isolated failures.

## Reconciliation incident actions

- Stop daily close if unmatched variance exceeds approved tolerance.
- Validate provider statement files before import.
- Assign exception categories for unmatched records.
- Require checker approval before posting finance adjustments.

## Balance incident actions

- Compare normalized balances against legacy balances.
- Do not perform manual balance correction without finance approval.
- Record all adjustment requests through the maker-checker flow.

## Communication cadence

- SEV1: update every 30 minutes.
- SEV2: update every 60 minutes.
- SEV3: update at least once per business day.

## Closure checklist

- Root cause identified.
- Customer impact summarized.
- Failed callbacks reprocessed where appropriate.
- Reconciliation exceptions cleared or assigned.
- Follow-up issue created for permanent fix.
- Incident alert resolved.
