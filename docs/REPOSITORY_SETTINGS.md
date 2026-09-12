# Repository Settings

This document records the GitHub repository settings that must be enabled outside the codebase. These controls cannot be enforced by workflow files alone.

## Protect `main`

Create an active repository ruleset targeting the `main` branch with these controls:

- Require a pull request before merging.
- Require at least one approving review.
- Require review from CODEOWNERS for protected paths.
- Require all conversations to be resolved before merge.
- Require the `CI`, `CodeQL`, and `Dependency Review` checks to pass.
- Block force pushes.
- Block branch deletion.
- Prefer linear history where compatible with the team's merge workflow.

Do not allow bypassing these requirements for ordinary contributors. Keep administrator bypass limited to emergency repository administration.

## Security features

Enable GitHub's **Dependency graph** for the repository. Dependency Review depends on this feature being enabled.

Review and enable GitHub Advanced Security features appropriate to the repository's visibility and plan, including secret scanning and push protection where available.

## Validation

After changing repository settings, verify that:

1. A direct push to `main` is rejected for contributors without bypass permission.
2. A pull request cannot merge until required status checks succeed.
3. A pull request touching protected paths requires CODEOWNERS approval.
4. Dependency Review runs successfully after the Dependency graph is enabled.

These are repository-owner settings and intentionally are not represented as application configuration or workflow logic.
