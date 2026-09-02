# Provider incidents and public status

## Open an incident

1. Confirm provider, country, channel and environment from current telemetry or provider evidence.
2. Choose severity: minor, major or critical. Do not overstate impact.
3. Write separate public and internal context. Public text must contain no merchant identity, credential, transaction PII or speculative root cause.
4. Record the incident through the administrator provider workspace. The action is audited.
5. Link the incident to the established provider escalation channel and certification evidence where relevant.

## Status states

- `OPERATIONAL`: no active published production incidents.
- `DEGRADED`: one or more active minor/major production incidents.
- `MAJOR_OUTAGE`: at least one active critical production incident.

Sandbox incidents do not change public production status. Public status exposes only the incident reference, provider/channel geography, severity, status, public title/message and timestamps. Internal notes never leave the administrator API.

## Resolve and review

Confirm recovery with live probes and provider evidence, record the resolved time, notify affected merchants using their preferences, and complete a post-incident review. Review detection time, acknowledgement time, recovery time, impacted transactions, support demand and reconciliation follow-up.
