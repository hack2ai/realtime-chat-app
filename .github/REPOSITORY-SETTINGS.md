# GitHub Repository Settings

This file records the repository-level controls that cannot be enforced by files in the repository alone.

## Dependency Graph

Enable **Dependency graph** in GitHub repository **Settings → Security → Advanced**.

This is required for the existing Dependency Review workflow to analyze dependency changes. Do not weaken or remove the workflow to bypass a disabled Dependency Graph.

Also enable **Dependabot alerts** so newly disclosed dependency vulnerabilities are surfaced even when no pull request has been created yet.

## Secret Protection

Enable **Secret scanning** and **Push protection** in **Settings → Security → Advanced**.

Push protection should block supported secrets before they enter Git history. Keep this enabled alongside the repository's CI checks for tracked-secret and container-secret detection.

## Protected `main` branch

Create an active ruleset targeting `main` with at least:

- Require pull requests before merging.
- Require the CI, CodeQL, and Dependency Review checks to pass.
- Require CODEOWNERS review for protected paths.
- Require conversation resolution before merge.
- Block force pushes and branch deletion.

Linear history should be enabled when compatible with the team's merge workflow.

## Release Tags

Protect release tags matching `v*.*.*` from unauthorized updates or deletion.

Release automation treats a semantic-version tag as the release identity, verifies the tag commit, and publishes immutable provenance data for the generated artifacts and container image. Tag protection should prevent an existing release tag from being moved after publication.

## Pull Requests

Require at least one approving review for protected branches and dismiss stale approvals when new commits change the reviewed code. Enable automatic updates for required status checks so merges cannot bypass a newly introduced failing validation.

## Repository-side enforcement

The repository already contains workflow checks, immutable GitHub Action references, CODEOWNERS coverage, container security policy checks, dependency review, secret-material checks, artifact checksums, SBOM validation, image provenance, and automated build/security validation. These controls complement the GitHub settings above; they do not replace server-side branch protection or organization-level security policy.
