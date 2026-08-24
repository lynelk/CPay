# Cito Integrated Management System (IMS)

This directory is the controlled management-system layer for the Cito multi-tenant platform and its CPay payments module.

It organizes existing engineering, security, operations, compliance, finance, supplier, continuity and payment-interoperability controls into one evidence-driven system aligned to the standards listed in `standards-applicability-matrix.md`.

## Scope

The IMS scope is the design, development, operation, support and continual improvement of:

- the Cito platform, including merchant, administrator, developer, validation, communications, marketplace, identity and integration capabilities;
- the CPay payments module, including collections, payouts, routing, refunds, reconciliation, settlement, ledger, cross-border and provider integrations;
- software delivery, infrastructure, databases, monitoring, incident response, business continuity, supplier management and customer support processes that materially affect those services.

Corporate functions outside this repository remain in scope when they control people, legal, regulatory, physical-security, procurement, insurance, finance, privacy or certification evidence. Repository controls do not replace those accountable functions.

## Standards baseline

The controlled baseline used by this repository is:

- ISO 9001:2015, including Amendment 1:2024, until the replacement edition is formally adopted;
- ISO/IEC 27001:2022;
- ISO/IEC 27000:2026 as vocabulary and ISMS guidance context;
- ISO/IEC 20000-1:2018;
- ISO/IEC 27032:2023 as cybersecurity guidance;
- ISO 22301:2019;
- ISO 20022, using the applicable current message-definition and schema versions for each connected clearing, bank or partner profile;
- ISO 8583:2023 where an acquiring, issuing, switching or payment-network profile actually uses ISO 8583;
- ISO 9362:2022 for BIC structure and BIC-based addressing where applicable;
- ISO 32212:2026 only when the legal/compliance applicability assessment determines that the responsible Cito/CPay entity is in scope as a financial institution or otherwise adopts the standard voluntarily.

The duplicate ISO 27001, ISO 8583 and ISO 32212 entries in the original programme request are treated once each.

## Certification statement

Repository alignment is not certification. No document, CI result or internal approval may describe Cito or CPay as ISO certified unless a valid certificate has been issued by the appropriate accredited certification body and its scope covers the claimed service/entity.

ISO 20022 and ISO 8583 implementation also does not imply network or scheme certification. A network-specific message profile, connectivity test pack and counterparty/scheme signoff are required before claiming conformance for a production interface.

## Controlled document hierarchy

1. `Integrated-management-system-manual.md` defines policy, governance and the management cycle.
2. `standards-applicability-matrix.md` defines how each requested standard applies.
3. `isms-risk-and-control-framework.md` defines information-security risk treatment and the local Statement of Applicability approach.
4. `service-continuity-and-cybersecurity.md` defines service management, continuity and cyber coordination requirements.
5. `financial-messaging-interoperability.md` defines ISO 20022, ISO 8583 and ISO 9362 engineering boundaries.
6. `net-zero-transition-planning.md` defines ISO 32212 applicability and transition-planning controls.
7. `internal-audit-management-review.md` defines assurance, nonconformity, corrective action and management review.
8. `ops/iso/governance.json` is the machine-readable control, objective, risk, service and supplier register.
9. `ops/iso/validate_iso_governance.py` is the fail-closed consistency validator used in CI.

Existing runbooks and architecture documents remain authoritative for execution details and are referenced as evidence rather than duplicated here.

## Control rules

- Every material control has an accountable owner role and evidence reference.
- Every critical service has a service owner, target availability/SLO, RTO, RPO and dependency record.
- Every material risk has treatment, residual-risk rating, review date and explicit acceptance where residual risk remains high.
- Every production change records security, quality, service, continuity, privacy, financial-integrity and interoperability impact.
- Every major incident produces a post-incident review and corrective-action record.
- Every management-system document has a review cadence; stale governance metadata fails the ISO governance CI job.
- Human or external approvals are never synthesized by automation.
