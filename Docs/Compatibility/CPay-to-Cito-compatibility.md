# CPay to Cito Compatibility Policy

Cito is the current platform/product name. CPay is the legacy payment-gateway name and remains valid only where changing it would break an existing integration, persisted contract, operational artifact or customer expectation.

## Compatibility classes

| Class | Examples | Policy |
|---|---|---|
| Public API contract | existing CPay v1/v2 URLs, signature names, documented payloads | Preserve until formally versioned/deprecated; never cosmetic-rename in place. |
| Webhook contract | event names, headers, signatures, retry semantics | Preserve or introduce a new version alongside the old one. |
| Persisted schema/keys | table names, migration history, immutable references | Prefer retention; rename only with migration and rollback evidence. |
| Deployment/internal service names | `CPay`, jar names, lock paths | May be migrated gradually after dependency inventory. |
| Java/internal class names | legacy controllers/classes not externally referenced | Rename opportunistically behind tests; avoid large churn-only PRs. |
| Product/documentation text | README, architecture, portal labels | Use Cito now, with explicit “legacy CPay” wording when needed. |

## New naming

- Platform: **Cito**
- Payments product/domain: **Cito Payments**
- Billing product: **Cito Billing & Monetization**
- Merchant billing service: **Cito Billing-as-a-Service (BaaS)**
- Legacy payment contract: **CPay Compatibility API** where a distinction is needed
- Java namespace: `net.citotech.cito`

## Deprecation requirements

A public CPay compatibility surface may be removed only when all of the following exist:

1. a replacement Cito contract;
2. migration documentation and examples;
3. telemetry showing remaining legacy usage;
4. a published deprecation date/window;
5. customer/merchant notification where applicable;
6. contract tests for both old and new surfaces during the overlap period;
7. rollback/restore procedure;
8. release approval.

## Prohibited cleanup

Do not rename or delete a legacy identifier merely to make a repository search look cleaner. Compatibility has higher priority than cosmetic consistency.

## CI direction

Compatibility tests should eventually maintain a machine-readable manifest of retained legacy routes/headers/events. Breaking changes require an intentional version transition rather than an unnoticed diff.
