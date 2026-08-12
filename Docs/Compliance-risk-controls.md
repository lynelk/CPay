# Compliance and Risk Controls

This document translates the EAC-context risk findings into implementation tracks.

## Implemented Foundation

| Control | Current State |
|---|---|
| Data retention | Retention policy documented in `Docs/Data-retention.md`; cleanup jobs exist for transient operational rows. |
| Sensitive read audit | Merchant statement export and account validation reads write to `merchants_audit_trail`. |
| Upload constraints | Spreadsheet uploads enforce size, MIME, extension, and row-count limits. |
| Production safety | Production profiles reject sandbox mode and SSL-bypass settings. |
| Risk decisioning | Legacy and v2 pay-in/pay-out paths run authorization-time risk checks before provider execution. |
| KYC tier caps | Merchant tier limits are read from `compliance_profiles.tier` and applied to transaction/daily caps. |
| Payer velocity | The same payer identifier is capped over a rolling window. |
| Compliance operations | `/api/v2/admin/compliance/**` exposes summaries, reports, event review, cases, case decisions, and profiles. |
| KYB review | `/api/v2/admin/kyc/**` supports beneficial-owner and document capture/review. |
| Regulator reporting | `/api/v2/admin/regulator/**` can generate daily cash-flow/transaction summaries and PII inventory from internal data. |

## Required Risk Engine

Authorization-time rules should run before provider calls and ledger writes. Current coverage
includes flat caps, KYC-tier caps, payer velocity, and blocklist/screening foundations; new rules
should continue emitting auditable decisions.

- per-merchant daily amount caps
- per-payer velocity caps
- KYC-tier transaction limits
- blocklisted payer/payee values
- country, currency, and provider restrictions
- manual-review rules for high-risk payouts

Rules must emit an auditable decision with rule ID, input summary, decision, and actor/system source.

## AML and Sanctions

The target integration is a provider-neutral screening interface:

- request payload: name, account, country, merchant, transaction amount, direction
- response payload: clear, review, blocked
- provider reference and match details stored separately from transaction rows
- provider outage should follow a configured fail-open/fail-closed policy per merchant tier

## KYC

Self-service and admin-assisted KYC should support tiers:

| Tier | Requirements | Limits |
|---|---|---|
| Starter | Email verification and phone/account validation. | Low daily cap. |
| Business | Company details, TIN/registration, director contact. | Medium cap. |
| Enhanced | Document review and compliance approval. | Configured cap. |

## Data Protection

- Maintain a PII inventory covering merchants, merchant admins, payer numbers, callbacks, and statements.
- Mask payer/payee numbers in logs and non-diagnostic UI surfaces.
- Define deletion/anonymization workflows where legal retention allows.
- Keep regulator and finance exports generated from ledger/read models, not ad hoc dashboard queries.

## EFRIS and Regulator Reporting

Successful Ugandan merchant payins can queue an EFRIS e-receipt record when configured, but the
current implementation is an extension point until real EFRIS/URA credentials, schemas, and
certification are confirmed. Do not call it a certified e-receipt integration in production
materials.

Regulator-facing reports are generated from internal transaction/ledger/read-model data. The exact
BoU or other regulator submission schema and cadence still need compliance/legal confirmation
before any generated report is submitted externally.
