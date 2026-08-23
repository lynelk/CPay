# Cito / CPay Production Go-Live Manual Verification

## Purpose

This checklist contains the physical, external, financial, operational and formally authorized checks that cannot be proven by unit tests or source-code review alone. The codebase may be merged while these items remain open, but production deployment is intentionally fail-closed until the required evidence in `ops/readiness/go-live-evidence.json` is marked `VERIFIED`.

Do not place passwords, API secrets, provider tokens, customer data, private keys, test PINs or regulated identity records in the evidence register. Store sensitive evidence in the approved secure evidence repository and record only a ticket, document or artifact reference.

Run the gate with:

```bash
python3 ops/readiness/verify_go_live_evidence.py
```

A non-zero exit code means production go-live is not approved.

## 1. Production monitoring, alerting and support escalation

### Monitoring target

- Confirm the production backend health endpoint is reachable from the monitoring network.
- Confirm the production Prometheus/Micrometer scrape target is `UP` and carries the expected Cito/CPay application and environment labels.
- Confirm dashboards display current transaction throughput, provider success/failure, latency, callback/webhook failures, reconciliation exceptions, queue/backlog signals, JVM/runtime health and database-pool health.
- Confirm clocks/time zones are correct across application, database and monitoring systems so incident and reconciliation timestamps agree.
- Confirm logs and traces can be correlated by request/transaction/reference identifiers without exposing secrets or full sensitive payloads.

### Alert delivery drill

Physically trigger or inject a safe synthetic condition for every critical notification route and verify end-to-end delivery. At minimum verify:

- critical service unavailable alert;
- material payment/provider failure-rate alert;
- high callback/webhook failure alert;
- reconciliation or settlement exception alert;
- database/resource exhaustion alert;
- security/authentication abuse or rate-limit alert where configured.

For each route record the alert name, trigger time, receipt time, recipient, acknowledgement time and evidence reference. Verify alert recovery/resolution notifications as well as firing notifications.

### Support escalation

Verify, by actually contacting or test-notifying the channel:

- primary on-call engineer/operations receiver;
- secondary/backup on-call receiver;
- support mailbox or ticket queue;
- incident bridge/chat/war-room path;
- finance/reconciliation escalation contact;
- security incident contact;
- compliance escalation contact;
- MTN support/escalation contact;
- Airtel support/escalation contact;
- Airtel OpenAPI support/escalation contact;
- Safaricom support/escalation contact.

Confirm ownership, operating hours, acknowledgement expectations and the after-hours path. Replace stale personal contacts before signoff.

## 2. Real provider sandbox certification

Use the existing Provider Certification screen and backend evidence workflow. Evidence must come from the provider's real sandbox/test environment, not a local mock or CPay simulator.

Perform the following independently for **MTN**, **Airtel**, **Airtel OpenAPI**, and **Safaricom**.

### Physical prerequisites

- Provider-issued sandbox account/application is active.
- Sandbox API credentials are stored in the approved secret store, not source control.
- Required test MSISDN/SIM/mobile-money wallet is available and controlled by the test team.
- Required test wallet balance/float is sufficient for the provider scenarios.
- Provider IP allow-listing, callback URLs, webhook URLs and TLS requirements are registered.
- A provider support contact and provider-side application/account identifier are recorded in the secure evidence record.

### Authentication and connectivity

- Obtain/refresh a real provider access token or complete the provider's actual authentication handshake.
- Confirm outbound TLS connectivity from the intended CPay environment.
- Confirm invalid credentials fail safely without leaking credentials.
- Confirm credential/token expiry and refresh behavior.

### Transaction lifecycle

Where the provider product supports the operation, execute and capture evidence for:

- collection/payment initiation;
- successful terminal transaction;
- rejected/failed transaction;
- insufficient funds or equivalent business failure;
- invalid subscriber/account failure;
- provider timeout or delayed response;
- status/query lookup using the original CPay/provider reference;
- duplicate/idempotent retry behavior;
- callback/webhook delivery;
- callback authenticity/signature verification where provided;
- replay/duplicate callback handling;
- reversal/refund/cancellation flow where supported;
- payout/disbursement flow where enabled for the integration.

### Provider reconciliation

For every successful scenario verify that:

- amount and currency match the request;
- CPay reference, provider reference and merchant/customer reference can be traced end-to-end;
- final CPay state matches the provider state;
- provider fees/charges, if applicable, are represented correctly;
- callbacks do not cause duplicate financial postings;
- the transaction appears correctly in reconciliation/ledger views.

Capture the evidence in the certification workflow and obtain an authorized Cito reviewer approval for each required scenario. Record any provider acceptance ticket/email/reference in the secure evidence location.

## 3. Staging migration and balance reconciliation

Production data must not be used as a casual migration test bed. Use a separate staging environment with access restricted to the migration/reconciliation team.

### Staging environment

- Provision a dedicated staging application and database environment.
- Match the production MySQL major version, character set/collation, SQL mode and time-zone behavior as closely as practical.
- Restore an approved sanitized production-like snapshot or a controlled representative dataset.
- Record the source schema/Flyway version and target commit SHA before migration.

### Migration rehearsal

- Take a restorable pre-migration database backup/snapshot.
- Run Flyway migration from the current production schema version through the target release.
- Verify every migration is applied exactly once and Flyway history is clean.
- Verify application startup and health checks against the migrated schema.
- Exercise critical reads/writes across payments, refunds, marketplace splits, recurring payments, virtual accounts, provider certification, compliance, settlement and reconciliation paths that touch changed tables.
- Re-run the migration against an already-current database and verify it is a no-op.
- Perform the documented recovery/restore rehearsal and verify the staging database can be returned to the pre-migration state when the release strategy requires restoration rather than down-migrations.

### Financial reconciliation

Capture pre- and post-migration totals using the same cut-off timestamp. At minimum compare:

- transaction counts and gross amounts by currency and terminal status;
- merchant ledger balances by currency;
- provider/channel balances where represented;
- held/reserved balances;
- payable/receivable or settlement balances;
- refunds and reversals;
- marketplace split liabilities/subaccount balances;
- settlement batches and unsettled amounts;
- reconciliation exception counts.

Select representative successful, pending, failed, reversed/refunded and disputed transactions and trace each from transaction record through ledger/reconciliation records.

Where provider statements/files are available for the test period, reconcile the CPay totals to those external records. Any delta must be explained and documented. Unexplained financial deltas block signoff.

Finance/reconciliation signoff must name the reviewer, cut-off timestamp, datasets/statements used, aggregate totals, exceptions and evidence reference.

## 4. Security verification

The following require inspection or execution against the intended production environment, not only source review:

- Verify production secrets are stored in the approved secret manager/environment store and default/example credentials are not active.
- Rotate or freshly issue production provider/API credentials according to the launch plan.
- Verify TLS certificate, hostname chain, renewal and HTTPS-only behavior on public endpoints.
- Verify firewall/WAF/network policies and database exposure from outside the approved network.
- Verify public rate limits and abuse controls with a controlled test.
- Verify administrator and privileged user MFA where required by policy.
- Review active privileged accounts and confirm least privilege, current staff ownership and removal of stale/test accounts.
- Verify maker/checker or approval controls on financially sensitive operations where required.
- Verify audit events are generated for privileged and financial actions and are retrievable by the authorized review team.
- Review current dependency/SAST/CodeQL or equivalent findings and explicitly accept/remediate any launch-blocking issue.
- Complete an authorized penetration test or security assessment for the production-exposed scope and record remediation/accepted-risk evidence.
- Perform a database backup restore test and record recovery evidence.
- Conduct an incident-response/tabletop drill for payment/provider outage and suspected credential compromise.

Security approval must be provided by the person or role authorized by the organization to accept production security risk.

## 5. Compliance, privacy and regulatory verification

Formal applicability and approval must be determined by qualified internal/external compliance and legal owners. Source code cannot decide which licenses, registrations or attestations an operating entity requires.

Verify and record, as applicable to the actual operating entity, countries, services and transaction flows:

- merchant KYB and customer KYC operating procedures;
- beneficial-ownership collection/review process;
- sanctions/PEP/adverse-media or configured screening-provider process;
- AML/transaction-monitoring alert review and escalation process;
- suspicious-activity/escalation procedure applicable to the operating model;
- data-protection/privacy notices, lawful-basis/consent flows and data-subject handling;
- retention/deletion schedules and evidence preservation;
- processor/controller and third-party data-processing agreements;
- cross-border data-transfer requirements where applicable;
- PCI DSS scope and required attestation where cardholder data or connected card services make it applicable;
- mobile-money/payment-service provider contractual or scheme approvals;
- Bank of Uganda, Financial Intelligence Authority, communications/data-protection authority, Safaricom/MTN/Airtel scheme/provider, or other regulator approvals **only where the qualified legal/compliance review determines they are applicable**;
- customer/merchant terms, fees, limits, complaints and dispute-handling obligations;
- regulatory reporting ownership and calendar.

Record the applicability decision as well as the approval. `Not applicable` must be supported by the responsible reviewer; absence of evidence is not an applicability decision.

## 6. Physical items to have in hand

These are the tangible or human-controlled items most likely to be missing when a technically green release meets the outside world:

- MTN sandbox/test SIM or test MSISDN/wallet, with required test balance/float.
- Airtel sandbox/test SIM or test MSISDN/wallet, with required test balance/float.
- Airtel OpenAPI sandbox application/account plus any required test MSISDN/wallet.
- Safaricom sandbox/test MSISDN or M-Pesa test credentials/account and any required test wallet/balance.
- A device/phone capable of receiving provider prompts, OTPs or test notifications when the provider flow requires one.
- Hardware MFA/security keys, authenticator enrollment or controlled backup-code custody for privileged production accounts where used.
- Access to the production support telephone/SIM, pager/on-call application and support mailbox/ticket queue.
- Access to provider support portals and escalation contacts for all four providers.
- Access to the secure credential vault/secret manager by the authorized deployment team.
- A dedicated staging database/environment and its separately controlled credentials.
- An approved migration backup/snapshot and tested restore location.
- Provider settlement/reconciliation statements or files for the selected reconciliation period.
- Bank/settlement account statement or portal access when bank-side settlement is part of the reconciliation scope.
- Named operations, finance/reconciliation, security, compliance/privacy and legal/regulatory approvers with authority to sign the relevant gate.
- Any signed provider acceptance/certification letter, scheme approval, license, registration, PCI attestation, penetration-test report or regulatory correspondence determined to be applicable.

## 7. Evidence update rules

A required gate may be changed to `VERIFIED` only when:

1. the test/review described above has actually been performed against the relevant real environment or provider;
2. `verifiedBy` identifies the authorized reviewer;
3. `verifiedAt` is an ISO-8601 timestamp;
4. `evidence` contains one or more stable references to the supporting artifact/ticket/report; and
5. the evidence contains no unresolved blocker that contradicts the approval.

After updating the register, run `python3 ops/readiness/verify_go_live_evidence.py`. The production deployment wrapper also runs this validation and refuses deployment while any required gate remains pending or blocked.
