# CPay — Codebase Status vs. 12-Workstream Prompt Pack & Cost/Speed Roadmap

> Archive note (2026-08-12): This file is a point-in-time review extract, not the current source of truth. For current setup, architecture, security, sandbox, and API guidance, use `Readme.md`, `Docs/Readme.md`, `Docs/Architecture/Overview.md`, `Docs/developer-guide.md`, and `Docs/sandbox-guide.md`.

> Verified against the live working tree at `c:/Dev/CPay`. This report supersedes project_info__1/2/3 where the code has moved on (Flyway V30 → V32; several audit items now closed).

---

## Summary

CPay is a mobile-money payments gateway (collections, payouts, status checks, balances, callbacks, reconciliation, merchant self-service, finance/ops workflows) for MTN MoMo, Airtel Money, Airtel OpenAPI, Safaricom M-Pesa, and Yo! Payments. Two API generations run side-by-side: legacy `/api`/`/api/v1` (must stay stable) and signed/versioned `/api/v2/**` backed by an adapter/ledger/risk architecture.

**The single most important finding for your cost/speed decision:** the repository has advanced well past the state the attached prompt pack assumes. On this checkout the code sits at **Flyway V32**, the **v2 OpenAPI contract is version 1.5.0**, and **roughly 60–70% of the 12 workstreams' recommended work already exists in code**. Running the deck verbatim would burn budget re-implementing finished features. The real remaining work is concentrated in a far smaller set of gaps — mostly UI surfaces, maker-checker approval enforcement, an admin RBAC matrix, and hardening/verification.

---

## What's already done per workstream (verified in code — do NOT re-pay for this)

| Workstream | Verified status on this checkout |
|---|---|
| 1. v2 API contract | **Mostly done**. `PaymentsV2Controller` (collect/payout/status/balances/channels/account-validate/statements json+csv+xlsx), `RefundsV2Controller` (partial amounts, lifecycle via `RefundService`/`RefundStatus`), `BatchPayoutsV2Controller` (status + retry-failed), FX quotes + cross-border transfer intents, payment links + invoices + hosted checkout, idempotency keys on v2. OpenAPI 1.5.0 documents most of it. |
| 2. Merchant KYB | **Backend done**. Beneficial owners (`beneficial_owners`, hashed IDs, PENDING screening), KYC documents (`merchant_kyc_documents`), compliance profiles + tiers (V32 `kyc_tier_limits`), per-merchant readiness blocking. |
| 3. Admin security/RBAC | **Partial**. `@EnableMethodSecurity` + `@PreAuthorize` on admin/recon controllers, `AdminPermissionService` + seeded permissions, admin TOTP MFA enrollment. |
| 4. Provider certification | **Backend done**. `ProviderCertificationService` (sandbox + statement evidence, approve CAPTURED→APPROVED, coverage summary vs. `provider_certification_requirements`), OpenAPI endpoints for evidence + approve. |
| 5. Webhook/callback | **Mostly done**. `WebhookEventCatalog` (versioned types + JSON-Schema envelope), merchant self-service register/rotate(replay (merchant-scoped), delivery log, HMAC-signed delivery, backoff retry, callback claims + park/requeue, per-merchant callback secrets. |
| 6. Reconciliation/finance close | **Backend done**. 5 provider parsers, import/validate/match/manual-match workbench, review approve/reject/post, daily close, settlement batches with double-entry ledger posting, sweep scheduler + merchant settlement preferences. |
| 7. Payout risk controls | **Partial**. Risk authorization on v2 + legacy, step-up MFA for high-value payout batches (fails closed), payout compensation saga (V18, alertable STUCK states). |
| 8. Cross-border/FX/treasury | **Backend foundation done**. Corridors, FX quotes (atomic claim + expiry), transfer intents, treasury reservation, corridor limits. |
| 9. AML/KYC/KYB compliance | **Backend done**. `SanctionsScreeningService` (hashed watchlist, BLOCK vs REVIEW, screening hits), compliance cases + risk-decision capture + decision + notes, KYC profiles/tiers/owners/documents. |
| 10. UX/workflows | **Partial**. `OperationsConsole.tsx` is now a real ops surface (channels, callback/webhook health, operating controls, go-live readiness, run-callbacks + auto-match) on TanStack hooks; merchant webhook manager UI exists. |
| 11. Dev experience | **Extensive docs exist** (README, OpenAPI 1.5.0, Postman, signing, adapter guide, runbooks, ADRs). |
| 12. Testing/CI | **Mostly done**. CI: backend `mvn verify` + SBOM, OWASP (fail CVSS≥9), CodeQL, migration-uniqueness check, OpenAPI presence checks, frontend lint/test/typecheck/build, readiness doc gate. |

---

## The real remaining gaps (the actual cost)

### Phase 1 — Compliance/safety hardening (highest value per dollar; ~1 dev-week per item)

1. **Maker-checker on finance close, settlement close, and manual-match approve.** `FinanceWorkflowService.dailyClose` and `SettlementOpsService.closeBatch` are single-actor writes today. This is exactly the "who approved what" failure mode the deck itself calls #1. Fix = state field + second actor + audit rows; no new architecture.
2. **Payout limits + approval queue** (daily/monthly/per-tx/beneficiary-velocity). Compensation saga + step-up MFA exist; limits are the missing control.
3. **Webhook replay admin endpoint + test-callback button.** Backend `replay(merchantId, deliveryId)` exists; expose the admin endpoint + UI action. Small, high merchant value.
4. **v1 idempotency/nonce/signing** — real safety gap but the largest single refactor; schedule after Phase 2 since the v2 compat path already provides idempotency.

### Phase 2 — UI surfaces that unlock the finished backends (~2–3 dev-weeks total)

The KYB, certification, finance-close, and settlement backends are done — what's missing is humans using them. Four screens, each following the established `ModuleReconciliation.tsx` / `MerchantModuleWebhooks.tsx` pattern:

- Merchant onboarding tracker (stepper over signup/email/KYB/channels/sandbox/callback/pricing/approval)
- Admin KYB review UI (approve/reject/request-info on owners + documents + profiles)
- Finance daily-close dashboard
- Provider certification dashboard

### Phase 3 — API completeness + docs (~1–2 dev-weeks)

Missing OpenAPI endpoints/schemas: webhooks events/replay, settlements list/statement, beneficiaries, compliance-case decision, FX quote GET, cross-border GET/cancel. Plus consolidated developer guide, v1→v2 migration, sandbox guide, curl examples.

### Phase 4 — Structural long-poles (separate funded track; NOT cheap)

- v1 money-path ledger unification + idempotency (dual-write `/api/v1` through `LegacyLedgerPostingService` + risk + reservation)
- God-class decomposition (`TransactionsLogController` ~6,000+ lines, `Common` ~2,900)
- Beneficiaries/corridor reports/treasury positions — only if cross-border is a confirmed product priority

---

## Cost estimate

**~6–9 developer-weeks** (single dev) to close everything above; **~3–4 weeks wall-clock with two parallel tracks** (backend hardening ∥ frontend surfaces + docs). This is dramatically less than running the full 12-workstream deck, because the heavy lifting is already in the tree.

**Bottom line:** skip Phase 0-of-the-deck (don't re-implement what exists), start with maker-checker + payout limits (cheapest safety wins), parallelize the four UI screens with docs, and defer the v1/decomposition long-poles to a dedicated track.

---

The full report is saved at **`project_info__4.md`**. Note: I'm in Explore Mode (read-only) — to implement any of this, switch to **Act Mode** using the mode selector; the findings above will carry over as context.
