# GitHub Security Setup

This document covers repository-level GitHub controls that cannot be enforced by files in the repository alone.

## Dependency Graph

Enable **Dependency graph** in:

**Repository → Settings → Security → Advanced Security → Dependency graph**

Dependency Review requires the repository Dependency Graph to be enabled. After enabling it, rerun or update any pending dependency-review workflow runs.

## Main branch ruleset

Create an active ruleset for `main` in:

**Repository → Settings → Rules → Rulesets**

Recommended protections:

- Require a pull request before merging.
- Require the repository CI, CodeQL, and Dependency Review checks to pass.
- Require CODEOWNERS approval for protected paths.
- Require conversation resolution before merging.
- Block force pushes and branch deletion.
- Require linear history where compatible with the team's merge workflow.

The repository already contains workflow checks and `CODEOWNERS` coverage, but those controls do not by themselves prevent a direct push to `main`.

## Verification

After applying the settings, confirm that:

1. Dependency Review completes successfully on dependency-change pull requests.
2. Direct pushes to `main` are rejected unless allowed by the ruleset.
3. Pull requests cannot merge while required checks are failing.
4. Required CODEOWNERS approvals are enforced by GitHub.

These settings are intentionally kept outside the repository because they are GitHub account/repository administration controls rather than application source configuration.
