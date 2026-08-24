# Production Incident, Major Incident, Problem and Continuity Response Runbook

## Purpose

This runbook defines the standard operating response for production incidents affecting Cito or CPay, including payments, callbacks, provider connectivity, authentication, merchant channel setup, reconciliation, balances, admin operations, security/privacy events and critical infrastructure.

It supports the integrated service-management, information-security and business-continuity processes. Incident restoration is distinct from problem/root-cause resolution and from business-continuity invocation.

## Severity levels

### SEV1 / major incident

Use when one or more of these conditions applies:

- customer payments or payouts are broadly failing or financial integrity cannot be trusted;
- balances/ledger/settlement/reconciliation are materially inconsistent;
- authentication/authorization or a security event creates material exposure;
- provider callbacks or critical queues are broadly stuck;
- a Tier 0/1 service is unavailable or likely to breach its RTO;
- daily finance close cannot proceed because of a material unexplained variance;
- a legal/regulatory/privacy notification assessment is potentially required;
- executive/incident command determines coordinated major-incident governance is necessary.

### SEV2

One provider, merchant, security domain or important workflow is materially degraded but the impact is contained and a safe workaround exists.

### SEV3

A non-critical admin workflow, report, dashboard or isolated defect is degraded without material customer, financial, security or regulatory impact.

## Incident roles

A SEV1 record must identify these roles. One person may hold multiple roles only when capacity and segregation-of-duty requirements permit.

| Role | Responsibility |
|---|---|
| Incident commander | severity, priorities, decisions, owners, timeline, continuity recommendation and closure authority |
| Technical lead | diagnosis, containment, recovery and technical evidence |
| Communications lead | staff/customer/merchant/provider status communication and cadence |
| Operations/service owner | SLO/service impact, monitoring, operational dependencies and recovery verification |
| Security owner | required for suspected compromise, fraud, credential/key or sensitive-data events |
| Finance owner | required when money movement, balances, ledger, settlement or reconciliation may be affected |
| Compliance/privacy owner | required for possible regulatory/privacy/KYB/KYC/reporting consequences |
| Provider/supplier owner | required when a third party materially contributes to the incident |

## First response checklist

1. Create/identify the incident record and timestamp detection.
2. Assign severity and incident commander.
3. Record affected service(s), merchants/customers, countries/providers and known financial/security impact.
4. Check operations dashboard, alerts, traces/logs and recent deploy/migration/configuration changes.
5. Check provider health, sandbox/certification history and supplier status where relevant.
6. Check merchant channel setup, callback queues/claims/parked tasks and relevant schedulers.
7. Check reconciliation unmatched/exception counts and ledger/settlement integrity where money movement is involved.
8. Preserve relevant deployment SHA, configuration revision, audit/security events and provider references.
9. Assign technical/communications/domain owners and next update time.
10. Determine whether containment, production freeze or business-continuity invocation is required.

## Safety rules

- Restoration speed does not override ledger/financial integrity.
- Do not retry payouts blindly. Confirm provider/network idempotency/replay behavior first.
- Do not bypass maker-checker for finance corrections merely to close an incident.
- Do not expose secrets, full PAN/track/PIN data, raw identity documents or other restricted evidence in incident chat/issues.
- Suspected credential/key compromise requires controlled rotation/revocation and assessment of affected transactions/sessions.
- Do not restore production traffic to a database whose integrity has not been validated.

## Payment incident actions

- Pause broad merchant onboarding or affected money movement when failures are systemic.
- Identify whether v1/v2/native/provider-specific paths are affected.
- Check provider/network failures before changing application routing.
- Confirm merchant/provider configuration and endpoint/certificate status.
- Capture CPay transaction, provider/network and merchant references.
- Verify idempotency/reversal/timeout semantics before replay.
- Reconcile the incident window after recovery where processing ambiguity existed.

## Callback/webhook incident actions

- Confirm merchant callback endpoint reachability and expected TLS/authentication.
- Confirm callback signing and allowlist configuration.
- Check claims, retry/backoff and parked tasks.
- Requeue only after root cause is corrected and replay safety is confirmed.
- Verify backlog and successful-delivery metrics return to normal.

## Reconciliation, settlement and balance incident actions

- Stop daily close when variance exceeds approved tolerance.
- Validate provider/statement source and parser/profile.
- Compare provider, merchant, transaction, settlement and ledger references, amount and currency.
- Assign exception categories and owners.
- Require checker approval before any financial adjustment/posting.
- Record pre/post correction balances and independent verification.

## Security/privacy incident actions

- Add the security owner immediately for suspected compromise, abuse, malware, credential/key leakage, unauthorized access or sensitive-data disclosure.
- Contain through account/session revocation, key/secret rotation, network/provider restriction or service isolation as appropriate.
- Preserve audit/log/deployment evidence under controlled access.
- Compliance/privacy/legal determines notification obligations and timelines.
- Reconcile affected money movement when compromise could have changed financial state.

## Business continuity invocation

Invoke or prepare continuity procedures when normal incident recovery is unlikely to restore a Tier 0/1 service within its RTO, system/data integrity cannot be established promptly, the primary platform/database/location is unavailable, a major cyber event requires isolation, or a critical supplier/personnel dependency is unavailable beyond normal workaround capability.

The incident commander recommends invocation. The executive/continuity owner authorizes it unless immediate containment is required to prevent greater harm.

Use `Docs/ISO/service-continuity-and-cybersecurity.md` and `Docs/Runbooks/Backup-restore-dr.md` for continuity/recovery requirements.

## Communication cadence

- SEV1: initial acknowledgement/assignment target within 10 minutes; stakeholder status at least every 30 minutes while materially active.
- SEV2: stakeholder status at least every 60 minutes while materially active.
- SEV3: at least once per business day or according to the relevant support SLA.

Each update states: current impact, what changed, actions underway, risks/unknowns and next update time. Customer/regulator statements use authorized communications/compliance owners.

## Service restoration and closure

Incident restoration requires:

- service health and key monitoring signals restored;
- affected financial state/reconciliation checked where relevant;
- security containment validated where relevant;
- known unsafe retry/replay conditions removed;
- affected owners confirm recovery;
- residual risk/workaround recorded;
- customer/provider communications updated as required.

The incident may be operationally closed when service is restored, but required problem/CAPA work remains open separately.

## Problem and post-incident review

A problem/PIR is mandatory for every SEV1 and for recurring/material SEV2 events, control failures or management-requested reviews.

The PIR is initiated within two business days and completed with an agreed corrective-action plan within five business days unless investigation complexity is documented. It records:

- timeline: detection, acknowledgement, containment, restoration and closure;
- customer/merchant/service/SLO and financial/security/regulatory impact;
- technical and process root cause;
- why prevention/detection/containment controls did or did not work;
- workaround/known error if unresolved;
- corrective/preventive actions, owner and due date;
- documentation/training/risk/control changes;
- effectiveness-check date.

Repeat incidents are evaluated for systemic nonconformity under `Docs/ISO/internal-audit-management-review.md`.

## Evidence and retention

The incident record references, without unnecessarily copying restricted content:

- alert/monitoring evidence;
- relevant logs/traces and audit events;
- deployment/configuration revisions;
- transaction/provider/reconciliation references;
- communications and decisions;
- recovery validation;
- problem/CAPA record;
- regulatory/customer notification assessment where applicable.

Retention follows applicable legal, contractual, security, privacy and finance requirements and `Docs/Data-retention.md`.
