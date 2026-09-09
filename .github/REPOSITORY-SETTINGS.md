# GitHub Repository Settings

This file records the repository-level controls that cannot be enforced by files in the repository alone.

## Dependency Graph

Enable **Dependency graph** in GitHub repository **Settings → Security → Advanced**.

This is required for the existing Dependency Review workflow to analyze dependency changes. Do not weaken or remove the workflow to bypass a disabled Dependency Graph.

## Protected `main` branch

Create an active ruleset targeting `main` with at least:

- Require pull requests before merging.
- Require the CI, CodeQL, and Dependency Review checks to pass.
- Require CODEOWNERS review for protected paths.
- Require conversation resolution before merge.
- Block force pushes and branch deletion.

Linear history should be enabled when compatible with the team's merge workflow.

## Repository-side enforcement

The repository already contains workflow checks, immutable GitHub Action references, CODEOWNERS coverage, container security policy checks, dependency review, and automated build/security validation. These controls complement the GitHub settings above; they do not replace server-side branch protection.
