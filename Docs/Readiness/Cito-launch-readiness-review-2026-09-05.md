# Cito Launch Readiness Review — 2026-09-05

**Repository:** `lynelk/Cito`  
**Railway project:** `f489b3d0-19d0-4ead-8dbb-c6297ee7161a`  
**Reviewed production revision:** `6a95f789fb17da1d889652521aedfdea60a1c1d1`  
**Review role:** CTO release readiness assessment  
**Decision:** **READY FOR CONTROLLED PRODUCTION LAUNCH; NOT YET APPROVED FOR UNRESTRICTED BROAD-MERCHANT GA**

## 1. Executive decision

Cito is sufficiently mature to run in production for a controlled launch with approved merchants and explicitly activated services. The current repository and Railway production deployment demonstrate substantial implementation of the Cito control plane, CPay payments, communications foundations, merchant/admin portals, platform entitlements, provider operations, billing/finance controls, tenant controls, operational Insights, CI/CD gates, production migrations and deployment health checks.

The platform must **not** be represented as universally production-certified across every advertised provider or service until the manual/external evidence gates below are completed. Software readiness and provider/regulatory certification are deliberately separate.

## 2. Evidence reviewed

- Repository production branch and current release commit.
- Current Cito platform implementation, including entitlement and feature-access services, communication domain, CPay payment operations, compliance/KYB/KYC, billing/finance controls, provider integration foundations and admin/merchant experience.
- `Docs/Readiness/Market-readiness-gates.md`.
- Current Railway production service topology and deployment logs.
- Current production Flyway state and MySQL connectivity.
- Current launch blueprint: **Cito — Connect Once. Operate Everything. Comprehensive Development Instructions and Implementation Blueprint** dated 2026-09-05.

## 3. Repository readiness

### Green

- Production promotion PR #140 has been merged.
- Production includes the payout funding guard, frontend readiness correction, browser semantic contract fix, governance evidence refresh and validated CI dependency upgrades.
- Platform entitlement and feature-access services are present.
- Communications has a structured domain with activation, campaign, configuration, credentials, delivery, provider, preference and outbox foundations.
- Existing CPay compatibility surfaces are preserved while Cito remains the platform identity.
- Production migrations validate successfully and the runtime reports schema version 115 with no migration pending.
- The production commit has successful Railway deployment checks for the canonical Cito Backend and Cito Frontend services.
- No open pull requests remain at the time of this review.

### Amber

- The repository still contains legacy/monolithic implementation areas alongside newer modular domain code. This is acceptable for launch but should continue to be converged rather than duplicated.
- Some legacy operational logging uses error severity for normal scheduler activity. This does not make the system unhealthy, but it creates noisy monitoring and should be normalised in a later hardening change.
- `main` and `production` have diverged Git ancestry because production promotion uses release-history preservation/squash-style promotion. Content promotion is present, but branch-history housekeeping should remain explicit so engineers do not mistake ancestry divergence for missing production code.

## 4. Railway production readiness

Canonical production services:

- `Cito Backend` — production branch — deployment `b104c987-1306-4542-8233-6fe64433de73` — **SUCCESS**.
- `Cito Frontend` — production branch — deployment `814f006b-0fb6-456d-b1d1-584e03475b2e` — **SUCCESS**.
- `MySQL` — **SUCCESS**.

Backend startup evidence confirms:

- Spring production profile active.
- MySQL 9.4 connectivity through Railway private networking.
- 109 migrations validated.
- Current production schema version 115.
- No migration pending.
- `/status/health` deployment healthcheck passed.

The MySQL restart observed during this review temporarily invalidated existing JDBC connections, and scheduled workers emitted transient connection errors. Subsequent logs show normal scheduled execution without continued JDBC transaction failures, demonstrating application recovery after the database returned. This is useful resilience evidence, but it is **not equivalent to tested database high availability/failover**.

Two legacy services, `Cito` and `Cito API`, retain failed historical deployments. Their configurations are deliberately pointed at disabled watch paths and/or sleeping behaviour and are not the canonical production runtime. They should be removed from Railway when service-deletion tooling/administrative cleanup is available so the project dashboard reflects only the supported topology.

## 5. Launch blockers for unrestricted GA

The following are release-governance blockers for broad merchant onboarding, not blockers to deploying the software into production for a controlled launch:

1. External security review/signoff is still a manual readiness requirement.
2. Provider production certification must be evidenced per live provider. Adapter/code existence does not constitute provider approval.
3. Real provider sandbox/production evidence and statement/reconciliation evidence remain required where not already signed off operationally.
4. Finance must complete/approve the daily-close and settlement-variance operational evidence required by the market-readiness gate.
5. Merchant callback receiver verification and reachability must be completed for each production merchant integration.
6. Support rota, monitoring/escalation channels and provider emergency contact ownership must be formally signed off.
7. Database HA must not be claimed until failover is implemented and tested. Current backup/recovery and single MySQL availability are adequate for a controlled launch only within the accepted RPO/RTO.
8. Regulatory/provider integrations must be marketed only for jurisdictions and services that have actual approvals, contracts and credentials.

## 6. Approved launch posture

### Allowed now

- Production deployment of the reviewed release.
- Internal operations and staff use.
- Controlled merchant onboarding with business/compliance approval.
- CPay and other service activation only for providers with valid production credentials and approval evidence.
- Cito-managed/default rails only within configured entitlement, limit, settlement and compliance controls.
- Sandbox use for services still awaiting provider production certification.

### Not allowed yet

- Unrestricted public/broad merchant GA without owners/signoff for all manual market-readiness gates.
- Marketing every implemented adapter as a live production provider.
- Claiming database HA or automatic failover.
- Bypassing maker-checker, reconciliation, KYC/KYB, entitlement or production-environment controls to accelerate onboarding.

## 7. Production acceptance criteria for this release

The release is accepted when:

- the exact production commit deploys successfully to canonical backend and frontend services;
- backend healthcheck is green;
- frontend readiness is green and tied to backend health;
- MySQL is reachable and Flyway reports schema current;
- no migration failure is present;
- no sustained JDBC/provider/callback/reconciliation failure appears after deployment stabilization;
- release SHA is traceable from GitHub to Railway deployment metadata;
- no unresolved PR remains for this release;
- manual gates remain explicitly tracked rather than silently reclassified as complete.

## 8. CTO conclusion

**Cito is approved for controlled production launch at the reviewed revision.**

The architecture and implementation are now beyond a foundation/prototype state and are suitable for real controlled operations. The remaining work is principally operational certification, external assurance, provider evidence, finance-close evidence and infrastructure resilience proof. Those are important, but they should not be confused with missing application implementation.

Broad general availability should be declared only after the manual/external launch gates have documented owners and signoff.
