# Merchant activation and support runbook

## Activation triage

1. Open the merchant lifecycle and confirm merchant ID, lifecycle reference and environment.
2. Read the current step, owner, next action, blocker and due date. Do not infer completion from a different workflow table.
3. Inspect the ordered step evidence. Route KYB to Compliance, risk review to Risk, commercial approval to Sales, provider certification/go-live to Operations, and settlement configuration to Finance.
4. Update evidence through the owning workflow. Never mark a required step complete merely to bypass activation.
5. If production is already live, use a suspension or controlled rollout workflow; do not roll the lifecycle backwards without an audited operational decision.

## Support case handling

Every case must have a reference, merchant scope where applicable, category, severity, status, opened-by actor, description and SLA timestamps. Link transaction and provider references when known.

| Severity | First response target | Resolution target | Typical use |
|---|---:|---:|---|
| Critical | 1 hour | 4 hours | Active loss, broad payment outage, security incident |
| High | 4 hours | 24 hours | Material degradation or blocked financial operation |
| Medium/Low | 24 hours | 72 hours | Standard account, integration or reporting help |

Before recommending a retry, open transaction support context. Confirm finality, state transitions, reconciliation evidence and idempotency. The default is **do not retry** until the evidence supports it.

## Merchant 360 minimum context

- legal/operating identity and account state;
- activation lifecycle, blocker and next action;
- service entitlements by environment;
- live transaction summary with source timestamp;
- open cases and linked transaction references;
- relevant provider incidents;
- no unmasked payer identifier or credential material.

If live data returns `503`, report the retrieval failure and investigate availability. Do not replace it with cached-looking or example values.
