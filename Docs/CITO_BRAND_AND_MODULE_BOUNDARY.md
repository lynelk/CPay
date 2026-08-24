# Cito Platform and CPay Payments Naming Boundary

## Canonical naming

**Cito** is the platform, application and customer-facing product identity.

**CPay** is the payments module within Cito.

This distinction is intentional. A blind repository-wide replacement of `CPay` with `Cito` would break valid payment contracts and make the architecture less clear, which is a fairly heroic amount of damage for a branding exercise.

## Use Cito for

Use **Cito** for platform-level names and customer-facing language, including:

- application/product title;
- landing page and portal shell;
- sign-in/sign-up experience;
- administrator and merchant portal branding;
- platform control plane;
- identity, onboarding and access-request journeys;
- platform-wide documentation titles;
- service catalogue and entitlement management;
- vending and other non-payment business modules;
- general support/product references;
- runtime application identity and observability service name.

## Use CPay for

Use **CPay** for payment-domain concepts and compatibility contracts, including:

- the payments module itself;
- collections and payouts;
- payment requests, refunds, payment links and settlement;
- payment provider/channel integrations;
- payment signing and verification;
- payment nonces and replay prevention;
- payment idempotency;
- payment callbacks/webhooks where the contract is already CPay-specific;
- payment ledger, reconciliation, treasury and payout-control concepts where they are specifically part of the payments module;
- `X-CPay-*` API headers;
- existing `CPAY_*` environment variables and `cpay.*` properties that configure the CPay module;
- existing payment API paths, database identifiers, migration names, external SDK contracts and integration artefacts where changing the identifier would break clients or deployments.

## Compatibility identifiers retained deliberately

Some historical names are not customer-facing branding and are retained until a controlled migration can prove they are safe to change. Examples include:

- repository name `CPay`;
- legacy Spring Boot entry class `CpayadminApplication`;
- database/schema names such as `cpayadmin`;
- legacy payment API documentation filenames such as `cpay-v2-openapi.yaml`;
- CPay payment configuration keys and signing headers;
- private frontend package/lock identifiers when changing them would create unnecessary build churn.

Retention does **not** mean these identify the overall product. They are compatibility surfaces or CPay payment-module artefacts.

## Current implementation alignment

The codebase already had substantial Cito platform naming before this review:

- Java package root: `net.citotech.cito`;
- Cito platform entitlement and control-plane services;
- Cito access gateway and signup gateway;
- Cito landing page;
- Cito-branded admin shell;
- Cito-branded browser title and description.

This change completes more of the safe platform-facing boundary by:

- setting the Spring application name to `Cito`;
- changing Maven display metadata to `cito-platform`;
- publishing OpenAPI as `Cito Platform API` and describing CPay as the payments module;
- identifying the installed web application/PWA as Cito;
- documenting the permanent Cito/CPay boundary so future work does not regress into ambiguous naming.

## Review rule for future changes

Before renaming a `CPay` occurrence, classify it:

1. **Platform/customer-facing?** Rename it to Cito.
2. **Payment-domain?** Keep CPay.
3. **External or persisted compatibility contract?** Keep it unless a versioned migration and deprecation path are implemented.
4. **Internal historical identifier with no external effect?** Prefer Cito when the change can be made atomically with tests.
5. **Ambiguous?** Trace consumers before changing it. Names are cheaper than outages.

## API documentation convention

Platform documentation should use:

> Cito Platform API

Payment-specific sections should use:

> CPay Payments

Existing request-signing examples must continue to show the `X-CPay-*` header family unless a future versioned CPay payment API introduces a deliberately different contract.
