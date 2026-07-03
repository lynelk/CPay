# Reconciliation Finance Daily Close Runbook

## Purpose

This runbook describes how to operate the reconciliation finance workflow: statement validation, exception review, ledger posting, reporting, and daily close.

## Daily workflow

1. Validate provider statement files.
2. Import validated statements.
3. Run auto-match.
4. Review unmatched records.
5. Assign exception categories and settlement batches.
6. Request maker-checker review for adjustments.
7. Approve or reject review items.
8. Post approved reviews to the finance workflow.
9. Run the daily close.
10. Review the operations dashboard for alerts.

## Maker-checker expectations

The maker should request a review with clear reason and supporting reference. The checker should independently verify provider reference, merchant reference, amount, currency, and settlement batch before approval.

The application now has finance posting and close endpoints, but production rollout should also add role separation so makers cannot approve their own adjustments.

## Daily close checks

Before closing the day:

- unmatched count should be within tolerance
- exception categories should be assigned
- approved reviews should be posted
- settlement batches should be closed
- callback failures should not be blocking transaction status updates

## Escalation

Escalate when:

- unmatched amount exceeds tolerance
- provider file validation fails
- settlement batch cannot be closed
- approved adjustment cannot be posted
- normalized balance totals diverge from legacy balances
