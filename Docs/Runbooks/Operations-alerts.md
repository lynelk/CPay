# Operations Alert Runbook

Use this runbook for alerts raised by the admin dashboard, scheduled checks, callback processing, and operating-control summaries.

## Triage

1. Record the alert type, severity, first-seen time, and owner.
2. Capture the dashboard count and one example reference.
3. Check whether the alert is isolated to one merchant, one provider, or all traffic.
4. Check recent deployment, migration, provider, and credential changes.
5. Update the incident note with the next review time.

## Float below threshold

Signals:

- float runway below configured threshold
- low-balance email from `FloatAlertScheduler`
- failed payout reason indicates insufficient float

Actions:

1. Confirm current balance from provider and internal dashboard.
2. Stop broad payout retries if float is genuinely low.
3. Ask finance or operations to top up the affected provider/channel.
4. Resume retries only after the top-up is confirmed.
5. Record the top-up evidence and close the alert.

## Callback parked

Signals:

- `callback_tasks.task_status='PARKED'`
- Prometheus alert `CPayCallbackParkedBacklog`
- merchant reports missing status updates
- callback retry queue stops draining

Actions:

1. Confirm merchant callback URL reachability.
2. Confirm callback signing secret and endpoint allowlists.
3. Review the latest parked task message.
4. Requeue by merchant only after the endpoint issue is fixed.
5. Verify `cpay_callback_delivery_total{status="DONE"}` increases after requeue.

## Retry queue growth

Signals:

- retry queue count rising for more than one scheduler interval
- Prometheus alert `CPayCallbackPendingBacklogHigh`
- callbacks or payments remaining in retry

Actions:

1. Check provider status and network reachability.
2. Identify whether retries are from one provider, one merchant, or all providers.
3. Avoid manual bulk retry while provider responses are unstable.
4. Confirm idempotency behavior before replaying payouts.
5. Escalate to SEV1 when customer money movement is broadly blocked.

## Merchant webhook delivery failures

Signals:

- Prometheus alert `CPayWebhookFailedBacklog`
- `merchant_webhook_deliveries.delivery_status='FAILED'`
- merchant reports no callback even though CPay transaction status is terminal

Actions:

1. Open the merchant webhook delivery log and identify the failing endpoint.
2. Confirm the merchant endpoint is reachable and accepts CPay's signed callback payload.
3. Ask the merchant to fix authentication, allowlisting, certificate, or response-code issues.
4. Replay only the failed delivery after the endpoint issue is fixed.
5. Verify the failed backlog gauge returns to zero and the delivery is marked terminal success.

## Reconciliation exceptions

Signals:

- unmatched statement rows
- settlement exception count increases
- daily close blocked

Actions:

1. Validate the provider statement file and parser type.
2. Compare provider reference, merchant reference, amount, and currency.
3. Assign an exception category before finance review.
4. Do not close the day until finance signs off on material variance.

## Closure

Close the alert only after:

- root cause is recorded
- owner confirms recovery
- affected task or transaction count is back to expected levels
- follow-up issue or ADR exists for structural changes
