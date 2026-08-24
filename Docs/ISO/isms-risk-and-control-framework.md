# ISMS Risk and Control Framework

## 1. Purpose

This document defines the Cito information-security risk method and local Statement of Applicability (SoA) process used to support ISO/IEC 27001 alignment. It does not reproduce ISO control text. The organization must maintain licensed copies of applicable standards outside the repository where required.

## 2. Information-security objectives

Cito protects information and services according to business impact, legal/regulatory obligations, contractual commitments and threat exposure. Security objectives are maintained in `ops/iso/governance.json` and are reviewed at least quarterly.

## 3. Asset and data classification

Every critical service must identify material information assets and owners. Data is classified using these local classes:

- `PUBLIC`: intended for unrestricted disclosure.
- `INTERNAL`: non-public operational information whose disclosure is inconvenient but not materially harmful.
- `CONFIDENTIAL`: customer, merchant, commercial, security or operational data requiring controlled access and encryption in transit.
- `RESTRICTED`: credentials, secrets, sensitive identity/financial data, private keys, payment authentication data or regulated data requiring strongest access, encryption, logging and retention controls.

Raw secrets and restricted authentication material are prohibited from source control, logs, alerts, test fixtures and audit evidence. Evidence references must point to controlled storage rather than embedding restricted content.

## 4. Risk scoring

Risks are scored before and after treatment.

### Likelihood

1. Rare: exceptional conditions required.
2. Unlikely: plausible but not expected during normal operation.
3. Possible: could occur during the planning horizon.
4. Likely: has occurred or is expected without additional control.
5. Almost certain: recurring or continuously exposed.

### Impact

1. Negligible: no customer/financial/security consequence beyond routine correction.
2. Minor: localized service or internal impact.
3. Moderate: material merchant/service disruption, reportable internal control issue or limited sensitive-data exposure.
4. Major: broad service/financial impact, serious regulatory/contractual consequence or material sensitive-data compromise.
5. Severe: systemic financial-integrity failure, prolonged critical outage, major safety/legal impact or large-scale restricted-data compromise.

`riskScore = likelihood × impact`.

- 1-4: low
- 5-9: medium
- 10-15: high
- 16-25: critical

Critical risks require executive/security review before the affected production activity continues unless an incident commander authorizes a documented emergency containment action. High residual risks require explicit risk-owner acceptance with an expiry/review date before production continuation is authorized. Until that acceptance is actually recorded, the governance register must mark the risk `BLOCKED_PENDING_ACCEPTANCE`, identify the accountable acceptance owner and due date, and set `productionContinuationAuthorized` to `false`. This is a fail-closed governance state, not a substitute for an approval.

## 5. Risk lifecycle

Each material risk records:

- unique ID and title;
- affected service/asset/process;
- threat/event and consequence;
- owner;
- inherent likelihood/impact;
- existing controls and evidence;
- selected treatment and action owner;
- due date;
- residual likelihood/impact;
- acceptance approver/date and expiry when applicable;
- explicit pending-acceptance state when a high residual risk has not yet been accepted;
- next review date;
- status.

Review is required at least quarterly for high/critical risks, at least annually for all open risks, and after a material incident, architecture change, new supplier, new country/corridor, major provider change or regulatory change.

## 6. Local control domains

The local control register maps ISO/IEC 27001 requirements/control references to Cito control IDs. Core domains are:

- governance, policy, roles and segregation of duties;
- asset, data and records management;
- identity, authentication and privileged access;
- cryptography and key/secret lifecycle;
- secure architecture and engineering lifecycle;
- vulnerability, dependency and patch management;
- change, release and configuration control;
- logging, monitoring, alerting and incident response;
- network, endpoint, cloud and platform security;
- application/API/tenant isolation and abuse controls;
- supplier, provider and outsourcing security;
- backup, recovery and business continuity;
- privacy, regulatory and evidence controls;
- payment, ledger and reconciliation integrity;
- physical/personnel controls managed outside the repository but referenced by evidence.

## 7. Statement of Applicability process

The SoA is represented by control entries in `ops/iso/governance.json`. Each entry contains a local control ID, standards references, applicability, implementation status, accountable owner and evidence references.

Allowed applicability values:

- `APPLICABLE`
- `NOT_APPLICABLE`
- `CONDITIONAL`

Allowed implementation states:

- `IMPLEMENTED`
- `PARTIAL`
- `PLANNED`
- `NOT_APPLICABLE`

`NOT_APPLICABLE` requires a rationale and approver. `IMPLEMENTED` requires at least one evidence reference. `PARTIAL` and `PLANNED` require a due date/action reference in the risk or improvement register.

## 8. Secure engineering requirements

Material code changes must use the repository's security gates and peer review. At minimum:

- no embedded production secrets;
- authentication/authorization and tenant boundaries tested when changed;
- input validation and output encoding appropriate to the interface;
- protected cryptographic/key operations;
- dependency/SBOM and vulnerability analysis;
- static analysis;
- auditable state transitions for money movement/security decisions;
- fail-closed production configuration for security-critical settings;
- rollback/containment strategy;
- logging that supports investigation without leaking restricted data.

Payment and identity code receives enhanced review because defects can combine financial and privacy impact.

## 9. Vulnerability management

Vulnerabilities are triaged using exploitability, exposure and business impact, not CVSS alone. Default remediation targets are:

- actively exploited or critical Internet-facing: contain immediately, permanent remediation target 72 hours;
- high: 14 days;
- medium: 30 days;
- low: 90 days or documented risk acceptance.

Exceptions require risk acceptance with compensating controls and expiry.

## 10. Security incident integration

Security events that may affect confidentiality, integrity, availability, fraud, credentials or regulated data use `Docs/Runbooks/Production-incident-response.md` with the security owner added to the incident team. The team determines legal/regulatory/customer notification obligations and preserves investigation evidence under controlled access.

## 11. Control effectiveness

Control effectiveness is evaluated through CI results, access reviews, log/alert tests, backup/restore exercises, vulnerability metrics, penetration/security testing, incident recurrence, reconciliation exceptions, supplier reviews, internal audit and management review. An installed control with no operating evidence is not considered fully effective.
