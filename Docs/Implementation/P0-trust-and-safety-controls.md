# P0 - Trust and Safety Controls

## Objective

P0 establishes the minimum controls CPay needs before it can be considered a serious production candidate. The focus is preventing incorrect money movement, unsafe provider activation, privileged misuse, duplicate execution, webhook opacity, and unobservable production failures.

## P0 Deliverables

1. Provider certification evidence workflow
2. Admin RBAC and maker-checker controls
3. Payout risk controls
4. Webhook retry/replay hardening
5. Money correctness and ledger guardrails
6. Production observability baselines

---

## 1. Provider Certification Evidence Workflow

### Problem

Provider adapters are not enough. A provider channel must not be considered production-ready until real sandbox and provider evidence has been captured, reviewed, and approved.

### Implement

Add certification entities:

- `provider_certification_run`
- `provider_certification_scenario`
- `provider_certification_evidence`
- `provider_certification_exception`

Required fields for `provider_certification_run`:

- provider code
- channel code
- country
- currency
- environment
- merchant scope or global scope
- status: `DRAFT`, `RUNNING`, `EVIDENCE_PENDING`, `REVIEW_PENDING`, `APPROVED`, `REJECTED`, `EXPIRED`
- created by
- reviewed by
- approved by
- timestamps

Required scenarios:

- collect accepted
- collect failed
- payout accepted
- payout failed
- invalid MSISDN/account
- insufficient funds
- duplicate merchant reference
- provider timeout
- provider unavailable
- provider callback received
- duplicate callback handled safely
- statement file uploaded
- statement validation passed
- reconciliation match passed
- reconciliation exception handled
- daily close dry run completed

### APIs

Add admin endpoints:

```text
POST /api/v2/admin/provider-certification/runs
GET /api/v2/admin/provider-certification/runs
GET /api/v2/admin/provider-certification/runs/{runId}
POST /api/v2/admin/provider-certification/runs/{runId}/scenario-result
POST /api/v2/admin/provider-certification/runs/{runId}/evidence
POST /api/v2/admin/provider-certification/runs/{runId}/exception
POST /api/v2/admin/provider-certification/runs/{runId}/approve
POST /api/v2/admin/provider-certification/runs/{runId}/reject
```

### Enforcement

Block production channel activation unless:

```text
certification.status = APPROVED
AND provider_environment = PRODUCTION
AND all_required_scenarios_passed = true
AND unresolved_blocking_exceptions = false
```

### Tests

- Cannot enable production provider without approved certification.
- Certification approval requires authorized admin role.
- Evidence records cannot be deleted after approval.
- Approved exception is included in readiness summary.

---

## 2. Admin RBAC and Maker-Checker Controls

### Problem

Sensitive admin operations must not depend on generic credentials or a single unchecked operator.

### Implement

Admin roles:

- `SUPER_ADMIN`
- `OPERATIONS_ADMIN`
- `FINANCE_MAKER`
- `FINANCE_CHECKER`
- `COMPLIANCE_OFFICER`
- `PROVIDER_MANAGER`
- `SUPPORT_AGENT`
- `READ_ONLY_AUDITOR`
- `SECURITY_ADMIN`

Create an authorization matrix mapping every `/api/v2/admin/**` endpoint to:

- allowed roles
- read/write permission
- maker-checker requirement
- audit level
- environment restriction

### Maker-Checker Required For

- merchant production activation
- provider production enablement
- settlement approval
- daily close
- manual finance adjustment
- callback secret rotation
- high-value payout release
- compliance case closure
- admin role change
- production transaction cap removal

### APIs

```text
GET /api/v2/admin/access/roles
GET /api/v2/admin/access/permissions
POST /api/v2/admin/approval-requests
GET /api/v2/admin/approval-requests
POST /api/v2/admin/approval-requests/{requestId}/approve
POST /api/v2/admin/approval-requests/{requestId}/reject
```

### Audit Fields

Every privileged action must write:

- actor ID
- role
- action
- affected resource type
- affected resource ID
- previous state hash
- new state hash
- reason
- request ID
- timestamp

### Tests

- Unauthorized role receives 403.
- Read-only auditor cannot mutate data.
- Maker cannot approve own request.
- Checker approval triggers target state transition.
- All privileged actions create audit records.

---

## 3. Payout Risk Controls

### Problem

Payouts move money out and require stricter controls than collections.

### Implement

Payout states:

```text
CREATED
RISK_REVIEW_REQUIRED
APPROVAL_PENDING
APPROVED
SUBMITTED
PENDING
SUCCESSFUL
FAILED
CANCELLED
REVERSED
```

Payout limit configuration by:

- merchant
- provider
- channel
- country
- currency
- beneficiary
- transaction amount
- daily amount
- monthly amount
- daily count
- monthly count

Risk triggers:

- first payout to a new beneficiary
- high-value payout
- unusual payout frequency
- payout after merchant profile change
- new provider/channel usage
- high-risk destination
- manual adjustment payout
- duplicate or near-duplicate reference

### APIs

```text
POST /api/v2/payouts
GET /api/v2/payouts/{payoutId}
POST /api/v2/payouts/{payoutId}/approve
POST /api/v2/payouts/{payoutId}/reject
POST /api/v2/payouts/{payoutId}/cancel
GET /api/v2/admin/payout-risk/queue
POST /api/v2/admin/payout-risk/rules
```

### Tests

- Duplicate payout idempotency key returns stored result.
- Same idempotency key with different body is rejected.
- High-risk payout enters approval queue.
- Approval requires correct role and maker-checker where configured.
- Rejected payout never reaches provider adapter.

---

## 4. Webhook Retry, Replay, and Transparency

### Problem

Callbacks and webhooks must be reliable, observable, replayable, and safe.

### Canonical Events

```text
payment.accepted
payment.pending
payment.successful
payment.failed
payment.undetermined
payout.accepted
payout.pending
payout.successful
payout.failed
refund.created
refund.successful
refund.failed
settlement.created
settlement.approved
settlement.paid
settlement.closed
compliance.hold
compliance.released
```

### Webhook Delivery Policy

- immediate first attempt
- exponential backoff
- configurable max attempts
- parked state after retries exhausted
- merchant-authorized replay
- admin-authorized replay
- signed event payload
- nonce and timestamp replay protection

### APIs

```text
GET /api/v2/webhooks/events
GET /api/v2/webhooks/events/{eventId}
POST /api/v2/webhooks/events/{eventId}/replay
POST /api/v2/merchant-self-service/callback/test
```

### Tests

- Replay is audited.
- Replay does not change original event ID unless intentionally issuing a new delivery attempt ID.
- Merchant cannot replay another merchant's event.
- Stale callback signature is rejected by test receiver examples.

---

## 5. Money Correctness and Ledger Guardrails

### Problem

Money must not be represented or calculated using floating-point arithmetic.

### Implement

Adopt a `MoneyAmount` value object or equivalent pattern with:

- amount minor units or BigDecimal with explicit scale
- ISO currency code
- rounding mode
- serialization rules
- arithmetic helpers

Rules:

- no `double` for money
- no implicit currency
- no fee/tax calculation outside the money rules service
- no settlement posting without balanced debit/credit entries
- no reversal by mutating historical entries

### Tests

- Static or unit tests detect prohibited floating-point money calculations in payment/settlement packages.
- Ledger postings balance per transaction.
- Reversal creates reversing entries.
- FX rounding differences are visible and explainable.

---

## 6. Production Observability Baseline

### Implement Metrics

- provider latency
- provider success rate
- provider timeout rate
- callback queue depth
- parked callback count
- payout approval aging
- reconciliation exception count
- settlement variance count
- compliance case aging
- API signing failure rate
- webhook delivery failure rate

### Alerts

- provider outage
- callback backlog
- high payout failure rate
- settlement variance breach
- daily close overdue
- suspicious payout spike
- compliance SLA breach
- privileged action anomaly

### Tests

- Critical workflow failures emit operating-control events.
- Metrics separate sandbox and production.
- Alert records include severity, owner, and resource reference.

## P0 Definition of Done

P0 is done when:

- provider production activation requires certification evidence
- sensitive admin operations use RBAC and maker-checker
- payout risk rules can block execution
- webhook retry/replay is documented and auditable
- money calculations use safe money types
- production metrics and alerts exist for core payment operations
- CI covers authorization, idempotency, provider certification, callback replay, and money correctness