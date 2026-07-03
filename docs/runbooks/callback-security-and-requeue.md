# Callback Security and Requeue Runbook

## Purpose

This runbook explains how signed merchant callbacks should be operated, verified, rotated, and manually requeued.

## Callback signing

Each callback is signed with an HMAC-SHA256 signature. The callback request includes:

- `X-CPay-Signature`
- `X-CPay-Nonce`
- `X-CPay-Timestamp`

The canonical payload is built from task id, merchant id, merchant reference, timestamp, nonce, and request body. Merchants should reject callbacks with stale timestamps, reused nonces, or invalid signatures.

## Per-merchant secrets

Use the admin callback endpoint to rotate a merchant secret before enabling signed callbacks for that merchant. Store the returned secret securely and share it only through an approved channel.

Recommended process:

1. Rotate the merchant callback secret in sandbox.
2. Share the secret with the merchant technical contact.
3. Confirm merchant-side signature verification.
4. Run a sandbox callback delivery.
5. Promote to production only after a successful callback verification test.

## Manual requeue

Parked callbacks can be manually requeued by task or by merchant. Requeue only after confirming that the merchant callback URL is reachable and the previous failure cause has been addressed.

## Alerts

Create an operations alert when:

- parked callbacks exceed the threshold
- callbacks fail repeatedly for one merchant
- callback verification fails on the merchant side
- callback queue depth continues growing

## Rollback

If signed callbacks fail after rollout, rotate the merchant secret, re-run sandbox delivery, and requeue parked tasks only after verification succeeds.
