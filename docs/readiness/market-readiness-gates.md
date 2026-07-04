# CPay Market Readiness Gates

This checklist defines the software gates required before broad merchant launch.

## 1. Build gate

Required evidence:

- Backend Maven verify passes.
- Frontend install and build pass.
- Flyway migration versions are unique.
- API contract assets are present.
- CI uploads test reports.

## 2. API gate

Required evidence:

- v1.4 merchant documentation is published.
- v2 OpenAPI contract is present.
- v2 signing headers are documented.
- Error response format is documented.
- Idempotency behavior is documented.
- Callback headers are documented.
- Merchant self-service signup and channel setup APIs are documented.

## 3. Merchant self-service gate

Required evidence:

- Public merchant signup page exists.
- Public merchant signup API exists.
- Signup creates a merchant account, first merchant administrator, and merchant API keys.
- New self-service merchants are created in a pending approval state.
- Merchant login routes to the merchant dashboard after registration.
- Merchant dashboard includes a payment-channel setup page.
- Supported channels are listed for the merchant.
- Merchant channel setup values are stored server-side and returned only as masked values.
- Native v2 payments require the merchant channel to be configured and tested before use.

Manual evidence still required:

- Business approval of the merchant before live production use.
- Merchant identity and compliance review where required.

## 4. Security gate

Required evidence:

- v2 requests use signature version, timestamp, nonce, and signature.
- Replay protection is enabled.
- Admin routes require admin credentials.
- Actuator routes are separated from admin routes.
- Callback signing supports merchant-level secrets.
- Merchant channel setup values are stored in encrypted form.
- Dependency and CodeQL checks are in CI.

Manual evidence still required:

- External security review.
- Production secret storage review.

## 5. Provider gate

Required evidence:

- Provider sandbox run persistence exists.
- Sandbox run endpoint exists.
- Provider statement validation endpoint exists.
- Parser tests exist.
- Merchant channel setup supports MTN, Airtel, Airtel OpenAPI, and Safaricom.

Manual evidence still required:

- Real MTN sandbox run.
- Real Airtel sandbox run.
- Real Airtel OpenAPI sandbox run.
- Real Safaricom sandbox run.
- Real provider statement files.

## 6. Callback gate

Required evidence:

- Callback tasks are queued.
- Callback delivery is signed.
- Callback nonce and timestamp headers are sent.
- Parked callbacks can be requeued by task or merchant.
- Callback secret rotation exists.
- Callback runbook exists.

Manual evidence still required:

- Merchant callback receiver verifies signatures in sandbox.
- Merchant callback URL is reachable.

## 7. Finance gate

Required evidence:

- Reconciliation import and validation paths exist.
- Maker-checker review workflow exists.
- Approved reviews can be posted to finance workflow.
- Daily close records exist.
- Reconciliation finance summary endpoint exists.
- Daily close runbook exists.

Manual evidence still required:

- Finance signoff on settlement variance thresholds.
- Daily close dry run against staging data.

## 8. Operations gate

Required evidence:

- Operations alerts table exists.
- Operations dashboard summary endpoint exists.
- Incident response runbook exists.
- Provider certification checklist exists.
- Callback and reconciliation runbooks exist.

Manual evidence still required:

- Support rota.
- Production monitoring channel setup.
- Provider emergency contact list.

## Launch rule

Do not open broad merchant onboarding until all automated gates pass and all manual evidence items have an owner and signoff.
