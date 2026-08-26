# Cito API Documentation

Cito is the platform. CPay is the payments capability within Cito. The API documentation is therefore split by security and product boundary rather than pretending every endpoint belongs to one giant payments contract.

## Authoritative contracts

| Contract | File | Audience | Primary authentication |
| --- | --- | --- | --- |
| CPay Payments API | `Docs/Api/cpay-v2-openapi.yaml` | Server-to-server payment integrations, operations and payment-adjacent admin surfaces | CPay v2 request signature or explicitly documented admin/public auth |
| Cito Platform API | `Docs/Api/cito-platform-v2-openapi.yaml` | Signed-in merchant workspace, platform services, developer control plane and merchant capability APIs | Cito merchant session plus service/environment entitlements |

Supporting documentation remains authoritative for behavior that does not belong cleanly inside OpenAPI schemas:

- `Docs/developer-guide.md` - integration and onboarding guide
- `Docs/Api-v2-signing.md` - CPay v2 canonical signing
- `Docs/Webhook-events.md` - webhook contracts
- `Docs/Error-catalog.md` - error semantics and recovery guidance
- `Docs/Api/AUTO_UPDATE_POLICY.md` - documentation lifecycle and CI policy
- `Docs/CITO_BRAND_AND_MODULE_BOUNDARY.md` - platform/module naming boundary
- `Docs/CITO_SECURITY_ARCHITECTURE.md` - layered platform security architecture

## Platform API groups

The Cito Platform contract currently includes merchant self-service APIs for:

- service catalog and merchant entitlements;
- developer projects, service accounts, credentials, test events, request logs and readiness;
- intelligent payment routing simulation, policies, rules and decisions;
- marketplace subaccounts, split rules, executions, refund allocations and recovery events;
- recurring plans, mandates, subscriptions and charges;
- merchant-workspace refunds and financial timelines;
- virtual accounts and inbound transfer visibility;
- merchant analytics and recommendations;
- embedded/white-label partner onboarding, delegation, branding and commissions;
- integration marketplace installations, mappings, subscriptions and jobs.

## Documentation quality gate

Every pull request that changes API-facing Java code is checked in two ways:

1. it must include an API documentation change; and
2. every path declared by a changed Spring controller must exist in at least one authoritative OpenAPI contract.

Both OpenAPI contracts are parsed, structurally validated and linted. CI then generates separate browsable references for CPay Payments and the wider Cito Platform and stores them as source-commit-specific build artifacts.

This is intentionally stricter than the earlier advisory-only drift scan. A new controller can no longer be merged merely because somebody edited an unrelated documentation file, which was an impressively human loophole while it lasted.

## Change rule

If an implementation changes a public request, response, path, authentication requirement, entitlement requirement, asynchronous state, webhook, error condition or security-sensitive behavior, update the appropriate OpenAPI contract in the same pull request. Generated HTML is output, never the source of truth.
