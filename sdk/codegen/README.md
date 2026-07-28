# CPay SDK Codegen

`Sdk/Node`, `Sdk/Python`, and `Sdk/Php` ship small, hand-written, dependency-light helpers that do
request signing plus a handful of wrapper methods (`collect`, `payout`, `validateAccount`,
`createPaymentLink`, `statements`, ...). They intentionally do not cover every `/api/v2/**` route.

This directory is a scaffold for generating a **full** API client — every documented endpoint,
typed request/response models, generated from the spec rather than hand-maintained — using
[`openapi-generator-cli`](https://openapi-generator.tech/). Nothing in this directory is run
automatically (not part of `mvn` or `npm` builds, not part of CI) and no generated output is
vendored into the repo. It exists for a human to run when a fuller client is actually needed.

## Spec source

A static OpenAPI 3.0.3 spec is already checked in at:

```text
Docs/Api/cpay-v2-openapi.yaml
```

(title: "CPay Gateway API", currently version `1.5.0`). This is a **hand-maintained** spec, not one
generated from the running application, so treat it as the intended contract rather than a guaranteed
byte-for-byte mirror of every implemented route — cross-check against
`Docs/Api-versioning-deprecation.md`'s acceptance check ("The OpenAPI document and Postman collection
stay aligned with implemented v2 routes") and the actual `api/v2/**` controllers if you hit a
mismatch.

The backend can also serve a live, code-generated spec via springdoc
(`springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` in
`InitializrSpringbootProjectFresh/src/main/resources/application.properties`), but both are
**disabled by default** (`SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` default to
`false`). If you want to generate from the live surface instead of the static file, set both env vars
to `true`, start the backend, and point the commands below at
`http://localhost:8081/v3/api-docs` instead of the YAML file — but do this deliberately, since
exposing springdoc endpoints has its own security/production considerations (see
`Docs/Production-code-controls.md`) and is not something to flip on in a deployed environment
casually.

## Requirements

- Node.js (to run `openapi-generator-cli` via `npx`; see the repo root `Readme.md` for the required
  version already used by `Clientside/`)
- A JRE — `openapi-generator-cli` is a Java tool under the hood even when invoked through `npx`
- Network access the first time you run it, to fetch the generator package/jar

Nothing here requires installing `openapi-generator-cli` globally; every command below uses `npx` so
it resolves and caches the tool on demand.

## Generate a Node.js / TypeScript client

```bash
npx @openapitools/openapi-generator-cli generate \
  -i Docs/Api/cpay-v2-openapi.yaml \
  -g typescript-fetch \
  -o Sdk/codegen/generated/typescript \
  --additional-properties=npmName=cpay-api-client
```

Swap `-g typescript-fetch` for `-g typescript-axios` or `-g typescript-node` if you prefer a
different HTTP layer — all three read the same spec.

## Generate a Python client

```bash
npx @openapitools/openapi-generator-cli generate \
  -i Docs/Api/cpay-v2-openapi.yaml \
  -g python \
  -o Sdk/codegen/generated/python \
  --additional-properties=packageName=cpay_api_client
```

## Or just run the script

`Sdk/codegen/generate.sh` runs both of the above commands (TypeScript + Python) with the same
defaults, and lets you override the spec path (`CPAY_OPENAPI_SPEC`), output directory
(`CPAY_CODEGEN_OUT`), or generator version (`OPENAPI_GENERATOR_VERSION`) via environment variables.
It has not been executed as part of this change — it is a script for a human to run, not a working
generated client checked in alongside it.

```bash
bash Sdk/codegen/generate.sh
```

## What a generated client does NOT give you for free

A generated client handles request/response shapes and typed models. It does **not** know about
CPay's request-signing contract — every `/api/v2/**` call still needs the headers described in
`Docs/Api-v2-signing.md` (`X-CPay-Merchant-Number`, `X-CPay-Signature-Version`, `X-CPay-Timestamp`,
`X-CPay-Nonce`, `X-CPay-Signature`, optionally `X-CPay-Idempotency-Key`), computed from the canonical
string over method + path + query + timestamp + nonce + body hash.

Most OpenAPI generators support a custom `fetch`/`http` middleware or interceptor hook where you can
inject those headers per request. The existing hand-written helpers already implement the canonical
string and signing logic and are the reference for that logic:

- `Sdk/Node/cpay-signing.js`
- `Sdk/Python/cpay_signing.py`
- `Sdk/Php/CPaySigning.php`

The practical path today is: use a generated client for typed models and full endpoint coverage, but
either (a) wire its HTTP client to call through the existing signing helper before each request, or
(b) keep using the hand-written client wrappers in `Sdk/Node`, `Sdk/Python`, `Sdk/Php` for the
handful of routes they already cover, and fall back to the generated client only for endpoints they
don't. Nothing in this repo currently does (a) automatically — that integration glue is not written,
and is the main piece of follow-up work if this scaffold gets picked up.

## Explicitly out of scope for this scaffold

- No generated output is committed here (`Sdk/codegen/generated/` is gitignored).
- `generate.sh` has not been run or verified against a live `openapi-generator-cli` invocation as
  part of this change — treat it as a documented starting point, not a tested pipeline.
- No CI job runs codegen or diffs it against a committed client.
