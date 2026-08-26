# Backup, Restore, Disaster Recovery and Continuity Exercise Runbook

Use this runbook for scheduled backup assurance, isolated restore drills, disaster recovery and continuity evidence for critical Cito/CPay services.

## Recovery targets and ownership

The service catalogue in `ops/iso/governance.json` is the controlled source for service-specific RTO/RPO. This runbook's generic database target is a baseline, not permission to weaken a stricter service target.

| Area | Baseline target |
|---|---|
| Backup cadence | Daily logical MySQL dump plus provider-managed storage snapshots where available |
| Generic database RPO | 24 hours only where the service-specific BIA does not require a tighter target |
| Tier 0/1 production target | Use service-catalogue RPO; engineer backup/replication accordingly |
| Generic database RTO | 4 hours for restore into a verified application environment |
| Evidence | Backup job logs, immutable metadata, checksums, restore logs, timings, validation results, participants, approver and corrective actions |

Operations owns backup execution; the relevant service/data owner owns recovery acceptance. Finance must approve financial-integrity validation before money-moving services are resumed after a database recovery.

## Daily backup assurance

1. Confirm each scheduled database backup/snapshot completed successfully.
2. Confirm the artifact is encrypted at rest and separated from the application runtime failure domain.
3. Record database/service, timestamp, artifact size, checksum/immutable identifier, retention class and storage location/reference.
4. Confirm the backup metadata identifies schema/migration revision and source environment.
5. Confirm access to backup storage follows least privilege and is independently reviewed.
6. Open an operations incident/alert if a required backup is missing, incomplete, unencrypted, corrupt, outside retention policy or older than the applicable RPO.
7. Do not place database credentials or raw restricted data in backup evidence tickets.

## Monthly isolated restore drill

1. Define drill scope, services, current RTO/RPO targets and participants before starting.
2. Provision an isolated restore environment that cannot send live payments, payouts, SMS, callbacks, emails or provider/network messages.
3. Select a representative backup according to the exercise objective and record its recovery point.
4. Verify checksum/immutability metadata before restore.
5. Restore into a clean database.
6. Run Flyway/schema validation against the restored schema.
7. Start the backend with sandbox/non-money-moving provider settings only.
8. Verify representative read paths: admin authentication, merchant lookup, merchant statement/reporting, ledger trial balance and balance read models.
9. Verify transaction/ledger integrity checks appropriate to the restored revision.
10. Verify production provider callbacks, schedulers and money-moving jobs remain disabled.
11. Record restore start/finish, observed RTO, observed recovery point/RPO, validation results and any manual dependency/access delays.
12. Create corrective actions for every missed target or failed control.

## Annual Tier 0/1 recovery exercise

At least annually, and after material platform/database architecture changes, execute an end-to-end recovery exercise for Tier 0/1 services where technically and contractually feasible. The exercise should include application, database, networking/DNS/TLS, secrets/configuration, monitoring/alerting, provider isolation/routing, reconciliation and operator access.

Scenarios rotate across at least:

- primary database corruption/unavailability;
- cloud/runtime/region outage;
- critical provider outage combined with retry/backlog pressure;
- cyber compromise requiring credential/key rotation and service isolation;
- loss/unavailability of a critical operational dependency or key personnel.

Tabletop exercises may supplement but do not permanently replace technical restore/recovery tests.

## Emergency restore

1. Declare the incident and assign incident commander and restore owner.
2. Freeze deployments and jobs that could mutate the affected state.
3. Determine whether business continuity has been invoked and record the decision.
4. Preserve the damaged environment/read-only evidence where investigation or legal requirements demand it.
5. Select the newest known-good recovery point that satisfies the service's integrity/RPO decision.
6. Restore into a clean/controlled database/environment.
7. Run migration/schema validation and application smoke/integrity checks.
8. Compare critical ledger/balance/reconciliation/settlement state and investigate unexplained variance.
9. Re-establish monitoring, alerting and controlled provider connectivity.
10. Re-enable customer/money-moving traffic only after the incident commander plus service owner, operations and finance/security/compliance owners as applicable approve recovery.
11. Record actual RTO/RPO and initiate mandatory PIR/problem/CAPA for a SEV1/continuity event.

## Recovery integrity rules

- A faster restore with unverified financial state is not a successful recovery.
- Do not replay ambiguous payouts or provider messages until idempotency/network status is established.
- Secret/key restoration or rotation follows current security controls; stale compromised credentials are never restored merely because they existed in a backup.
- Restored personal/regulated data remains subject to retention/deletion/legal-hold requirements.
- Production and drill evidence references must not contain raw secrets or unnecessarily copied customer data.

## Exercise evidence record

Every material drill records:

- exercise ID/date/scenario and objective;
- services and recovery targets;
- participants/roles and suppliers involved;
- backup/recovery point selected;
- start, decision, restore and service-validation timestamps;
- observed RTO/RPO;
- integrity/reconciliation outcome;
- communications/escalation tests;
- deviations, failed dependencies or access problems;
- corrective actions, owner and due date;
- exercise approver and closure/effectiveness result.

## Post-drill management review input

Trend at least:

- backup success and age;
- restore success rate;
- observed RTO/RPO versus target;
- repeated manual bottlenecks;
- unresolved corrective actions;
- supplier/platform recovery dependencies;
- changes required to service criticality/BIA assumptions.

Material misses are raised in the risk register and management review rather than merely noted in drill minutes.
