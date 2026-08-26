# Standards Applicability Matrix

This matrix turns the requested ISO list into an explicit Cito/CPay applicability decision. It deliberately distinguishes certifiable management-system requirements, guidance standards and interface/message standards.

| Standard | Repository baseline | Applicability to Cito/CPay | Primary control domains | Evidence / implementation |
|---|---|---|---|---|
| ISO 9001 | 2015 + Amd 1:2024 | Applicable to the software/service quality management system. Migrate the IMS baseline after the replacement edition is published and formally adopted. | customer requirements, process control, design/development, supplier quality, measurement, nonconformity, corrective action, continual improvement, climate context | IMS manual; quality objectives in `ops/iso/governance.json`; CI/test evidence; support/incident records; supplier reviews; internal audits |
| ISO/IEC 27001 | 2022 | Applicable to the information security management system. | ISMS scope, risk assessment/treatment, control applicability, security objectives, competence, operation, measurement, audit, management review, improvement | `isms-risk-and-control-framework.md`; security architecture; CodeQL/OWASP/SBOM; access controls; logging; risk/control register |
| ISO/IEC 27000 | 2026 | Guidance/vocabulary context. Not treated as an independent certification target. | consistent ISMS concepts and terminology | this IMS documentation and security governance vocabulary |
| ISO/IEC 20000-1 | 2018 | Applicable to Cito/CPay technology service management. | service portfolio/catalogue, relationship/SLA, incident/request/problem, change/release, configuration, availability/capacity, continuity, security, supplier, monitoring/improvement | service register; incident/operations runbooks; monitoring; release workflows; supplier register; continuity controls |
| ISO/IEC 27032 | 2023 | Cybersecurity guidance integrated into the ISMS/service processes. Not treated as an independent certifiable management system. | Internet/cyberspace threats, collaboration, incident coordination, vulnerability/threat information, awareness | cybersecurity section of `service-continuity-and-cybersecurity.md`; security incident process; dependency/security scanning |
| ISO 22301 | 2019 | Applicable to business continuity for critical Cito/CPay services and supporting operations. | BIA, continuity strategy, response, recovery, exercises, RTO/RPO, communications, evaluation/improvement | critical service register; backup/restore/DR runbook; continuity plan and exercise records |
| ISO 20022 | Applicable current parts and message definitions | Conditional technical applicability. Required only for a bank/clearing/counterparty interface whose agreed profile is ISO 20022. | business-message identifiers, message definitions, schema/profile control, parties/BIC, validation, secure XML handling, translation/reconciliation | `financial-messaging-interoperability.md`; adapter-specific schemas/profiles; certification evidence; financial-message boundary classes |
| ISO 8583 | 2023 | Conditional technical applicability for card/switch/acquirer/issuer interfaces using ISO 8583. It is not imposed on mobile-money REST/OpenAPI integrations that do not use the format. | MTI/message structure, data elements, network-specific profile/dictionary, sensitive authentication/cardholder data protection, correlation and reconciliation | message envelope/redaction boundary; scheme/network profile; connectivity/certification test results |
| ISO 9362 | 2022 | Applicable wherever a BIC is accepted, stored, generated, routed or emitted. Structural validation does not prove that a BIC is active/assigned. | BIC syntax, party addressing/routing, authoritative registry verification | `BicValidator`; external BIC directory/partner verification at onboarding and routing boundaries |
| ISO 32212 | 2026 | Conditional. Legal/compliance must determine whether the responsible entity is a financial institution in scope or whether Cito adopts it voluntarily. | net-zero transition governance, planning, objectives, implementation, monitoring/review and credible disclosure | `net-zero-transition-planning.md`; approved applicability/materiality assessment; controlled transition plan and evidence |

## Shared management-system clauses

The certifiable management-system standards share a compatible structure. Cito therefore operates one integrated cycle instead of parallel bureaucracies:

1. determine organizational context, interested parties, scope and applicable obligations;
2. establish policy, leadership accountability and roles;
3. identify risks, opportunities and measurable objectives;
4. provide competent people, controlled information, infrastructure and communications;
5. operate controlled design/development, service, security, continuity, supplier and financial processes;
6. monitor objectives and controls, run internal audits and management review;
7. correct nonconformities and continually improve.

## Explicit non-applicability rules

A control can be marked not applicable only when:

- the reason is recorded in the local control register;
- no law, contract, provider/scheme rule or risk treatment requires it;
- the exclusion does not compromise the ability to meet the management-system outcome;
- the accountable owner approves the decision and sets a review date.

For ISO/IEC 27001, the local Statement of Applicability records control references and implementation decisions without reproducing copyrighted control text.

## Financial messaging profile rule

No generic “ISO compliant” switch exists. Each production financial-message connection must record:

- counterparty/network;
- standard and edition/profile;
- message types and usage rules;
- schema/data-dictionary version;
- transport/security profile;
- identifier requirements including BIC where used;
- sensitive-data fields and logging rules;
- certification/test evidence;
- reconciliation mapping and exception process;
- owner and change-notification process.

A message format that is not required by the counterparty must not be added merely to create an ISO checkbox.
