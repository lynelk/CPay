# Backup, Restore, and DR Drill Runbook

Use this runbook for scheduled backup checks, restore drills, and disaster-recovery readiness.

## Targets

| Area | Target |
|---|---|
| Backup cadence | Daily logical MySQL dump plus provider-managed storage snapshots where available |
| RPO | 24 hours until production traffic requires a tighter target |
| RTO | 4 hours for database restore into a verified application environment |
| Evidence | Store backup job logs, restore logs, checksum results, and approver signoff |

## Daily Backup Check

1. Confirm the scheduled database backup job completed successfully.
2. Confirm the backup artifact is encrypted at rest and stored outside the application host.
3. Record database name, backup timestamp, artifact size, checksum, and storage location.
4. Confirm the latest schema migration version is included in the backup metadata.
5. Open an operations alert if the backup is missing, incomplete, unencrypted, or older than the RPO.

## Monthly Restore Drill

1. Provision an isolated restore database that cannot send live payments, SMS, callbacks, or emails.
2. Restore the latest backup into the isolated database.
3. Run Flyway validation against the restored schema.
4. Start the backend with sandbox gateway settings only.
5. Verify these read paths: admin login, merchant lookup, merchant statement export, ledger trial balance, and dashboard balance read-model.
6. Verify money-moving schedulers and provider callbacks are disabled in the drill environment.
7. Record restore start time, restore finish time, validation commands, and observed RTO.

## Emergency Restore

1. Declare the incident and assign one restore owner.
2. Freeze deployments and scheduled jobs that could mutate the damaged database.
3. Select the newest known-good backup that satisfies the incident's recovery point.
4. Restore into a clean database.
5. Run migration validation and application smoke checks.
6. Re-enable application traffic only after finance and operations sign off on ledger, statement, and settlement consistency.
7. Keep the damaged database read-only for investigation unless legal or compliance direction says otherwise.

## Post-Drill Review

- Compare actual RPO/RTO with targets.
- Record failed steps, missing access, slow restores, or unclear ownership.
- Update this runbook and `Docs/Runbooks/Production-incident-response.md` when the drill uncovers a gap.
