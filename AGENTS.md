# Cito contributor and agent instructions

Before every design, writing or development task, read `Claude.md`, `Contributing.md`, `Docs/Brand/LATEST.json` and the brand standard referenced by that pointer. Read `Docs/Financial-correctness-and-data-integrity.md` before changing money paths. These instructions supplement, not replace, existing engineering and security rules.

The current brand baseline applies to Cito websites, portals, apps, documentation, onboarding, notifications, sales and support. Record the current brand version and affected touchpoints in the task/PR. Use canonical tokens and approved artwork, truthful operational states and capability claims, and the required accessibility/UX evidence. A backend-only change records why its brand impact is not applicable.

Do not assume an older attachment or remembered palette is current. Preserve `CPAY_*`, API routes, database identifiers, tenant isolation, financial precision, idempotency, maker-checker and audit controls. Branding never authorises a provider activation, real-money test, weaker permission or direct production push.

For a brand release, synchronise the guideline, toolkit, repository mirror, token metadata, generated assets, templates and change log. Run `python3 Docs/Brand/check_brand.py`. The repository mirror is an engineering reference; it is not evidence that the live application or complete kit has been deployed.

Follow `feature/* -> main -> sandbox -> production` and the existing review/CI rules. No forced branch updates or invented approvals. Consult `Docs/Brand/README.md` for release evidence and remaining integration duties.
