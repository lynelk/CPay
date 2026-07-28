# Callback Security and Requeue Runbook

## Purpose

This runbook explains how CPay callback delivery should be monitored, verified, and reprocessed safely.

## Callback security controls

CPay callback delivery includes:

- signed callback messages
- timestamp headers
- nonce headers
- merchant-level callback signing values where configured
- retry and parked-task handling
- task claims before delivery when workers process callbacks

Merchant systems should verify callback messages before acting on them.

## Normal callback flow

1. A transaction reaches a callback-worthy state.
2. CPay creates a callback task.
3. A callback worker claims the task.
4. CPay signs the callback message.
5. CPay sends the callback to the merchant URL.
6. A successful response marks the task as done.
7. A failed response schedules retry or parks the task after retry limit.

## Requeue rules

Only requeue a parked callback when:

- the merchant callback URL is reachable
- the merchant confirms it can receive the message
- callback signing setup is confirmed
- the original failure reason has been reviewed

Do not repeatedly requeue a callback without correcting the underlying issue. That is not operations. That is poking the same bruise and calling it monitoring.

## Worker-claim checks

When callbacks appear stuck:

1. Check open callback tasks.
2. Check active callback task claims.
3. Confirm workers are running.
4. Confirm task claims are released after processing.
5. Confirm failed tasks move to retry or parked state.

## Merchant receiver checks

Ask the merchant to confirm:

- the callback URL is publicly reachable
- the URL accepts POST requests
- it validates timestamp and nonce values
- it validates callback signatures
- it returns a 2xx response after successful processing

## Incident evidence

Record:

- merchant account number
- transaction reference
- callback task id
- attempt count
- last failure message
- whether task claim existed
- requeue time and operator
- merchant confirmation
