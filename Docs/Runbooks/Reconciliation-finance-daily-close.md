# Reconciliation and Finance Daily Close Runbook

## Purpose

This runbook defines the finance daily-close controls for **Cito**. A close confirms that internal payment/accounting records, provider evidence, reconciliation exceptions, settlement state, ledger balances and finance approvals are complete for the selected business date.

The normative arithmetic and matching rules are in `Docs/Financial-correctness-and-data-integrity.md`.

## Close principle

A balanced ledger is necessary but not sufficient for daily close. The business date is not closed until required provider statements, reconciliation evidence, exception disposition and maker-checker approvals are present.

Do not convert missing evidence into a zero, a match, or a completed status just to close the day.

## Before close

Confirm that:

- all expected provider statements/files for the close scope have been received or formally exceptioned;
- every file passed structural/provider validation and import completed;
- automatic matching used the current multi-factor rule: merchant reference + amount + currency + eligible final status + exactly one candidate;
- ambiguous/no-candidate statement rows remain unmatched rather than being force-matched;
- manual matches and adjustments have traceable operator evidence and the required approvals;
- provider/payment states that remain ambiguous are classified for follow-up rather than coerced to success/failure;
- settlement batch provider, channel, currency and expected amount agree with their original immutable batch record and ledger posting;
- ledger trial balance is balanced independently for every active currency in the close scope;
- payment/callback/webhook failures for completed transactions have been reviewed for duplicate or missing downstream outcomes;
- high/critical operating-control events are resolved or formally carried forward under an approved exception.

## Close steps

1. Select and record the business date and close scope.
2. Review the expected-provider-statement register and confirm all required evidence is present.
3. Review import validation results and imported row totals/amount totals.
4. Run/review automatic reconciliation and confirm the matching rule has not been weakened.
5. Review unmatched, ambiguous and exception items; resolve only with auditable evidence.
6. Review manual-match and financial-adjustment approvals.
7. Review settlement batches and ensure operational expected amounts agree with ledger settlement postings.
8. Run/review trial balance by active currency.
9. Review billing/invoice/payment allocations where included in the close scope, including credit-note tax allocations.
10. Review high/critical operational alerts and unresolved provider/callback/webhook conditions.
11. Maker submits the close with the required evidence set.
12. A different authorized checker reviews and approves/rejects the close.
13. Store the immutable close evidence and unresolved-item carry-forward list.

## Do not close when

- an expected provider statement is missing without an approved exception;
- a statement import failed validation or is incomplete;
- reconciliation variance exceeds approved policy/tolerance;
- an item is marked matched solely because its merchant reference matches;
- multiple candidate internal transactions could satisfy a provider row;
- settlement operational amounts differ from the immutable batch/ledger evidence;
- any active currency trial balance is unbalanced;
- high/critical exceptions remain unresolved without approved carry-forward treatment;
- required maker-checker approvals are missing or the maker is also the checker;
- finance owner/checker has not completed signoff.

## Evidence to retain

Retain at least:

- business date and close scope;
- currencies and providers covered;
- expected and received statement references;
- statement validation/import results and control totals;
- automatic matched/unmatched/ambiguous counts and amounts;
- manual-match evidence and reasons;
- reconciliation exceptions and disposition;
- settlement batch references, expected amounts and ledger references;
- trial balance result per currency;
- approved adjustments/reversals/credit notes;
- maker and checker identities/timestamps;
- unresolved items formally carried forward;
- close decision/status and immutable audit references.

## Post-close corrections

Do not reopen history by editing posted ledger entries or original settlement amounts. Corrections use explicit reversing/adjusting postings and retain links to the original artifact. If a provider later supplies contradictory evidence, create a governed reconciliation/adjustment case.

## Operational note

Daily close is an evidence-backed control, not a ceremonial button. Software is exceptionally capable of producing a green button while the bank statement disagrees, so the evidence wins.
