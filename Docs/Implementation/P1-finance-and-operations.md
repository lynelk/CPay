# P1 - Finance and Operations Maturity

## Objective

P1 turns reconciliation, settlement, treasury, reporting, and incident handling into enforceable operating workflows. This phase makes CPay explainable to finance teams, support teams, auditors, and serious merchants.

## P1 Deliverables

1. Settlement batch lifecycle
2. Treasury position tracking
3. Reconciliation exception ownership
4. Finance daily-close enforcement
5. Reports and exports
6. Incident management

---

## 1. Settlement Batch Lifecycle

### Problem

Reconciliation and daily close are not enough. CPay needs a formal settlement workflow that tracks what is owed, what was paid, what is pending, what failed, and what requires exception handling.

### Implement Entities

- `settlement_batch`
- `settlement_item`
- `settlement_adjustment`
- `settlement_variance`
- `settlement_approval`
- `settlement_statement`
- `provider_payable`
- `merchant_payable`
- `partner_receivable`
- `finance_posting`

### Settlement States

```text
OPEN
CALCULATED
REVIEW_PENDING
APPROVED
PAID
RECONCILED
EXCEPTION
CLOSED
```

### Settlement Batch Fields

- batch ID
- merchant ID or provider scope
- provider/channel
- country
- currency
- business date
- settlement cycle
- gross amount
- fees
- taxes/deductions
- adjustments
- net settlement amount
- variance amount
- status
- maker/checker references
- timestamps

### APIs

```text
GET /api/v2/admin/settlements
GET /api/v2/admin/settlements/{settlementId}
POST /api/v2/admin/settlements/{settlementId}/calculate
POST /api/v2/admin/settlements/{settlementId}/submit-review
POST /api/v2/admin/settlements/{settlementId}/approve
POST /api/v2/admin/settlements/{settlementId}/mark-paid
POST /api/v2/admin/settlements/{settlementId}/reconcile
POST /api/v2/admin/settlements/{settlementId}/close
GET /api/v2/merchant/settlements
GET /api/v2/merchant/settlements/{settlementId}/statement
```

### Tests

- Cannot approve a settlement before calculation.
- Maker cannot approve own settlement submission.
- Settlement cannot close with unresolved blocking variance.
- Settlement statement totals match settlement items.
- Reversal or adjustment produces auditable finance posting.

---

## 2. Treasury Position Tracking

### Problem

Payment gateways must know available funds, reserved funds, pending exposure, and unreconciled exposure. Balance checks alone are too shallow for finance-grade control.

### Implement Entity

`treasury_position` with:

- provider
- merchant
- channel
- country
- currency
- corridor where applicable
- available balance
- reserved balance
- settled balance
- pending payout exposure
- pending refund exposure
- unreconciled exposure
- last provider balance timestamp
- last reconciliation timestamp
- status

### APIs

```text
GET /api/v2/admin/treasury/positions
GET /api/v2/admin/treasury/positions/{positionId}
POST /api/v2/admin/treasury/positions/recalculate
GET /api/v2/admin/treasury/exposure-summary
```

### Controls

- Payouts reserve funds before provider submission.
- Failed payouts release reservations.
- Successful payouts move exposure into settlement/reconciliation.
- Refunds reserve funds before execution.
- Reconciliation reduces unreconciled exposure.

### Tests

- Reserved balance increases when payout is approved.
- Reserved balance releases on failed payout.
- Available balance cannot go negative unless explicitly allowed by configured overdraft rule.
- Treasury positions are separated by sandbox/production environment.

---

## 3. Reconciliation Exception Ownership

### Problem

Unmatched transactions must not sit anonymously. Exceptions need owners, severity, status, aging, and resolution evidence.

### Implement Entity

`reconciliation_exception` with:

- exception ID
- provider/channel
- transaction reference
- provider reference
- merchant reference
- business date
- amount
- currency
- exception type
- severity
- status
- assigned owner
- due date
- resolution note
- evidence reference

### Exception Types

```text
MISSING_PROVIDER_STATEMENT
MISSING_INTERNAL_TRANSACTION
AMOUNT_MISMATCH
CURRENCY_MISMATCH
DUPLICATE_PROVIDER_REFERENCE
DUPLICATE_MERCHANT_REFERENCE
STATUS_MISMATCH
LATE_PROVIDER_CALLBACK
UNDETERMINED_TRANSACTION
MANUAL_REVIEW_REQUIRED
```

### APIs

```text
GET /api/v2/admin/reconciliation/exceptions
GET /api/v2/admin/reconciliation/exceptions/{exceptionId}
POST /api/v2/admin/reconciliation/exceptions/{exceptionId}/assign
POST /api/v2/admin/reconciliation/exceptions/{exceptionId}/resolve
POST /api/v2/admin/reconciliation/exceptions/{exceptionId}/carry-forward
```

### Tests

- Exceptions are created when validation rules fail.
- High-severity exceptions block daily close.
- Resolved exceptions require resolution notes.
- Carry-forward exceptions remain visible on the next business date.

---

## 4. Finance Daily-Close Enforcement

### Problem

Daily close should be an enforceable workflow, not merely a checklist.

### Daily Close Preconditions

Block close when:

- provider statements are missing
- statement validation has failed
- reconciliation import has not completed
- high-severity exceptions are unresolved
- variance exceeds approved tolerance
- maker-checker approvals are missing
- callback failures for completed transactions are unresolved
- operating-control events are unresolved
- finance owner has not signed off

### APIs

```text
GET /api/v2/admin/finance-close/days/{businessDate}
POST /api/v2/admin/finance-close/days/{businessDate}/precheck
POST /api/v2/admin/finance-close/days/{businessDate}/submit
POST /api/v2/admin/finance-close/days/{businessDate}/approve
POST /api/v2/admin/finance-close/days/{businessDate}/reopen
```

### Tests

- Daily close fails when provider statements are missing.
- Daily close fails when variance exceeds tolerance.
- Finance maker cannot approve own close submission.
- Close evidence is immutable after approval unless reopened by an authorized role.

---

## 5. Reports and Exports

### Standard Reports

Implement permission-controlled export for:

- daily transaction summary
- merchant settlement statement
- provider settlement report
- payout report
- refund/reversal report
- reconciliation exception report
- failed transaction report
- compliance case summary
- provider SLA report
- treasury exposure report
- production readiness evidence report

### Export Formats

- CSV
- XLSX
- PDF where required
- JSON API response

### Export Audit

Every export should record:

- actor
- report type
- filters
- generated timestamp
- file reference
- row count
- checksum/hash where applicable

### Tests

- Unauthorized roles cannot export restricted reports.
- Report totals match source records.
- Export audit record is created.
- Date/currency/provider filters are applied consistently.

---

## 6. Incident Management

### Incident Entity

`operational_incident` with:

- incident ID
- severity: `SEV1`, `SEV2`, `SEV3`, `SEV4`
- title
- affected provider/channel
- affected merchant scope
- production/sandbox environment
- detected at
- owner
- status
- timeline events
- root cause
- corrective action
- postmortem link/export

### Incident Triggers

Create incidents or incident candidates from:

- provider outage
- high failed payout rate
- callback backlog
- settlement variance breach
- reconciliation import failure
- daily close overdue
- suspicious payout spike
- compliance SLA breach
- privileged action anomaly

### APIs

```text
GET /api/v2/admin/incidents
GET /api/v2/admin/incidents/{incidentId}
POST /api/v2/admin/incidents
POST /api/v2/admin/incidents/{incidentId}/timeline
POST /api/v2/admin/incidents/{incidentId}/assign
POST /api/v2/admin/incidents/{incidentId}/resolve
POST /api/v2/admin/incidents/{incidentId}/postmortem
```

### Tests

- High-severity operating-control event can create an incident candidate.
- Incident timeline is append-only.
- Closed incident requires resolution summary.
- Incident export includes timeline and linked evidence.

## P1 Definition of Done

P1 is done when:

- settlement batches have lifecycle states and approvals
- treasury positions reflect available, reserved, pending, and exposure balances
- reconciliation exceptions have owners and severity
- daily close is blocked by unresolved critical conditions
- finance and operations reports are exportable and audited
- incidents can be created, assigned, resolved, and exported
- tests cover settlement state transitions, close blocking, treasury reservation, exception handling, and incident workflows