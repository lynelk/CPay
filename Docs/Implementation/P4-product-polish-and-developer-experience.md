# P4 - Product Polish and Developer Experience

## Objective

P4 makes CPay usable by merchants, developers, finance teams, compliance teams, operations users, and administrators without requiring source-code archaeology or private support conversations.

This phase does not replace core controls from P0, P1, or P2. It makes those controls visible, testable, and usable.

## P4 Deliverables

1. Merchant onboarding workflow UI
2. Developer portal improvements
3. Payment links, hosted checkout, and invoice lifecycle polish
4. Mobile, USSD, SMS, email, and WhatsApp journey definitions
5. Operations dashboards
6. Documentation and sandbox polish

---

## 1. Merchant Onboarding Workflow UI

### Problem

Merchants need to know what blocks production activation. Admins need to know what requires review. Nobody should need to infer this from scattered tables.

### Merchant Onboarding Steps

```text
Account created
Email verified
Phone verified
KYB profile submitted
KYB approved
Beneficial owners reviewed
Payment channels configured
Sandbox provider tests passed
Webhook endpoint verified
Pricing/terms accepted
Business approval complete
Production active
```

### Merchant Portal Features

- onboarding progress stepper
- missing requirement list
- document upload status
- channel setup status
- sandbox test status
- webhook test status
- production cap visibility
- request production activation action

### Admin Portal Features

- onboarding queue
- KYB review queue
- channel approval queue
- production activation queue
- blocker summary
- review history

### Acceptance Criteria

- Merchant sees a clear production-readiness checklist.
- Admin sees pending reviews by type and age.
- Each checklist item links to the screen where it can be resolved.
- Unauthorized merchant roles cannot complete restricted steps.

---

## 2. Developer Portal Improvements

### Problem

A serious gateway must let developers integrate without guessing headers, manually constructing signatures from memory, or asking support for every callback failure.

### Developer Portal Features

- API key overview
- signing key rotation workflow
- v2 request signing playground
- sample payload generator
- sandbox test numbers
- sandbox outcome simulator
- Postman collection download
- OpenAPI download
- SDK/reference client links
- webhook endpoint setup
- test callback sender
- delivery log and replay
- error code explorer

### Documentation Pages

- `Docs/Developer-guide.md`
- `Docs/V1-to-v2-migration.md`
- `Docs/Sandbox-guide.md`
- `Docs/Webhook-guide.md`
- `Docs/Error-catalog.md`
- `Docs/Go-live-checklist.md`

### Acceptance Criteria

- A developer can complete collect, payout, status, and webhook verification from docs and portal alone.
- The signing playground can reproduce canonical string examples.
- Sandbox outcomes include success, failure, pending, timeout, and undetermined flows.
- Webhook test events are visibly distinguishable from real production events.

---

## 3. Payment Links, Hosted Checkout, and Invoice Lifecycle

### Problem

Payment links and invoices must have explicit lifecycle states and user-facing operational controls.

### Payment Link States

```text
CREATED
ACTIVE
PAID
PARTIALLY_PAID
EXPIRED
CANCELLED
REFUNDED
```

### Hosted Checkout Requirements

- token expiry
- merchant branding
- allowed channels per merchant
- customer MSISDN validation
- retry after failed attempt
- receipt generation
- checkout session logging
- fraud/rate limiting
- status polling with timeout
- customer-facing failure messages

### Invoice Requirements

- invoice number
- customer details
- line items
- due date
- partial payment policy
- reminder schedule
- payment status
- receipt download
- export/download

### APIs

```text
GET /api/v2/payment-links
GET /api/v2/payment-links/{paymentLinkId}
POST /api/v2/payment-links/{paymentLinkId}/cancel
GET /api/v2/invoices
GET /api/v2/invoices/{invoiceId}
POST /api/v2/invoices/{invoiceId}/cancel
POST /api/v2/invoices/{invoiceId}/send-reminder
GET /api/v2/checkout/{token}/status
GET /api/v2/checkout/{token}/receipt
```

### Acceptance Criteria

- Expired payment links cannot be paid.
- Paid one-time links cannot be reused.
- Invoice status is derived from linked transaction records.
- Customer receipts include amount, currency, reference, provider reference where available, fees where applicable, and timestamp.
- Merchant can export payment links and invoice reports.

---

## 4. Mobile, USSD, SMS, Email, and WhatsApp Journeys

### Problem

CPay has communication and channel surfaces, but the user journeys must be defined so payment flows do not duplicate business rules across channels.

### Required Journey Specs

Create journey documents for:

- mobile app collect request
- mobile app payout approval
- USSD customer payment
- USSD session timeout and recovery
- SMS receipt delivery
- email invoice delivery
- WhatsApp payment link delivery
- WhatsApp payment status notification
- channel operator payment initiation

### Design Rule

All channels must call existing payment application services. Do not duplicate payment rules inside UI, USSD menus, WhatsApp handlers, or SMS services.

### Acceptance Criteria

- Every channel journey has success, failure, pending, timeout, and cancellation paths.
- Channel-specific flows use the same payment lifecycle as API flows.
- Receipts and notifications share one canonical receipt payload model.
- Communication delivery failures do not mutate payment state.

---

## 5. Operations Dashboards

### Dashboards

Implement or polish dashboards for:

- provider health
- callback queue
- parked callbacks
- webhook delivery failures
- payout approval queue
- reconciliation exceptions
- settlement variance
- finance close progress
- compliance case aging
- merchant onboarding blockers
- incident timeline
- treasury exposure

### Dashboard Standards

Each dashboard should include:

- counts by severity/status
- filters by environment, provider, merchant, country, currency, date
- drill-down to source records
- owner/assignment where applicable
- export action where appropriate
- refresh action without full app reload

### Acceptance Criteria

- Operations users can identify provider degradation.
- Finance users can track daily close blockers.
- Compliance users can monitor case aging.
- Merchant support users can diagnose failed webhooks without database access.

---

## 6. Documentation and Sandbox Polish

### Required Docs

Add or update:

- developer guide
- sandbox guide
- webhook guide
- v1-to-v2 migration guide
- payment links guide
- hosted checkout guide
- invoice guide
- merchant onboarding guide
- provider certification guide
- finance close guide
- compliance operations guide
- incident response guide

### Sandbox Requirements

- documented sandbox base URL
- test credentials placeholder format
- test MSISDNs
- simulated success/failure/pending outcomes
- provider timeout simulation
- callback simulator
- webhook replay simulator
- statement sample files

### Acceptance Criteria

- New merchants can test in sandbox without production credentials.
- Developer docs include curl and Postman examples.
- Sandbox and production behavior differences are explicit.
- The go-live checklist references actual readiness gates and evidence requirements.

## P4 Definition of Done

P4 is done when:

- merchant onboarding is workflow-driven and visible
- developer portal supports signing, sandbox, keys, webhooks, and examples
- payment link, checkout, and invoice lifecycles are explicit
- mobile/USSD/SMS/email/WhatsApp journeys are documented and use shared services
- operations dashboards expose actionable blockers
- documentation supports integration without private support intervention
- tests cover payment link expiry, checkout reuse prevention, invoice status, webhook test events, and merchant access controls