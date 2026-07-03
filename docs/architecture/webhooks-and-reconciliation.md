# Webhook Retry and Reconciliation Modules

## Webhook retry/dead-letter service

New tables and services:

- `webhook_deliveries`
- `HookTaskRepository`
- `HookTaskService`
- `HookTaskController`

The service retries callbacks using a staged schedule:

1. 1 minute
2. 5 minutes
3. 15 minutes
4. 1 hour
5. dead-letter after max attempts

The admin endpoint is:

```text
POST /api/v2/admin/webhooks/process-due?limit=50
```

## Reconciliation module

New tables and services:

- `reconciliation_imports`
- `reconciliation_records`
- `ReconciliationRepository`
- `ReconService`
- `ReconController`

Initial capabilities:

- list unmatched records
- auto-match by merchant reference
- approve manual match by record and transaction ID

Endpoints:

```text
POST /api/v2/admin/reconciliation/auto-match
GET /api/v2/admin/reconciliation/unmatched?limit=100
POST /api/v2/admin/reconciliation/manual-match
```

## Next production step

Add a provider statement upload/import endpoint and maker-checker approval before financial adjustments are posted. Because letting one click alter money records without review is how audit committees become theatrical.
