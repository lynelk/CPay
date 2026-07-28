# CPay Developer Docs

Welcome to the CPay merchant integration docs. CPay is a payments gateway for mobile money
collections, payouts, status checks, balances, callbacks, and reconciliation across MTN MoMo,
Airtel Money, Airtel OpenAPI, and Safaricom M-Pesa.

This page is a scaffold for a real hosted docs site. There is no hosting/build pipeline behind it
yet (see "About this page" at the bottom) — it exists so merchants and integrators have one place to
start instead of hunting through the `Docs/` folder. It follows the documentation-portal outline
already agreed in [`Docs/Developer-experience.md`](../Developer-experience.md#documentation-portal):
quickstart, request signing, idempotency, collections, payouts, refunds, account validation,
statement export, webhook verification, test mode, and the error catalog.

## Getting started

CPay API v2 (`/api/v2/**`) is the supported surface for new integrations. Every request is signed
with your merchant's RSA private key — see [Request signing](../Api-v2-signing.md) for the full
canonical-string contract. The snippet below is copied as-is from
[`Sdk/Readme.md`](../../Sdk/Readme.md) and uses the first-party Node.js helper in
[`Sdk/Node/cpay-client.js`](../../Sdk/Node/cpay-client.js), so it is guaranteed to match what ships
in this repo rather than a hand-typed example that can drift.

```js
const { CPayClient } = require("./node/cpay-client");

const client = new CPayClient({
  baseUrl: "https://cpay.example.com",
  merchantNumber: "1000003",
  privateKeyPem: process.env.CPAY_PRIVATE_KEY
});

await client.collect({
  amount: "1000",
  currency: "UGX",
  country: "UG",
  reference: "INV-100",
  description: "Invoice 100",
  callbackUrl: "https://merchant.example.com/callback",
  payer: { type: "MSISDN", value: "256770000000" }
});
```

Prefer Python or PHP? The equivalent helpers and a matching quickstart snippet for each are in
[`Sdk/Readme.md`](../../Sdk/Readme.md), backed by [`Sdk/Python/cpay_client.py`](../../Sdk/Python/cpay_client.py)
and [`Sdk/Php/CPayClient.php`](../../Sdk/Php/CPayClient.php). All three helpers return the same five
signed headers (`X-CPay-Merchant-Number`, `X-CPay-Signature-Version`, `X-CPay-Timestamp`,
`X-CPay-Nonce`, `X-CPay-Signature`) plus `X-CPay-Idempotency-Key` when one is supplied or generated.

Want a generated client instead of the hand-written helpers? See
[`Sdk/codegen/README.md`](../../Sdk/codegen/README.md) for how to generate a full Node/TypeScript or
Python client from the OpenAPI spec below.

## API reference

| Resource | What it's for |
|---|---|
| [OpenAPI spec](../Api/cpay-v2-openapi.yaml) | Machine-readable contract for every `/api/v2/**` route (requests, responses, error shapes). Feed this into an OpenAPI viewer (Swagger UI, Redoc) or a codegen tool. |
| [Postman collection](../Api/cpay-v2-postman-collection.json) | Import into Postman/Insomnia for ready-to-run requests against sandbox. |
| [API v2 examples](../Api-v2-examples.md) | Copy-paste request bodies for collect, payout, and channel listing. |
| [Request signing](../Api-v2-signing.md) | The canonical-string format, required headers, idempotency key behavior, and nonce replay protection. |
| [Error catalog](../Error-catalog.md) | Stable machine-readable error codes (`code`, `category`, `retryable`) and the legacy numeric-code mapping. |
| [Pagination standard](../Pagination.md) | Cursor-based list pagination: request/response shape and ordering guarantees. |
| [API versioning & legacy deprecation](../Api-versioning-deprecation.md) | What `Deprecation`/`Sunset`/`Link` headers on legacy routes mean and the v1 -> v2 migration policy. |
| [Webhook event registry](../Webhook-events.md) | Versioned webhook envelope and event types delivered to your callback URL. |

## Guides

| Guide | What it's for |
|---|---|
| [Merchant self-service onboarding](../Merchant-self-service.md) | Signing up and configuring payment channels from the merchant portal without an admin creating the account manually. |
| [Developer experience](../Developer-experience.md) | The intended SDK/documentation-portal shape this page is the first step toward. |
| [Gateway adapter guide](../Gateway-adapter-guide.md) | For provider integration work, not merchant integration — how a payment channel adapter is implemented internally. |
| [CitoConnect integration](../Citoconnect-integration.md) | The contract between CPay and CitoConnect specifically, if you are integrating as CitoConnect rather than as a standalone merchant. |

## SDKs

| Language | Signing helper | Client wrapper |
|---|---|---|
| Node.js | [`Sdk/Node/cpay-signing.js`](../../Sdk/Node/cpay-signing.js) | [`Sdk/Node/cpay-client.js`](../../Sdk/Node/cpay-client.js) |
| Python | [`Sdk/Python/cpay_signing.py`](../../Sdk/Python/cpay_signing.py) | [`Sdk/Python/cpay_client.py`](../../Sdk/Python/cpay_client.py) |
| PHP | [`Sdk/Php/CPaySigning.php`](../../Sdk/Php/CPaySigning.php) | [`Sdk/Php/CPayClient.php`](../../Sdk/Php/CPayClient.php) |

These are hand-written, dependency-light helpers meant to be copied into your integration. If you
want a fully generated client (more endpoint coverage, typed models) instead, see
[`Sdk/codegen/README.md`](../../Sdk/codegen/README.md).

## About this page

This is a static Markdown scaffold, not a built/hosted site. There is no site generator, search
index, or versioned-docs pipeline wired up here — that is out of scope for now. What it gives you
today:

- One landing page that links every merchant-facing doc that already exists in this repo, instead of
  requiring you to browse `Docs/` folder-by-folder.
- A quickstart snippet that is copied verbatim from `Sdk/Readme.md` rather than re-typed, so it can't
  silently drift from the SDKs it references.

What it does **not** do yet, so a future engineer doesn't assume otherwise:

- No static site generator (Docusaurus, MkDocs, VitePress, etc.) is wired up. This file renders as
  plain Markdown on GitHub today; it is not deployed anywhere as HTML.
- No search, versioning, or navigation sidebar beyond the tables above.
- The OpenAPI spec and Postman collection are linked as static files, not rendered inline; a real
  docs site would embed a Swagger UI/Redoc viewer for the OpenAPI spec.
