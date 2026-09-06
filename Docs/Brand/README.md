# Current Cito brand standard

Read `LATEST.json` before each task. It identifies the current standard and token release. Root `AGENTS.md` makes this part of contributor/agent instructions. The complete Cito Brand Toolkit v1.1 contains the editable documents, original artwork, digital examples, messaging and quality report; obtain the controlled distribution from the brand owner rather than copying an obsolete attachment.

## Release 1.1
The user instructed application of the guidelines across the kit and use of the latest baseline in future development on 6 September 2026. This adopts the brand foundation for implementation, not a legal signature, provider certification or production approval. This folder is the engineering mirror, not the entire binary toolkit. No live application code or infrastructure is changed by this brand-governance commit.

## Required PR evidence
Add the following to every PR description. A non-customer-facing change still records a reason rather than silently skipping the assessment.

Brand version: 1.1

Brand impact: [changed touchpoints, or not applicable with a reason]

Evidence: [tokens/assets; claims and status mapping; functional and responsive/state tests; keyboard/focus/200% text and relevant assistive technology; screenshots or reason not applicable]

Approvals/exceptions: [reviewer or exception reference, expiry and mitigation; no invented approval]

Release scope: [exact surfaces changed and remaining production/specialist verification]

Run `python3 Docs/Brand/check_brand.py`. The workflow checks version consistency, approved token roles, selected contrast pairs and an explicit PR brand assessment. It cannot certify the UI, legal claims, full accessibility or all future behaviour. Making this check required in branch protection is a separate administrator decision; this commit does not change repository rulesets.

## Synchronisation
The JSON tokens mirror `03_Digital/cito.tokens.json` in the distributed kit. Keep their parsed content identical. Update the current pointer, standard, tokens, generated assets, document metadata, messaging and change log together in a reviewed release. Archive superseded versions. The package's build and verification scripts remain with the complete toolkit.

## Preserved artwork
Landscape-Logos-Cito.png SHA-256: fa3d73c73f7b41e974fb9336416d6a7a00e87bc46da2d51ec7d96bc4724ba853

CT - AW _ Logo 512x512.jpg SHA-256: a5d0ad6a6c73ff8b0ba2695408760685276108f6d83a2500f7d6b620ce52ef28

Use the supplied originals and documented clear-space crop. Do not trace, recolour or invent an app icon. New logo variants, official contacts, current service/CPay limits, claims evidence and production rollout require their own verification and approval.
