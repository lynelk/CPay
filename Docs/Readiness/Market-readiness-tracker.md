# CPay Market Readiness Tracker

This tracker lists every gate from `Docs/Readiness/Market-readiness-gates.md` with a status and an
assigned owner role so launch signoff is tracked, not just documented.

## How to use

- **Status** values: `NOT STARTED`, `IN PROGRESS`, `BLOCKED`, `DONE`, `N/A`.
- **Owner** is the role accountable for producing/approving the evidence (e.g. `Platform Eng`,
  `Operations`, `Finance`, `Security`, `Compliance`). Assign a named person before launch.
- **Evidence** is the artifact that proves the item (CI job name, report link, signoff document).
  Every `DONE` row must point at a real artifact.
- Update the row `Updated` date whenever the status or evidence changes.
- The launch rule in `Market-readiness-gates.md` forbids broad merchant onboarding until every
  automated gate passes and every manual item has an owner and completed signoff.

## 1. Build gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 1.1 | Backend Maven verify passes | DONE | Platform Eng | CI `backend` job (`mvn -B verify`), local `mvn verify` 768 tests green |
| 1.2 | Frontend install and build pass | DONE | Platform Eng | CI `frontend` job (`npm ci`, `lint`, `test`, `typecheck`, `build`) |
| 1.3 | Flyway migration versions are unique | DONE | Platform Eng | CI `migration-smoke` job; currently V1–V60 unique |
| 1.4 | API contract assets are present | DONE | Platform Eng | CI `api-contract` job; `Docs/Api/cpay-v2-openapi.yaml` 109 paths, Postman collection |
| 1.5 | CI uploads test reports | DONE | Platform Eng | CI `backend` job uploads `backend-surefire-reports` |

## 2. API gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 2.1 | Merchant documentation published (site index, OpenAPI, Postman, sandbox guide, SDK) | IN PROGRESS | Platform Eng | `Docs/site/index.md`, `Docs/Api/*`, `Docs/sandbox-guide.md`, `Sdk/` |
| 2.2 | v2 OpenAPI contract present | DONE | Platform Eng | `Docs/Api/cpay-v2-openapi.yaml` |
| 2.3 | v2 signing headers documented | DONE | Platform Eng | `Docs/Api-v2-signing.md` |
| 2.4 | Error response format documented | DONE | Platform Eng | `Docs/Error-catalog.md` |
| 2.5 | Idempotency behavior documented | DONE | Platform Eng | `Docs/Api-v2-examples.md`, `Docs/Error-catalog.md` |
| 2.6 | Callback headers documented | DONE | Platform Eng | `Docs/Webhook-events.md`, runbooks |
| 2.7 | Self-service signup and channel-setup APIs documented | DONE | Platform Eng | `Docs/Merchant-self-service.md` |

## 3. Merchant self-service gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 3.1 | Public merchant signup page exists | DONE | Platform Eng | `Clientside` `/signup` |
| 3.2 | Public merchant signup API exists | DONE | Platform Eng | `MerchantsController` signup route |
| 3.3 | Signup creates merchant, first admin, and API keys | DONE | Platform Eng | `MerchantsController` |
| 3.4 | New self-service merchants pending approval | DONE | Platform Eng | `pending approval` state, V10 |
| 3.5 | Signup rate limited by client source | DONE | Platform Eng | DB-backed signup rate limiting |
| 3.6 | Payment-channel setup page | DONE | Platform Eng | `Merchant Dashboard -> Payment Channels` |
| 3.7 | Sandbox/production switching + production cap | DONE | Platform Eng | Merchant environment preferences + cap settings |
| 3.8 | Supported channels listed | DONE | Platform Eng | `GET /api/v2/channels` |
| 3.9 | Channel values stored server-side, masked on read | DONE | Platform Eng | `MERCHANT_CHANNEL_ENCRYPTION_KEY` + masked service |
| 3.10 | Channel setup requires URLs + setup values | DONE | Platform Eng | channel setup flow |
| 3.11 | Native v2 requires configured, tested channel | DONE | Platform Eng | adapter gateway pre-flight |
| 3.12 | Payment links / hosted checkout / invoices tested | DONE | Platform Eng | v2 endpoints + portal flows; OpenAPI documented |
| 3.13 | **Business approval of merchant before live use** | NOT STARTED | Compliance | Go-live approval process |
| 3.14 | **Merchant identity and compliance review** | NOT STARTED | Compliance | KYB review workflow |

## 4. Security gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 4.1 | v2 requests use version, timestamp, nonce, signature | DONE | Platform Eng | `Docs/Api-v2-signing.md`, `V2RequestSecurityService` |
| 4.2 | Replay protection enabled | DONE | Platform Eng | JDBC nonce store (`CPAY_SECURITY_NONCE_STORE=jdbc`) |
| 4.3 | Admin routes require admin credentials | DONE | Platform Eng | path-level + `@PreAuthorize` |
| 4.4 | Actuator routes separated from admin | DONE | Platform Eng | `/actuator/**` under `ACTUATOR_*` creds |
| 4.5 | Callback signing supports merchant-level values | DONE | Platform Eng | webhook/callback secret model |
| 4.6 | Channel setup values encrypted at rest | DONE | Platform Eng | AES-GCM credential store |
| 4.7 | API origins restricted to trusted origins | DONE | Platform Eng | `CORS_ALLOWED_ORIGINS` / trusted IPs |
| 4.8 | Dependency and CodeQL checks in CI | DONE | Platform Eng | CI `owasp` + `codeql` jobs |
| 4.9 | **External security review** | NOT STARTED | Security | Signed-off review report |
| 4.10 | **Production configuration review** | NOT STARTED | Security | `/etc/cpay/.env` checklist walkthrough |

## 5. Provider gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 5.1 | Provider sandbox run persistence | DONE | Platform Eng | `provider_sandbox_runs` + endpoint |
| 5.2 | Sandbox run endpoint | DONE | Platform Eng | `POST /api/v2/admin/provider-sandbox/run` |
| 5.3 | Provider statement validation endpoint | DONE | Platform Eng | `POST /api/v2/admin/statements/check` |
| 5.4 | Parser tests exist | DONE | Platform Eng | provider statement parser tests |
| 5.5 | Channel setup supports MTN/Airtel/Airtel OpenAPI/Safaricom/Yo! | DONE | Platform Eng | channel catalog |
| 5.6 | Adapters call configured endpoint URLs | DONE | Platform Eng | adapter gateway, `RestClient` transport |
| 5.7 | Missing endpoint URLs rejected in production | DONE | Platform Eng | gateway pre-flight checks |
| 5.8 | **Real MTN sandbox run** | NOT STARTED | Operations | certified evidence in `provider-certification` UI |
| 5.9 | **Real Airtel sandbox run** | NOT STARTED | Operations | certified evidence |
| 5.10 | **Real Airtel OpenAPI sandbox run** | NOT STARTED | Operations | certified evidence |
| 5.11 | **Real Safaricom sandbox run** | NOT STARTED | Operations | certified evidence |
| 5.12 | **Real Yo! Payments sandbox run** | NOT STARTED | Operations | certified evidence |
| 5.13 | **Real provider statement files** | NOT STARTED | Operations | validated statement files |
| 5.14 | **Provider production approval** | NOT STARTED | Operations | provider signoff per channel |

## 6. Callback gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 6.1 | Callback tasks queued | DONE | Platform Eng | `callback_tasks` + claim records |
| 6.2 | Callback delivery signed | DONE | Platform Eng | signing service |
| 6.3 | Callback nonce and timestamp headers sent | DONE | Platform Eng | delivery headers |
| 6.4 | Parked callbacks requeueable | DONE | Platform Eng | `POST /api/v2/admin/callback-admin/retry-task` |
| 6.5 | Callback secret rotation exists | DONE | Platform Eng | rotate-secret endpoints |
| 6.6 | Workers claim tasks before processing | DONE | Platform Eng | claim-based processing |
| 6.7 | Callback runbook exists | DONE | Platform Eng | `Docs/Runbooks/Callback-security-and-requeue.md` |
| 6.8 | **Merchant receiver verifies signatures in sandbox** | NOT STARTED | Operations | sandbox callback verification evidence |
| 6.9 | **Merchant callback URL reachable** | NOT STARTED | Operations | delivery log evidence |

## 7. Finance gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 7.1 | Reconciliation import and validation paths | DONE | Platform Eng | import/check endpoints |
| 7.2 | Maker-checker review workflow | DONE | Platform Eng | reviews + approve/reject |
| 7.3 | Approved reviews post to finance workflow | DONE | Platform Eng | `POST /api/v2/admin/recon-finance/post` |
| 7.4 | Daily close records | DONE | Platform Eng | recon daily-close records |
| 7.5 | Finance summary endpoint | DONE | Platform Eng | `GET /api/v2/admin/recon-finance/summary` |
| 7.6 | Daily close runbook | DONE | Platform Eng | `Docs/Runbooks/Reconciliation-finance-daily-close.md` |
| 7.7 | **Finance signoff on settlement variance thresholds** | NOT STARTED | Finance | signed-off thresholds |
| 7.8 | **Daily close dry run against staging data** | NOT STARTED | Finance | staging dry-run report |

## 8. Operations gate

| # | Evidence item | Status | Owner | Evidence |
|---|---|---|---|---|
| 8.1 | Operations alerts table | DONE | Platform Eng | `operations_alerts` |
| 8.2 | Operations dashboard summary endpoint | DONE | Platform Eng | `GET /api/v2/admin/ops-dashboard/summary` |
| 8.3 | Operating-control event table | DONE | Platform Eng | `operating_control_events` |
| 8.4 | Operating-control summary endpoint | DONE | Platform Eng | `GET /api/v2/admin/operating-controls/summary` |
| 8.5 | Incident response runbook | DONE | Platform Eng | `Docs/Runbooks/Production-incident-response.md` |
| 8.6 | Provider certification checklist | DONE | Platform Eng | `Docs/Runbooks/Provider-certification-checklist.md` |
| 8.7 | Callback and reconciliation runbooks | DONE | Platform Eng | `Docs/Runbooks/` |
| 8.8 | Production code controls document | DONE | Platform Eng | `Docs/Production-code-controls.md` |
| 8.9 | **Support rota** | NOT STARTED | Operations | on-call rota + escalation |
| 8.10 | **Production monitoring channel setup** | NOT STARTED | Operations | alert routes confirmed |
| 8.11 | **Provider emergency contact list** | NOT STARTED | Operations | contacts document |

## Launch rule

Do not open broad merchant onboarding until:

- every `DONE` automated-row is backed by a passing CI run, and
- every manual item has `Status = DONE` with a named owner and a real evidence artifact.

Rows marked `NOT STARTED` in the tables above are the only remaining blockers between the current
codebase and the automated + manual launch gates.
