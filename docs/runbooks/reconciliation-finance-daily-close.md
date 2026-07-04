# Reconciliation Finance Daily Close Runbook

## Purpose

This runbook explains the finance daily-close controls for CPay.

## Daily close goal

The daily close confirms that internal transaction records, provider statements, reconciliation exceptions, balances, and finance review actions are complete for a selected business date.

## Before close

Confirm that:

- provider statements have been received
- statement files have been validated
- reconciliation import has completed
- unmatched items have been reviewed
- manual adjustments have maker-checker approval where required
- callback failures for completed transactions have been reviewed
- operating-control events for the day have been reviewed

## Close steps

1. Review reconciliation finance summary.
2. Confirm unmatched and exception counts.
3. Review provider statement validation results.
4. Review approved finance postings.
5. Confirm high-severity operating-control events are resolved or assigned.
6. Record finance signoff.
7. Run the daily close endpoint or close workflow.
8. Store close evidence for audit review.

## Do not close when

- provider statement files are missing
- reconciliation variance exceeds approved tolerance
- high-severity operating-control events are unresolved
- required maker-checker approvals are missing
- finance owner has not signed off

## Evidence to retain

- close date
- currency
- provider statement references
- matched and unmatched counts
- exception summary
- adjustment approvals
- operator and checker names
- close timestamp
- unresolved items carried forward

## Production note

Daily close is a finance control, not just a button. If it is treated as a button, the button will eventually become a meeting.
