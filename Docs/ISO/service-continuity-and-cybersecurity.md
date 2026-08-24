# Service Management, Business Continuity and Cybersecurity

## 1. Service-management model

Cito manages technology services using one lifecycle covering design, transition, operation, assurance and continual improvement. The service register in `ops/iso/governance.json` is the minimum controlled service catalogue.

Each critical service records:

- service name and owner;
- customers/users and business outcome;
- support hours and escalation path;
- availability/SLO target and measurement source;
- RTO, RPO and maximum tolerable disruption assumption;
- critical application, database, provider, network and people dependencies;
- security/data classification;
- supplier dependencies;
- monitoring/alerting evidence;
- continuity and recovery procedure.

## 2. Incident, request and problem management

### Incident management

`Docs/Runbooks/Production-incident-response.md` is the operational incident procedure. Incidents must record severity, service impact, start/detection/acknowledgement/recovery times, owner, communications, evidence, resolution and follow-up actions.

SEV1 incidents require an incident commander plus explicit technical, communications and affected-domain owners (for example security, finance, compliance or provider operations). One person can cover more than one role only when capacity and segregation-of-duty constraints permit.

### Service requests

Routine access, configuration, data/export and support requests use documented request paths and authorization. Requests that alter privileged access, production configuration, financial data or regulated information require additional approval/evidence appropriate to risk.

### Problem management

A problem record is required when:

- a SEV1 occurs;
- the same material incident recurs;
- a workaround is used for a known structural defect;
- an incident exposes a control-design failure;
- management, security, finance, compliance or a customer contract requires root-cause review.

Problem records identify root cause, known error/workaround, permanent corrective action, owner, due date and effectiveness check. Closure of the incident does not automatically close the problem.

## 3. Service level, availability and capacity

Service owners monitor SLOs and capacity trends against the catalogue. A sustained breach requires corrective action or explicit management acceptance. Capacity planning considers transaction growth, tenant growth, database/resource saturation, provider limits, queue/backlog growth, log/metric volume and disaster-recovery capacity.

Alert thresholds are engineering signals, not substitutes for SLO measurement. Production monitoring must exercise alert delivery and escalation paths at least quarterly.

## 4. Change, release and configuration

Normal changes require peer review, applicable CI gates, impact/rollback analysis and controlled deployment. High-risk changes affecting money movement, authentication, authorization, keys/secrets, migrations, ledger/accounting, provider routing or regulated data require explicit domain review.

Emergency changes are permitted only to contain/recover a material incident or urgent vulnerability. They are recorded against the incident and receive retrospective review no later than five business days after service stabilization.

Configuration records must identify production service, environment, source revision/deployment artifact, major dependencies and security-relevant settings without exposing secret values.

## 5. Business impact analysis and continuity tiers

The service catalogue establishes continuity tiers.

### Tier 0: financial/system integrity

Examples: core ledger/integrity controls, authentication required for privileged recovery, production database. Failure can make it unsafe to process transactions. Recovery prioritizes integrity over availability.

### Tier 1: critical transaction service

Examples: CPay payment orchestration, provider callback/state processing, critical reconciliation/settlement controls. Prolonged disruption materially affects customers or financial close.

### Tier 2: important supporting service

Examples: merchant/admin portals, reporting, developer/control-plane functions whose temporary outage has a documented workaround.

### Tier 3: non-critical service

Deferred features/content/services that can tolerate extended interruption without material financial, legal or customer impact.

Criticality, RTO/RPO and dependencies are reviewed annually and after significant product/provider/infrastructure changes.

## 6. Continuity invocation

Business/service continuity may be invoked when any of the following is expected to exceed normal incident-recovery capability:

- a Tier 0/1 outage threatens its RTO;
- the primary database/region/platform is unavailable or integrity cannot be trusted;
- a critical provider or network is unavailable with no normal routing workaround;
- a cyber incident requires isolation of production systems;
- loss of key personnel/facility/supplier prevents normal service operation;
- severe environmental/climate/power/connectivity conditions threaten the operating location or infrastructure chain;
- management determines that coordinated continuity/crisis governance is required.

The incident commander recommends invocation; the executive/continuity owner authorizes it unless immediate isolation/recovery is needed to prevent greater harm.

## 7. Recovery and exercises

`Docs/Runbooks/Backup-restore-dr.md` defines database backup/restore execution. Continuity assurance includes:

- daily backup monitoring;
- monthly isolated restore drill for critical data;
- quarterly alert/escalation exercise;
- at least annual scenario/tabletop exercise covering provider outage, cloud/platform outage and cyber compromise;
- at least annual end-to-end recovery exercise for Tier 0/1 services where technically and contractually feasible;
- follow-up corrective actions for failed exercise objectives.

Exercise evidence records scenario, scope, participants, assumptions, start/recovery times, observed RTO/RPO, failed controls, decisions and corrective actions.

## 8. Crisis and continuity communications

Every continuity event identifies communications ownership and authorized channels for staff, merchants/customers, providers, regulators and other interested parties. Regulatory/privacy notification decisions are made by the authorized compliance/legal owner, not by an engineer improvising in an incident chat.

## 9. Cybersecurity coordination

Cybersecurity is integrated into the ISMS and service-management lifecycle.

### Threat and vulnerability information

Security owners consume relevant vulnerability, dependency, cloud, provider and threat information and translate material items into a risk, issue, patch/change or incident. Threat intelligence is not retained merely as a news feed; it must produce an accountable decision when relevant.

### External collaboration

Contacts are maintained for critical providers, cloud/platform operators, financial partners, legal/compliance, regulators/law enforcement where appropriate and incident-response/security specialists. Contacts are tested/reviewed periodically.

### Vulnerability disclosure

Reports from customers/researchers are acknowledged through an approved security contact, protected from routine support exposure where sensitive, triaged promptly, and tracked to remediation or risk acceptance. Public disclosure/credit decisions are coordinated with security/legal.

### Cyber incident containment

Containment may include credential/key rotation, session revocation, provider/API isolation, traffic/routing restriction, feature disablement, network isolation or restoration from known-good state. Financial-integrity reconciliation follows containment when money-moving or ledger-related systems could have been affected.

### Evidence and forensics

Relevant logs, database snapshots, deployment revisions, access/audit records and provider references are preserved with access control and timestamps. Forensic collection must not introduce raw secrets or restricted customer data into GitHub issues or other unsuitable systems.

## 10. Supplier and service dependency management

Critical suppliers are classified in the governance register. Review covers service performance, incidents, security posture/evidence, data access/location, continuity, concentration risk, subcontractors where relevant, exit/portability and unresolved findings. Material supplier degradation becomes a service or enterprise risk and is reviewed in management review.

## 11. Continual improvement

Service reviews consider SLO trends, incidents/problems, customer/support signals, change failure, capacity, vulnerabilities, supplier performance, continuity exercises and audit findings. Repeated failure is treated as a process/control problem, not as an invitation to write a longer runbook around the same defect.
