# Cito Integrated Management System Manual

## 1. Purpose and policy

Cito operates an integrated quality, information-security, service-management and business-continuity system for the Cito platform and CPay payments module. The system is intended to make customer outcomes, security, service reliability, financial integrity, regulatory obligations and recovery capability measurable and repeatable.

Management commits to:

- meet applicable customer, contractual, legal, regulatory, provider and scheme requirements;
- protect confidentiality, integrity and availability according to risk and data classification;
- provide controlled, observable and recoverable technology services;
- prevent avoidable defects and financial-control failures through engineering and maker-checker controls;
- learn from incidents, complaints, audit findings, vulnerabilities and reconciliation exceptions;
- maintain competent personnel and controlled suppliers;
- continually improve the management system and the services it governs.

## 2. Context and interested parties

The IMS owner maintains a context review at least annually and after a material regulatory, ownership, market, architecture or supplier change. At minimum it considers:

- merchants, end customers and developers;
- employees, contractors and privileged operators;
- mobile-money operators, banks, payment schemes, clearing partners and identity/communications providers;
- regulators, tax authorities, data-protection authorities and financial-intelligence authorities where applicable;
- shareholders/management and external auditors;
- cloud, source-control, observability, communications and security suppliers.

Needs and obligations are translated into the risk, supplier, service, compliance, quality and control registers. Unresolved conflicts are escalated to management review.

## 3. Scope and boundaries

The scope is defined in `README.md`. Interfaces crossing the boundary are treated as supplier or customer interfaces and must have an accountable owner, contract/profile, data classification, service expectation, security requirement and continuity dependency.

The Cito/CPay naming boundary in `Docs/CITO_BRAND_AND_MODULE_BOUNDARY.md` remains authoritative: Cito is the platform; CPay is the payments module and keeps compatibility-sensitive payment identifiers.

## 4. Governance and roles

Minimum accountable roles are:

| Role | Accountability |
|---|---|
| Executive sponsor | IMS policy, resources, risk acceptance and management review |
| IMS lead | integrated system, objectives, document control, audit programme and corrective actions |
| Security owner | ISMS, threat/vulnerability management, security incidents and security risk treatment |
| Service owner | service catalogue, SLOs, availability, capacity, continuity and customer outcomes |
| Engineering owner | secure SDLC, architecture, testing, release integrity and technical debt |
| Operations owner | monitoring, incident command, change execution, backups and recovery exercises |
| Finance owner | ledger integrity, reconciliation, settlements, maker-checker and financial signoff |
| Compliance/privacy owner | legal/regulatory obligations, KYB/KYC, AML, privacy, retention and evidence |
| Supplier owner | due diligence, SLA/security clauses, performance, continuity and exit plans |
| Internal auditor | independent audit planning, evidence sampling and nonconformity reporting |

One person may hold multiple roles in a small organization, but an individual must not approve their own high-risk change, financial adjustment, risk acceptance or internal-audit finding closure where independence is required.

## 5. Planning: objectives, risks and opportunities

### 5.1 Quality and service objectives

Measurable objectives live in `ops/iso/governance.json`. They include service availability, incident response, recovery testing, backup success, vulnerability remediation, change failure, reconciliation completion, defect escape and customer/support response. Objectives have owner, measurement source, target and review cadence.

### 5.2 Risk management

The risk methodology is defined in `isms-risk-and-control-framework.md`. Risk treatment can be avoid, reduce, transfer or accept. High residual risks require explicit accountable approval and time-bounded review. Security risk is integrated with quality, continuity, supplier, privacy, regulatory and financial-integrity risk rather than maintained as an isolated spreadsheet.

### 5.3 Change planning

Every material change must identify:

- intended outcome and customer/merchant impact;
- security and privacy impact;
- data-classification and retention impact;
- availability, capacity, support and continuity impact;
- ledger, settlement, reconciliation or money-movement impact;
- provider/supplier and interoperability impact;
- migration and rollback strategy;
- test and post-deployment validation evidence;
- documentation/training changes.

The pull-request template captures these questions. Emergency changes follow the incident process and receive retrospective review.

## 6. Support processes

### 6.1 Competence and awareness

People performing privileged operations, development, incident command, finance/reconciliation, compliance or supplier assurance must have role-appropriate competence evidence. Required training includes security/privacy awareness, incident response, business continuity, secure development and job-specific financial/regulatory controls. Training completion and competence review are retained as controlled records.

### 6.2 Communication

External communications use approved merchant/support, provider, regulator and incident channels. Major incidents have a designated communications owner. Security incidents are disclosed only through the authorized incident/compliance path. Material customer communication is retained with the incident/problem record.

### 6.3 Documented information

Controlled records have owner, version/date, approval where required, retention rule and evidence location. Git history supplies version control for repository records; external approvals must reference an immutable ticket, signed document, audit report or controlled storage location. Secrets, raw identity documents, payment authentication data and other restricted information must never be embedded in repository evidence.

## 7. Operational control

### 7.1 Engineering and release

The normal release path requires peer-reviewed change, CI, dependency/security analysis, migration checks, API-contract checks, sandbox/staging validation where applicable and controlled production promotion. Existing controls in `.github/workflows`, `Docs/Production-code-controls.md`, `Docs/Testing-strategy.md` and the go-live evidence gate are incorporated by reference.

### 7.2 Service operations

Service management covers:

- service catalogue and ownership;
- incident and major-incident management;
- problem/root-cause management;
- change, release and deployment management;
- configuration and asset information;
- availability, capacity and performance;
- information security;
- service continuity;
- supplier/service-level management;
- monitoring, reporting and continual improvement.

`service-continuity-and-cybersecurity.md` defines minimum requirements.

### 7.3 Financial integrity

Payment, ledger, settlement and reconciliation controls remain governed by application-level idempotency, auditable state transitions, segregation of duties, maker-checker, immutable/accounting evidence, daily reconciliation and variance escalation. Provider/network formats are validated at adapter boundaries. No external message is considered authoritative without counterparty/profile validation appropriate to that integration.

### 7.4 Suppliers

Critical suppliers require risk classification, owner, security/privacy obligations, service expectations, incident notification requirements, continuity arrangements, data-location/subprocessor consideration where relevant and an exit/replacement plan. Supplier performance and open risks are reviewed at least annually, and more frequently for critical providers.

## 8. Performance evaluation

### 8.1 Monitoring and measurement

Management reviews objective performance using evidence from CI, monitoring, alerting, incident/problem records, customer/support cases, vulnerability management, supplier performance, reconciliation/finance controls, audits, continuity exercises and regulatory/compliance reviews.

Metrics must be attributable to a defined source and period. Manual estimates are identified as such.

### 8.2 Internal audit

An independent, risk-based audit programme covers the full IMS scope. `internal-audit-management-review.md` defines cadence, sampling and evidence. Audit findings are classified, assigned and tracked to closure with root cause and effectiveness review.

### 8.3 Management review

Top management reviews the IMS at planned intervals and records decisions on suitability, adequacy, effectiveness, objectives, risk, resources, customer outcomes, audit results, supplier performance, incidents, continuity tests and improvement actions.

## 9. Improvement

Nonconformities, incidents, escaped defects, missed SLOs, security findings, failed controls, complaints and material reconciliation exceptions may trigger corrective action. Corrective actions must address root cause rather than only the immediate symptom and include an effectiveness check.

Continual improvement is demonstrated through measurable objective movement, completed corrective actions, reduced repeat incidents, improved control automation and management-approved changes to the IMS.

## 10. Climate and sustainability considerations

The organization evaluates whether climate-related issues are relevant to its context, interested parties, infrastructure resilience, suppliers and service continuity. ISO 32212-specific net-zero transition controls are applied according to `net-zero-transition-planning.md` and do not become applicable merely because CPay processes money.
