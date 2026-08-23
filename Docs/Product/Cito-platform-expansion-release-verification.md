# Cito Platform Expansion Release Verification

PR #67 is the release-candidate integration for the Cito platform expansion.

The branch must not be merged on GitHub mergeability alone. The final head commit must complete the repository's automated verification gates, including backend build/tests/Spotless, Flyway migration checks, clean MySQL migration, API-contract validation, frontend lint/tests/typecheck/build, OWASP dependency checking, CodeQL, and the readiness summary.

Any automated failure must be corrected on the PR branch and the full applicable gate set rerun against the resulting final head SHA before merge.

For PR #67, isolated Railway verification services are attached to this branch for backend/API/dependency checks, frontend quality checks, and clean-MySQL migration verification. Their evidence is valid only when Railway reports the same branch head commit hash as the pull request.

Production deployment remains separately subject to provider certification, production credentials, production-like UAT, callback/webhook verification, finance and reconciliation signoff, independent security review, compliance/regulatory approval, production secrets and infrastructure authorization, monitoring/on-call readiness, and DNS/TLS cutover approval.
