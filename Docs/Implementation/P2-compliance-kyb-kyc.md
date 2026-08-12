# P2 - Compliance, KYB, KYC, and Risk Operations

## Objective

P2 formalizes compliance operations so CPay can support regulated merchant onboarding, transaction monitoring, payment holds, case review, and evidence-based decisions.

## P2 Deliverables

1. Merchant KYB lifecycle
2. Individual KYC lifecycle where applicable
3. Beneficial ownership capture
4. Compliance case management
5. Transaction monitoring rules
6. Screening provider integration boundary
7. Regulatory evidence pack

---

## 1. Merchant KYB Lifecycle

### Problem

A merchant account in pending approval is not a regulated KYB workflow. CPay needs explicit KYB states, required data, document evidence, beneficial owner capture, review decisions, and expiry/renewal.

### KYB States

```text
DRAFT
SUBMITTED
UNDER_REVIEW
INFO_REQUESTED
APPROVED
REJECTED
EXPIRED
SUSPENDED
```

### Required Merchant Profile Fields

- registered business name
- trading name
- business registration number
- tax identification number
- business type
- registration country
- physical address
- operating address
- primary contact name
- primary contact email
- primary contact phone
- expected monthly collection volume
- expected monthly payout volume
- expected countries/corridors
- business use case
- source of funds description
- website/app URL where applicable

### Required Documents

- certificate of incorporation or registration
- tax certificate where applicable
- operating license where applicable
- proof of address
- director/authorized representative ID
- board resolution or authorization letter where applicable
- ownership declaration

### APIs

```text
GET /api/v2/merchant-self-service/kyb/profile
POST /api/v2/merchant-self-service/kyb/profile
POST /api/v2/merchant-self-service/kyb/documents
POST /api/v2/merchant-self-service/kyb/submit
GET /api/v2/admin/kyb/queue
GET /api/v2/admin/kyb/merchants/{merchantId}
POST /api/v2/admin/kyb/merchants/{merchantId}/decision
POST /api/v2/admin/kyb/merchants/{merchantId}/request-info
POST /api/v2/admin/kyb/merchants/{merchantId}/suspend
```

### Tests

- Merchant cannot submit incomplete KYB profile.
- Merchant cannot edit approved KYB data without opening a review/amendment.
- Production activation is blocked until KYB is approved where required.
- Admin KYB decision writes audit trail.

---

## 2. Individual KYC Lifecycle

### Problem

Individual users, operators, beneficiaries, or payees may require identity checks depending on the product and corridor.

### KYC States

```text
NOT_STARTED
SUBMITTED
UNDER_REVIEW
APPROVED
REJECTED
EXPIRED
SUSPENDED
```

### KYC Fields

- legal name
- date of birth where required
- nationality
- phone number
- email
- ID type
- ID number
- ID country
- address
- selfie/liveness reference where required
- screening status

### APIs

```text
GET /api/v2/kyc/profile
POST /api/v2/kyc/profile
POST /api/v2/kyc/documents
POST /api/v2/kyc/submit
GET /api/v2/admin/kyc/queue
POST /api/v2/admin/kyc/{profileId}/decision
```

### Tests

- KYC expiry moves profile out of approved status.
- Rejected KYC records preserve evidence and reason.
- Restricted flows check KYC status before execution.

---

## 3. Beneficial Ownership

### Problem

Merchant KYB is incomplete without beneficial owner capture and review.

### Entity

`beneficial_owner` with:

- merchant ID
- full legal name
- ownership percentage
- role/title
- nationality
- ID type
- ID number
- phone/email
- address
- screening status
- KYC profile reference
- review status

### Controls

- Require ownership declaration before KYB approval.
- Require review for owners above configured threshold.
- Require re-review when owners change.
- Block production activation if ownership review is incomplete.

### Tests

- Merchant cannot be KYB-approved without required owner records.
- Owner changes reopen KYB review where configured.
- Beneficial owner screening hit creates compliance case.

---

## 4. Compliance Case Management

### Case Types

```text
MERCHANT_KYB_REVIEW
INDIVIDUAL_KYC_REVIEW
BENEFICIAL_OWNER_REVIEW
HIGH_VALUE_TRANSACTION
VELOCITY_LIMIT_BREACH
SANCTIONS_SCREENING_HIT
PEP_SCREENING_HIT
SUSPICIOUS_ACTIVITY
CROSS_BORDER_REVIEW
PROVIDER_EXCEPTION
```

### Case States

```text
OPEN
UNDER_REVIEW
INFORMATION_REQUESTED
HOLD_APPLIED
APPROVED
REJECTED
ESCALATED
CLOSED
```

### Case Fields

- case ID
- case type
- severity
- status
- subject type and ID
- related transaction/payment/payout/transfer
- related merchant
- assigned owner
- opened at
- due at
- decision
- decision reason
- evidence references

### APIs

```text
GET /api/v2/admin/compliance/cases
GET /api/v2/admin/compliance/cases/{caseId}
POST /api/v2/admin/compliance/cases/{caseId}/assign
POST /api/v2/admin/compliance/cases/{caseId}/hold
POST /api/v2/admin/compliance/cases/{caseId}/release
POST /api/v2/admin/compliance/cases/{caseId}/decision
POST /api/v2/admin/compliance/cases/{caseId}/escalate
POST /api/v2/admin/compliance/cases/{caseId}/request-info
```

### Tests

- Compliance case can hold a payout or transfer.
- Held transaction cannot execute until released.
- Case closure requires authorized compliance role.
- Decision records actor, reason, timestamp, and evidence.

---

## 5. Transaction Monitoring Rules

### Rule Types

- high-value transaction
- rapid payout velocity
- repeated failed payouts
- repeated undetermined outcomes
- new beneficiary high-value payout
- multiple merchants sharing contact details
- unusual transaction volume spike
- refund abuse pattern
- high-risk corridor
- payout immediately after merchant profile change

### Rule Entity

`transaction_monitoring_rule` with:

- rule code
- description
- enabled flag
- severity
- merchant scope
- channel scope
- country/currency scope
- threshold values
- time window
- action: `LOG_ONLY`, `CREATE_CASE`, `HOLD_TRANSACTION`, `BLOCK_TRANSACTION`

### APIs

```text
GET /api/v2/admin/compliance/monitoring-rules
POST /api/v2/admin/compliance/monitoring-rules
POST /api/v2/admin/compliance/monitoring-rules/{ruleId}/enable
POST /api/v2/admin/compliance/monitoring-rules/{ruleId}/disable
GET /api/v2/admin/compliance/alerts
```

### Tests

- Enabled rule creates alert when threshold is crossed.
- Hold action blocks transaction execution.
- Disabled rule does not trigger.
- Rule hit is visible in compliance dashboard.

---

## 6. Screening Integration Boundary

### Problem

CPay should integrate with screening providers without hardcoding one vendor.

### Implement Interface

`ScreeningProvider` with operations:

- screen merchant
- screen individual
- screen beneficial owner
- screen beneficiary
- screen transaction

### Screening Result Fields

- provider code
- subject type
- subject ID
- request reference
- match status
- match score
- categories: sanctions, PEP, adverse media, internal denylist
- raw response reference
- reviewed flag
- reviewed by
- decision

### Tests

- Screening provider can be swapped through configuration.
- Screening hit creates compliance case.
- Raw sensitive response is protected from unauthorized roles.

---

## 7. Regulatory Evidence Pack

### Implement Exports

- KYB decision report
- KYC decision report
- compliance case report
- transaction monitoring alert report
- held/released transaction report
- suspicious activity review pack
- beneficial ownership report
- merchant production approval report

### Evidence Rules

- Every report must include generation audit.
- Every report must preserve applied filters.
- Sensitive reports require compliance or auditor role.
- Report downloads are logged.

## P2 Definition of Done

P2 is done when:

- KYB and KYC states are explicit and enforced
- merchant production activation checks KYB requirements
- beneficial owners are captured and reviewed
- compliance cases can hold/release/reject transactions
- transaction monitoring can create alerts/cases automatically
- screening provider integration is abstracted
- compliance evidence reports can be generated and audited
- tests cover KYB, KYC, ownership changes, case holds, monitoring rules, and screening hits