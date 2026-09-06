# Cito launch revalidation — 6 September 2026 (Africa/Kampala)

## Decision and scope

**Software deployment and general availability are separate decisions. Do not declare unrestricted merchant launch from successful builds or HTTP health alone.**

This revalidation reviews the production repository revision `7de0ead9cd286f4c19bc7a8b8533286a94591f13`, selected release/security/deployment controls, GitHub Actions results, Railway production configuration and runtime logs. It is not a line-by-line audit of every source file, an independent penetration test, authenticated merchant UAT or provider certification.

The 5 September **Connect Once. Operate Everything.** blueprint recovered from the document library is the requirements reference. No new attachment was available in the review conversation. Its acceptance criteria must not be treated as completed merely because an implementation class or adapter exists.

The prior decision in `Cito-launch-readiness-review-2026-09-05.md` permits a controlled launch. This revalidation does not waive that decision's manual gates. Internal evaluation and a deployment of verified code may proceed; real-money pilot activation is conditional on the specific merchant/provider approvals, reconciliation and recovery evidence below. Broad general availability remains **NO-GO pending evidence**.

## Evidence observed before this revalidation change

Repository:

- Default `main`: `1cf4f70826bedacae00cf6355f1d06bd2bcaa2c4`.
- `production`: `7de0ead9cd286f4c19bc7a8b8533286a94591f13`.
- PR #140 already promoted the main changes to production; PR #141 already merged the prior readiness report and payout guard formatting.
- No open pull requests existed at the beginning of this review. Those previous merges are not new actions performed by this revalidation.
- Main/production have divergent ancestry following earlier promotions. Compare actual content before promoting either branch; do not assume ancestry counts prove missing functionality.

GitHub Actions:

- Production-head ISO Governance and Financial Messaging run `33991221543`: full Maven verify, financial messaging boundary tests and governance register jobs all succeeded.
- Production-head Billing Convergence Gates run `33991221577`: succeeded.
- PR #141 head `498b036adae1756d6a5f02f8e09b55f9ab6f53c5`: ISO/full Maven, Billing Convergence, API Documentation and Docker Build (PR) workflows succeeded.
- Cloudflare Workers checks named `cito` and `cpay` failed on the production head. Their relevance to current traffic must be established and the integrations repaired or deliberately retired by their owner. They are not silently classified as passed or optional.
- The separate `CI` workflow specifies a clean MySQL 8.4 migration check, whereas this Railway database runs MySQL 9.4. A successful runtime migration validation is not equivalent to a complete clean-install/upgrade compatibility test matrix.

Railway project `f489b3d0-19d0-4ead-8dbb-c6297ee7161a`, environment `eabd4925-5a7c-4701-9038-42bfba3ed87e`:

| Service | Observed deployment | Status | Release/source |
|---|---|---|---|
| Cito Backend | `5abfc593-56af-4fc0-b207-4b87f20a9ef5` | SUCCESS | Production `7de0ead9cd286f4c19bc7a8b8533286a94591f13` |
| Cito Frontend | `814f006b-0fb6-456d-b1d1-584e03475b2e` | SUCCESS | Production `6a95f789fb17da1d889652521aedfdea60a1c1d1` |
| MySQL | `c7958d59-5d5b-403b-8a18-e9916faa97b9` | SUCCESS | `mysql:9.4` |

The frontend's newer `7de0ead9` attempt was SKIPPED under path-based deployment rules; its active successful deployment remained the previous unchanged frontend. A skipped attempt is not evidence that a new artifact was deployed.

Runtime/configuration observations:

- Canonical frontend and backend both use the production branch and each have one Amsterdam-region replica.
- Frontend domains: `cito.coresynergi.es` and `cito-frontend-production.up.railway.app`, port 8080.
- Backend and database have private endpoints; no public backend/database domain was listed in the inspected configuration.
- Backend startup used the production Spring profile and established a MySQL connection over the private hostname with `sslMode=REQUIRED`.
- Flyway validated 109 migrations, reported schema version 115 and no pending migration.
- Frontend `/readyz` and `/api/status/health` proxy to backend `/status/health`; `/healthz` is frontend liveness only.
- The inspected backend log window contained recurring `Checking status for 0 TXs` at ERROR severity. This is noisy routine scheduler logging, not by itself proof of a failed payment or database outage. Normalize its source-level severity in a separately tested change.
- Legacy services `Cito` and `Cito API` retain failed deployments, disabled watch paths, restart policy NEVER, sleeping enabled and no public domain in inspected configuration. They were not redeployed or deleted by this review.
- MySQL is single-node with a persistent volume. HA template metadata is not a deployed HA cluster.
- No current backup object, successful restore drill, signed RPO/RTO acceptance, production provider transaction evidence or authenticated portal UAT was independently verified in this session.

## Release verification improvement

The existing `Cito Exact-Head Release Verification` workflow is extended, rather than replaced, with a read-only public production health job for production changes.

The job checks only these fixed endpoints on the two canonical frontend hostnames:

- `/healthz` — require HTTP 200 and the expected liveness response.
- `/readyz` — require HTTP 200 and a healthy non-HTML payload from the backend proxy.
- `/api/status/health` — require HTTP 200 and a healthy non-HTML payload from the public API proxy.

It verifies TLS, refuses redirects, uses bounded timeouts/retries, rejects SPA HTML fallbacks and explicit unhealthy payloads, and retains sanitized timestamped results. It sends no credentials, performs no collections/payouts, accesses no customer records and changes no production data. Existing audit, lint, typecheck, unit/component, build and browser-matrix steps remain unchanged.

The probe checks the **currently running** environment, including when executed from a PR. Its workflow SHA is not a runtime SHA attestation and it does not establish that an unmerged PR has been deployed. Match release source to Railway deployment metadata separately. Health evidence is point-in-time, not a substitute for continuous monitoring or end-to-end UAT.

## Mandatory launch gates

| Gate | Evidence needed | Responsible function | Status in this review |
|---|---|---|---|
| Production release | Required tests passed; canonical deployments terminal SUCCESS; deploy SHA recorded; health smoke passed | Engineering / operations | Evaluate using this PR's Actions and final Railway deployment records |
| Merchant/admin UAT | Authenticated login, settings, role/tenant isolation, onboarding, balances and truthful failure states | Product / QA / security | Not independently verified |
| Real-money provider activation | Valid credentials and approval; callback verification; controlled collection/payout/reversal evidence; reconciliation | Payments / provider owner | Per-provider external gate; no live test performed here |
| Finance operations | Ledger balancing, matched provider statement, close/settlement and approved variance handling | Finance | Signoff required |
| Recovery | Current encrypted backup with retained keys; isolated restore drill; measured and accepted RPO/RTO | Operations | Not independently verified |
| Security/privacy | External security review; access/MFA review; secret handling and consent evidence | Security / compliance | Signoff required |
| Support and incidents | Named rota, alerts, escalation contacts, incident/rollback drills | Operations / customer care | Signoff required |
| Capacity/resilience | Representative load evidence; restore/failover evidence appropriate to advertised availability | Engineering / operations | Single-node database; no HA claim permitted |
| Legal/commercial launch | Provider/jurisdiction contracts and approvals; only approved services advertised as live | Compliance / commercial | Signoff required |
| Deployment hygiene | Resolve Cloudflare failures and legacy ownership; explicit main/production promotion policy | Engineering / platform owner | Open |

## Release handling

Do not disable checks, reduce security thresholds, edit already-applied migrations, rotate production secrets without a migration plan, enable uncertified providers, delete persistent services or bypass maker-checker to produce a green dashboard.

This revalidation change modifies release verification and documentation only. It does not change application APIs, database migrations, payment limits, credentials, financial records or service entitlements. Review and merge through a pull request only after applicable tests pass; redeploy the canonical services individually and confirm terminal status. Record final deployment IDs in the PR discussion and the release handover, rather than claiming success from a queued build.

**Conclusion:** the observed runtime supports continued internal operation and a verified production deployment. Real-money controlled launch remains conditional on explicit merchant/provider/finance/recovery approval. Full blueprint completion and unrestricted general availability are not certified by this review.
