# Cito brand development standard

Version 1.1 · Active implementation baseline · 6 September 2026

## Mandatory start for every change
Read the current `LATEST.json`, the complete brand guideline, the relevant toolkit asset and this standard before designing, writing or coding. Record the version in the task, issue, pull request or handover. Do not rely on an older attachment or a remembered colour. This standard applies to websites, admin/merchant/developer portals, apps, APIs and developer documentation, onboarding, sales, notifications, support, reports and partner material.

Brand rules complement, never override, financial correctness, security, privacy, accessibility and compatibility. Preserve `CPAY_*`, API routes, database identifiers and contracts unless a separately reviewed migration explicitly changes them. A visual rebrand is not permission to rewrite monetary or entitlement logic.

## Brand foundation
Corporate name: Cito Technologies. Platform: Cito. Explain the payments relationship as Cito Payments (CPay); retain CPay where the actual channel or legacy contract requires it. Tagline: Connect with confidence. Mission: Help businesses connect essential digital services, simplify daily operations and serve people with confidence. Promise: clarity, control and accountability across connected services.

Values must appear in behaviour: clarity in costs and next steps; accountability through references and timely updates; respect for privacy and unfamiliar users; practical inclusion across devices/connections; progress supported by tested evidence. Use British English and a calm, clear, capable, considerate voice. Explain what happened, what it means and the safest next step. Never blame users or use internal error jargon as customer guidance.

## Architecture and navigation
Use descriptive service groups: Payments, Communications, Identity & Risk, Vending, Billing & Finance. Treat administration, platform settings and developer tools as role-based utilities. Provider names belong within their service context, not as competing top-level product identities. Brand grouping is not proof every feature is released.

Every operational screen identifies the relevant business/workspace, role, environment, permissions and next useful action. Preserve tenant isolation when switching businesses. Use existing authorised capability and provider state to distinguish Available, Pilot, Planned, Restricted and Unavailable. Do not expose a service merely to fill a navigation menu.

## Tokens and typography
Use the current canonical JSON token release, not raw competing palettes. The core anchors are primary #1561A3, mid #4C8DC5, sky #70B3E3, descriptor #676766, heading #102A43, body #1F2937, muted #526575, page #F4F8FC, divider #D7E2EC and control border #738496. Semantic success is #147D57, warning #8A5700, error #B42318 and information #1561A3, with the tested surface pairings in the tokens. Mid/sky blue are not normal text on white; divider colour is not an essential control boundary.

Use Inter with system sans-serif/Arial fallback. Do not replace logo lettering. Web body is 16 px and short captions 14 px; Word body 11 pt, tables 10.5 pt, short notes 9.5 pt; slide titles 36 pt, body 24 pt, short notes 16 pt. Reflow content rather than shrinking below these implementation sizes. Use a 4 px spacing basis, 8 px control radius and 12 px card radius. Generate CSS and email colours from the token source. Test fonts and line breaks on the delivery platform. Never distribute font files in this kit.

## Logo and visual assets
Use only supplied originals and the documented clear-space placement derivative. Original artwork and colours remain unchanged. Clear space is at least one quarter of the I cap-height. The visible full lockup minimum is 280 CSS pixels or 65 mm. The padded placement canvas minimum is 315 CSS pixels or 73 mm. Use an ordinary Cito text label when that cannot fit; do not invent a compact symbol or treat the square JPEG as a favicon. No stretching, filters, recolouring, extra outlines or guessed vectors.

Use authentic, respectful imagery of real work. Record rights, consent, purpose and expiry; avoid exposed financial/identity data, stereotypes and invented customer evidence. Provider logos and badges need permitted use and a truthful relationship. New vector, compact, reversed, monochrome and print masters require separate approval.

## Truthful operational states
Submitted is not completed; completed is not settled; payment is not fulfilment. Map each user-facing status to an authoritative domain state, including pending, failed, reversed and partial outcomes. Keep available, reserved, pending and total balances separate. Show currency, provider/channel, last update and stale/error state. Never display an unavailable balance as zero.

For a pending payment tell the user to check status before trying again; do not encourage duplicate collection/disbursement. Reuse idempotency, maker-checker, permission, audit and reconciliation controls. CPay is the default channel only where actually provisioned; show the actual configured limit, permitted actions and credential transition. Do not invent the amount or activate a provider through a branding change. Sandbox and live remain visibly and technically distinct.

## Communication and claims
Describe released, supported capabilities for the actual audience and market. Show material fees, limits, eligibility and dependencies before commitment. Avoid unqualified instant, free, unlimited, fully compliant, bank-grade, 100% secure, guaranteed and licensed claims. A build, provider connection, external provider licence or code stub is not Cito certification. Require evidence, scope, owner, approval and review date.

Error messages state the problem, safe recovery and help reference without raw provider bodies or secrets. Incident messages separate known facts from investigation, state affected service/time zone, publish the next update time and keep that commitment. Identity requests explain purpose and safe submission; never request passwords, PINs or OTPs. Use synthetic or masked demonstration data. Replace every template field before external publication.

## Component and accessibility acceptance
Use shared logo, navigation, button, form, notice, table and status components. Show explicit loading, empty, success, error, permission-restricted and safe-recovery states where applicable. Preserve labels, focus order, keyboard activation and relevant live status announcements without unnecessary focus movement.

Target WCAG 2.2 AA: normal text 4.5:1, large text 3:1 and meaningful controls/graphics 3:1. Prefer 44 × 44 CSS-pixel targets; the standard's AA minimum criterion is 24 × 24 with exceptions. Do not use colour alone. Test at 320, 390, 768 and 1440 CSS pixels, 200% text, keyboard-only use, reduced motion, long/localised strings, stale data, slow connection and relevant assistive technology. Check focus against adjacent surfaces, overlays and clipping. Dark themes and charts require intentional additional review, not automatic inversion. Token checks are not a conformance assessment.

## Definition of done
Each changed touchpoint records the current brand version, audience and task; uses controlled assets and semantic tokens; presents truthful capabilities, states and context; has responsive and state evidence; passes relevant functional/security/financial tests; and updates documentation, support and messaging affected by the change. Attach before/after views and test results, excluding personal data and secrets. Mark non-applicable checks with a reason, never a silent blank.

## Exceptions and release
Record rule, scope, reason, user risk, mitigation, owner, reviewer, expiry and remediation in the exception register. No exception permits misleading financial states, unsupported certification, credential exposure or weakened permissions. Archive superseded assets. Update the current-version pointer, token metadata, guide, generated outputs, templates, messages and repository mirror together. Run automated checks and negative fixtures, then use normal review and CI. Record exact live surfaces changed and remaining dependencies. Never claim a ZIP, token check or PR deployed the application.

## Reference sources
WCAG 2.2: https://www.w3.org/TR/WCAG22/
Official Inter publisher: https://rsms.me/inter/
The complete guideline and root README contain source notes, examples and logo provenance. Sources checked 6 September 2026.
