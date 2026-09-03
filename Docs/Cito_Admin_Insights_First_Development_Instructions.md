# Cito Admin Portal: Insights-First Development Instructions

## 1. Purpose

This document defines the canonical admin-portal experience in which **Insights is the first page displayed after successful administrator authentication**.

The objective is to let an authorised Cito operator understand the state of the business and platform within seconds, without opening several operational modules first. The page must answer five questions in this order:

1. **What needs attention?**
2. **What happened today?**
3. **Which services are ready, operating, degraded, or not configured?**
4. **What happened most recently?**
5. **How is performance trending?**

The Insights page is an operational decision surface, not a decorative analytics dashboard. Every number, status, warning, trend, and activity row must come from a real authorised backend source. The frontend must never manufacture production-looking values to make an empty dashboard appear busy.

---

## 2. Canonical route and login behaviour

### 2.1 Canonical route

The canonical administrator landing route is:

```text
/bo/admin/insights
```

### 2.2 Authentication redirects

After a successful admin login, the user must end up at `/bo/admin/insights`.

Existing login code that still navigates to a legacy `/dashboard` route may remain temporarily for compatibility, provided the router immediately and deterministically redirects that route to `/bo/admin/insights`.

### 2.3 Backward compatibility

The following historical routes must continue to work and converge on Insights:

```text
/bo/admin/home
/bo/admin/dashboard
/admin/*
/dashboard/*
```

Do not break bookmarks, old emails, operational runbooks, or integrations merely to make route naming tidier. Humans have already created enough accidental dependencies without our help.

### 2.4 Shell behaviour

When an already authenticated user enters `/bo/admin`, `/bo/admin/`, `/bo/admin/home`, or `/bo/admin/dashboard`, the authenticated admin shell should replace the URL with `/bo/admin/insights`.

---

## 3. Navigation information architecture

The first item in the administrator navigation is **Insights**.

The admin navigation should remain capability-oriented and should not expose implementation detail as the primary organising principle. The current top-level operational structure is:

- Insights
- Merchants & Accounts
- Money Operations
- Treasury
- Risk & Compliance
- Providers & Integrations
- Platform
- Administration
- Engineering / Internal

Engineering/internal control planes must remain visibly separated from routine business operations and must continue to respect role-based access controls.

The label **Home** is deprecated for the admin operational landing experience. Legacy `home` and `dashboard` menu keys may remain as aliases internally until dependent code is removed safely.

---

## 4. Insights page composition

The page must render the following sections in this order.

### 4.1 Needs Attention

Purpose: surface exceptions before summary statistics.

Inputs should include, where available:

- dashboard/API refresh failures;
- failed transactions;
- provider/channel states that are not ready or operating;
- exhausted or approaching production limits;
- unresolved reconciliation exceptions;
- provider incidents;
- approval queues;
- compliance/KYB items requiring review;
- treasury/float warnings.

For the initial implementation, only conditions backed by existing authorised APIs should be displayed. Missing integrations are not an excuse to invent alerts.

Each actionable item should contain:

- a concise title;
- a plain-language explanation;
- severity/state;
- a direct route to the module where the operator can act.

If no issue is returned, show a truthful positive state such as:

```text
No active issues detected
Available insight sources are not reporting an exception that needs intervention.
```

Do not display a fabricated green score.

### 4.2 Today's Business

Purpose: provide immediate commercial and operational scale.

Initial live metrics:

- collections value;
- disbursement value;
- transaction count;
- success rate, calculated only from recorded transaction and failure totals;
- merchant count visible to the signed-in account.

Rules:

- respect tenant and role scoping from the backend;
- format monetary values consistently;
- do not substitute guessed values for null, empty, or zero data;
- distinguish **No activity** from an API failure;
- never calculate a percentage if the denominator is zero.

### 4.3 Services

Purpose: show whether configured platform capabilities can actually be used.

Service cards should be generated from the authorised channel/service summary returned by the backend.

Map technical states into operator-friendly states:

| Technical condition | Operator state |
|---|---|
| `ACTIVE` | Operating |
| `SANDBOX_TESTED` | Ready |
| `SUBMITTED_FOR_APPROVAL` | Ready |
| `DEGRADED` | Degraded |
| `FAILED`, `DISABLED`, `SUSPENDED` | Needs Attention |
| absent/unknown/not configured | Setup Required |

The underlying technical state should still be visible as supporting detail.

Environment must be explicit. Production and Sandbox are not interchangeable decorations. A channel configured in Sandbox must not be presented as live production capability.

The Cito-managed/default payment rail, including CPay where returned by the backend service catalogue, should appear using the same state model as merchant-specific MTN, Airtel, Yo! Payments, FlexiPay, or other configured rails.

### 4.4 Recent Activity

Purpose: let an operator understand what just happened without opening the full transaction workspace.

The initial implementation uses the existing authorised administrator transaction query and shows a small recent list with:

- time;
- merchant or reference;
- transaction type;
- status.

The full transaction workspace remains the system of record for investigation.

RBAC requirements:

- only query the transaction feed when the signed-in role has `ACCESS_TRANSACTION_LOG`;
- if access is not granted, show a restricted-state message rather than generating an access-denied storm in the background;
- do not leak merchant or transaction data through summary endpoints that bypass the normal permission model.

As Cito gains broader non-payment activity feeds, this section can aggregate immutable operational events from payments, communications, identity/risk, vending, integrations, support, and configuration audit sources. Any such extension must retain chronological ordering, source attribution, tenant scoping, and authorisation.

### 4.5 Performance

Purpose: provide trend context after the operator has understood current priorities and recent events.

Initial chart sources are the existing live admin dashboard APIs for:

- collections versus disbursements;
- transaction volume.

Rules:

- show a clear empty state when a chart has no live series;
- do not draw placeholder series;
- loading, empty, permission-denied, and error states must be distinguishable;
- chart refresh must participate in the shared admin refresh control;
- network-bound chart requests must use the existing query/cache framework rather than introducing ad-hoc fetch loops.

---

## 5. Data-source policy

### 5.1 Existing sources used by the first implementation

The Insights page should reuse the existing shared TanStack Query hooks:

```text
usePortalDashboardSummary
useAdminDashboardCharts
useAdminTransactions
```

Do not create duplicate API wrappers for these endpoints.

### 5.2 Truthfulness rule

The frontend must not contain hard-coded production-looking examples such as:

- provider runway values;
- fake success percentages;
- fake incidents;
- fake balances;
- fake merchants;
- fake settlement risk;
- fake SMS volumes.

Static content is acceptable only when it is clearly explanatory copy, configuration guidance, or a labelled example outside the live operational view.

### 5.3 Error handling

A query failure is operational information and should be surfaced in **Needs Attention**.

Session-expiry errors must invoke the existing parent-shell session-expiry behaviour.

A single failed source must not blank the entire page. Other successful sections should continue to render.

---

## 6. Security and permissions

Insights must not become a convenient loophole around existing controls.

Requirements:

1. Preserve cookie/session authentication already used by the admin portal.
2. Reuse existing RBAC checks before querying sensitive detail feeds.
3. Do not expose credentials, secrets, API keys, raw authentication headers, or provider tokens.
4. Do not make live payment test controls available from Insights itself. Those controls remain in the appropriate provider/payment operations surface with existing maker-checker and permission protections.
5. Production and Sandbox state must remain visibly distinct.
6. Direct links from Insights must still be protected by destination-module permissions.
7. Any future cross-tenant aggregate view must be backed by explicit platform-admin authorisation and server-side scope enforcement.

---

## 7. UX rules

### 7.1 Decision-first hierarchy

The page order is deliberate:

```text
Needs Attention
→ Today's Business
→ Services
→ Recent Activity
→ Performance
```

Do not move charts above urgent operational information merely because charts look impressive in screenshots.

### 7.2 Empty states

Use actionable state language:

- Setup Required
- Ready
- Operating
- Degraded
- Needs Attention
- No activity
- Restricted by role
- Data source unavailable

Avoid vague labels such as `Awaiting data` when the system can determine a more useful state.

### 7.3 Density

The page should support a quick scan on laptop and desktop layouts while remaining responsive on smaller screens. Prefer concise cards and tables over long explanatory text.

### 7.4 Navigation from insight to action

Every warning or important summary that has a clear operational destination should link to that module. Insights is the index of operational reality, not a dead-end report.

---

## 8. Implementation structure

### 8.1 Component

Canonical admin overview component:

```text
clientside/src/components/modules/ModuleInsights.jsx
```

Responsibilities:

- request the existing summary, charts, and permitted recent transactions;
- derive display-only states from returned values;
- render the five canonical sections;
- preserve refresh behaviour;
- invoke session-expiry behaviour;
- route operators to action modules.

### 8.2 Admin shell

Update:

```text
clientside/src/components/Layout.jsx
```

Requirements:

- register `insights` as the canonical menu key;
- map `home` and `dashboard` to `insights` for compatibility;
- make `/bo/admin/insights` the canonical route;
- render `ModuleInsights` for `insights`, legacy `home`, and legacy `dashboard` keys;
- replace legacy authenticated shell URLs with `/bo/admin/insights`.

### 8.3 Main navigation

Update:

```text
clientside/src/components/MainMenu.jsx
```

Requirements:

- first nav item is `Insights`;
- remove `Home` as the displayed admin label;
- preserve existing downstream menu identifiers unless deliberately migrated.

### 8.4 Router compatibility

Update:

```text
clientside/src/Routers.tsx
```

Requirements:

- `/dashboard/*` redirects to `/bo/admin/insights`;
- legacy `/admin/*` fallbacks redirect to `/bo/admin/insights`;
- existing direct operational routes such as provider treasury and production maturity remain intact.

---

## 9. Testing requirements

The implementation must include regression tests covering:

1. legacy dashboard routes converge on `/bo/admin/insights`;
2. Insights is the first administrator navigation item;
3. the five major page sections appear in the required order;
4. the implementation does not seed fake production-looking metrics;
5. frontend build succeeds;
6. typecheck succeeds;
7. lint succeeds;
8. full frontend test suite succeeds.

Before merge, also run the repository's normal backend/CI checks required by branch protection. A frontend-only feature is not permission to casually break the rest of a monorepo.

---

## 10. Deployment requirements

Production deployment sequence:

1. Create a feature branch from the exact current `main` SHA.
2. Apply code, tests, and this development specification.
3. Open a pull request to `main`.
4. Wait for required CI checks.
5. Re-fetch the PR head SHA immediately before merge.
6. Merge using expected-head protection.
7. Confirm the merge commit is now the `main` head.
8. Verify Railway production services are sourced from `lynelk/Cito` `main`.
9. Trigger or confirm deployment of both backend and frontend services as appropriate.
10. Confirm Railway reports successful deployments.
11. Review build and runtime logs for errors.
12. Verify frontend production routing reaches `/bo/admin/insights` after authentication.
13. Verify backend health/readiness endpoints remain healthy.

Do not report deployment complete until the Railway deployment objects and runtime evidence agree. A green GitHub merge button is not, despite decades of optimistic behaviour, the same thing as a healthy production system.

---

## 11. Acceptance criteria

The feature is complete when all of the following are true:

- An administrator signs in and lands on Insights.
- `/bo/admin/insights` is the canonical authenticated landing URL.
- Old admin dashboard URLs continue to work through redirects/aliases.
- Insights is the first visible admin navigation item.
- The page presents Needs Attention first.
- Today's Business uses live summary values only.
- Services shows real channel/service configuration states.
- Recent Activity uses an authorised real transaction feed or a truthful permission/empty state.
- Performance uses real chart sources only.
- No fake operational metrics are seeded in the Insights component.
- Refresh continues to work using the shared query model.
- Session expiration is handled by the existing shell behaviour.
- RBAC remains enforced.
- Frontend CI passes.
- Required repository CI passes.
- The merged `main` commit is successfully deployed to Railway production.
- Production smoke checks confirm the new landing behaviour without regressing existing payment, provider, treasury, merchant, or engineering routes.
