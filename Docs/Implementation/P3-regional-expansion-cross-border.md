# P3 - Regional Expansion and Cross-Border Payments

## Objective

P3 expands CPay from domestic/provider-backed mobile-money operations into regional corridor-based transfers. This phase should follow P0, P1, P2, and P4 because cross-border payments multiply operational, settlement, FX, compliance, and user-experience risk.

## P3 Deliverables

1. Corridor model
2. Beneficiary model
3. FX quote engine
4. Cross-border transfer lifecycle
5. Corridor treasury and settlement
6. Cross-border compliance and reporting

---

## 1. Corridor Model

### Problem

Cross-border transfers require explicit corridor configuration. A Uganda-to-Kenya transfer is not merely a payout with a Kenyan MSISDN.

### Entities

- `corridor`
- `corridor_route`
- `corridor_partner`
- `corridor_limit`
- `corridor_fee_rule`
- `corridor_compliance_rule`
- `corridor_settlement_calendar`

### Corridor Fields

- source country
- destination country
- source currency
- destination currency
- supported source instruments
- supported destination instruments
- enabled providers/partners
- route priority
- fallback routes
- min amount
- max amount
- daily/monthly limits
- risk level
- required purpose codes
- settlement model
- cutoff time
- settlement delay
- status: `DRAFT`, `SANDBOX`, `ACTIVE`, `SUSPENDED`, `RETIRED`

### APIs

```text
GET /api/v2/admin/corridors
POST /api/v2/admin/corridors
GET /api/v2/admin/corridors/{corridorId}
POST /api/v2/admin/corridors/{corridorId}/enable
POST /api/v2/admin/corridors/{corridorId}/suspend
GET /api/v2/corridors/available
```

### Tests

- Disabled corridor cannot execute transfers.
- Corridor amount limits are enforced.
- Unsupported currency pair is rejected.
- Sandbox and production corridor states are separated.

---

## 2. Beneficiary Model

### Problem

Cross-border transfers require structured beneficiaries and payout instruments. Beneficiary validation must happen before transfer execution.

### Beneficiary Types

```text
INDIVIDUAL
ORGANISATION
MERCHANT
```

### Instrument Types

```text
MOBILE_MONEY_WALLET
BANK_ACCOUNT
CARD
CRYPTO_WALLET
```

### Entities

- `beneficiary`
- `beneficiary_instrument`
- `beneficiary_validation_record`
- `beneficiary_compliance_status`

### Beneficiary Fields

- beneficiary ID
- merchant/customer owner
- type
- legal name
- country
- address where required
- phone/email
- identity/business reference where required
- validation status
- compliance status
- risk rating

### APIs

```text
POST /api/v2/beneficiaries
GET /api/v2/beneficiaries
GET /api/v2/beneficiaries/{beneficiaryId}
POST /api/v2/beneficiaries/{beneficiaryId}/instruments
POST /api/v2/beneficiaries/{beneficiaryId}/validate
POST /api/v2/beneficiaries/{beneficiaryId}/suspend
```

### Tests

- Transfer cannot execute to unvalidated beneficiary where validation is required.
- Merchant cannot access another merchant's beneficiary.
- High-risk beneficiary creates compliance review.
- Suspended beneficiary blocks transfer execution.

---

## 3. FX Quote Engine

### Problem

Cross-border payments require quote lifecycle, rate source, spread, expiry, acceptance, and auditability.

### FX Quote Fields

- quote ID
- merchant/customer ID
- source country
- destination country
- source currency
- destination currency
- source amount
- destination amount
- rate
- spread
- fees
- rate source
- expires at
- accepted at
- status: `REQUESTED`, `ACTIVE`, `ACCEPTED`, `EXPIRED`, `CANCELLED`
- rounding delta

### APIs

```text
POST /api/v2/fx/quotes
GET /api/v2/fx/quotes/{quoteId}
POST /api/v2/fx/quotes/{quoteId}/accept
```

### Rules

- Expired quotes cannot be accepted.
- Accepted quote is immutable.
- Transfer amount must match accepted quote.
- Rate source must be recorded.
- FX spread must be visible in finance records.

### Tests

- Quote expires after configured TTL.
- Transfer using expired quote is rejected.
- Accepted quote cannot be modified.
- Destination amount is reproducible from stored rate, spread, and rounding policy.

---

## 4. Cross-Border Transfer Lifecycle

### Transfer States

```text
CREATED
QUOTE_REQUESTED
QUOTE_ACCEPTED
COMPLIANCE_PENDING
COMPLIANCE_HOLD
APPROVED
FUNDS_RESERVED
SUBMITTED_TO_PARTNER
PENDING_DELIVERY
DELIVERED
FAILED
CANCELLED
REFUNDED
SETTLED
```

### Transfer Fields

- transfer ID
- merchant/customer ID
- corridor ID
- beneficiary ID
- beneficiary instrument ID
- source amount/currency
- destination amount/currency
- FX quote ID
- purpose code
- compliance status
- treasury reservation reference
- provider/partner route
- provider reference
- status
- failure reason
- timestamps

### APIs

```text
POST /api/v2/cross-border/transfers
GET /api/v2/cross-border/transfers
GET /api/v2/cross-border/transfers/{transferId}
POST /api/v2/cross-border/transfers/{transferId}/cancel
POST /api/v2/cross-border/transfers/{transferId}/retry
GET /api/v2/admin/cross-border/transfers
```

### Execution Preconditions

A transfer cannot execute unless:

- source merchant/customer is approved
- corridor is active
- beneficiary is valid
- payout instrument is valid
- FX quote is active and accepted
- compliance rules pass
- funds are available or prefunded
- route is available
- idempotency key is valid

### Tests

- Missing accepted quote blocks execution.
- Compliance hold blocks partner submission.
- Funds are reserved before partner execution.
- Duplicate request returns idempotent result.
- Failed partner submission releases or marks funds according to configured policy.

---

## 5. Corridor Treasury and Settlement

### Problem

Cross-border payments create exposure across currencies, providers, partners, and settlement cycles.

### Entities

- `corridor_treasury_position`
- `corridor_settlement_batch`
- `corridor_settlement_item`
- `fx_gain_loss_posting`
- `partner_prefund_account`

### Treasury Fields

- corridor
- partner
- source currency
- destination currency
- prefunded balance
- reserved balance
- available balance
- unsettled exposure
- settlement cycle
- cutoff time
- last reconciliation timestamp

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

### APIs

```text
GET /api/v2/admin/cross-border/treasury/positions
POST /api/v2/admin/cross-border/treasury/recalculate
GET /api/v2/admin/cross-border/settlements
POST /api/v2/admin/cross-border/settlements/{settlementId}/calculate
POST /api/v2/admin/cross-border/settlements/{settlementId}/approve
POST /api/v2/admin/cross-border/settlements/{settlementId}/close
```

### Tests

- Cross-border settlement totals tie to delivered transfers.
- FX gain/loss postings are reproducible.
- Settlement cannot close with unresolved variance.
- Prefund balance blocks transfer if insufficient.

---

## 6. Cross-Border Compliance and Reporting

### Required Data

- originator
- beneficiary
- source country
- destination country
- source amount/currency
- destination amount/currency
- FX rate and quote ID
- purpose code
- provider/partner route
- compliance decision
- screening result reference
- settlement status

### Compliance Rules

- purpose code required by corridor
- beneficiary validation required
- high-risk corridor triggers case
- high-value transfer triggers case
- sanctions/PEP hit triggers hold
- repeated failed transfers trigger review
- unusual velocity triggers review

### Reports

- corridor volume report
- corridor failure report
- FX spread report
- compliance hold report
- beneficiary report
- cross-border settlement report
- partner exposure report
- regulatory evidence export

### APIs

```text
GET /api/v2/admin/reports/cross-border/volume
GET /api/v2/admin/reports/cross-border/failures
GET /api/v2/admin/reports/cross-border/fx-spread
GET /api/v2/admin/reports/cross-border/compliance-holds
GET /api/v2/admin/reports/cross-border/settlements
```

### Tests

- Missing purpose code blocks configured corridors.
- Compliance hold prevents partner submission.
- Reports filter by corridor, date, provider, country, currency, and status.
- Sensitive cross-border reports require compliance or finance role.

## P3 Definition of Done

P3 is done when:

- corridors are explicit and configurable
- beneficiaries and payout instruments are validated
- FX quotes are lifecycle-managed and auditable
- cross-border transfer states are enforced
- funds are reserved before execution
- compliance can hold/release cross-border transfers
- corridor settlement ties out to delivered transfers
- corridor and regulatory reports are exportable
- tests cover corridor limits, FX expiry, beneficiary validation, compliance holds, treasury reservation, and settlement variance