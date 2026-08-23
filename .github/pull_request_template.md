## Summary

Describe the change and the user or operational problem it solves.

## Verification

- [ ] Backend build/tests pass for affected code.
- [ ] Frontend typecheck/tests/build pass for affected UI.
- [ ] Flyway versions are unique and migrations are additive/reversible where applicable.
- [ ] Security, tenant isolation, money movement and audit implications were reviewed.

## Sandbox parity

Every production capability should have a sandbox test path unless an explicit restriction is documented.

- [ ] New non-admin `/api/v2` endpoints appear in the runtime sandbox capability catalog.
- [ ] New provider-facing or money-moving behavior uses a SANDBOX adapter/simulator and cannot reach production providers or balances when `X-CPay-Environment: SANDBOX` is selected.
- [ ] New feature flags are visible through the sandbox catalog and have deterministic test data/scenarios where useful.
- [ ] New webhook/callback behavior has a sandbox test/replay path.
- [ ] New KYC/KYB/identity behavior has synthetic personas or documented sandbox-provider fixtures.
- [ ] Sandbox reset/snapshot behavior covers any new sandbox-owned state.
- [ ] Certification/readiness checks were extended when the feature is a go-live dependency.

If any sandbox item does not apply, explain why rather than silently leaving it unchecked.

## Documentation

- [ ] OpenAPI annotations/schemas/examples reflect new or changed public APIs.
- [ ] Developer guides/runbooks are updated for new integration behavior.
- [ ] Swagger/OpenAPI remains usable on the sandbox deployment.
- [ ] Breaking changes include migration/versioning guidance.

## Deployment

- [ ] The change is safe to advance `main` → `sandbox` after CI.
- [ ] Sandbox verification evidence is recorded before manual `sandbox` → `production` promotion.
