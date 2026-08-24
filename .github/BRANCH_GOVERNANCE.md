# Branch Governance

## Release topology

The repository release flow is strictly:

`feature/* -> main -> sandbox -> production`

- `main` is the integration branch. Changes reach it through pull requests after CI.
- `sandbox` is updated only by `.github/workflows/promote-sandbox.yml` after successful CI on `main`.
- `production` is updated only by `.github/workflows/promote-production.yml`, which accepts the current `sandbox` head only and refuses non-fast-forward updates.

## GitHub branch protection

Configure GitHub repository rules so `main` and `production` cannot be rewritten or casually pushed.

### main

- Require a pull request before merging.
- Require at least one approval when more than one maintainer is available.
- Dismiss stale approvals when new commits are pushed.
- Require conversation resolution before merging.
- Require the `Readiness Gate Summary` CI check.
- Block force pushes.
- Block branch deletion.
- Apply the rule to administrators, with emergency bypass limited to repository administrators.

### production

- Block direct human pushes.
- Block force pushes.
- Block branch deletion.
- Require updates to be fast-forward only from the verified `sandbox` head.
- Permit the repository's production-promotion GitHub Actions workflow to update the branch.
- Keep the GitHub `production` environment approval gate enabled for the promotion workflow.

## Invariants enforced by workflows

Production promotion fails unless all of the following are true:

1. The checked-out commit exactly equals `origin/sandbox`.
2. `sandbox` is contained in `main`.
3. `production` is an ancestor of `sandbox`.
4. The resulting update is a fast-forward.

No workflow or operator should push feature, verification, formatting, migration-test, or go-live-check commits directly to `production`.
