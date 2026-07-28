# Compliance and Risk Controls

This document translates the EAC-context risk findings into implementation tracks.

## Implemented Foundation

| Control | Current State |
|---|---|
| Data retention | Retention policy documented in `Docs/Data-retention.md`; cleanup jobs exist for transient operational rows. |
| Sensitive read audit | Merchant statement export and account validation reads write to `merchants_audit_trail`. |
| Upload constraints | Spreadsheet uploads enforce size, MIME, extension, and row-count limits. |
| Production safety | Production profiles reject sandbox mode and SSL-bypass settings. |

## Required Risk Engine

Authorization-time rules should run before provider calls and ledger writes:

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

Self-service KYC should support tiers:

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

Successful Ugandan merchant payins should eventually trigger an e-receipt hook when configured. Regulator-facing reports should be scheduled exports sourced from the normalized ledger after the ledger roadmap lands.
