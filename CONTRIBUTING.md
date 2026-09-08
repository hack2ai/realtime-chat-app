# Contributing

Thanks for contributing to Real-Time Chat Application.

## Development setup

1. Install JDK 21 and Maven 3.8+.
2. Create a local MySQL 8 database using `src/main/resources/sql/schema.sql`.
3. Copy `src/main/resources/config.properties.example` to `config.properties` and configure local credentials.
4. Run `mvn verify` before opening a pull request.

## Pull requests

- Keep changes focused and explain the problem they solve.
- Add or update tests for behavior changes.
- Do not commit credentials, private keys, local configuration, or generated build output.
- Keep protocol and database changes backward-compatible where practical; document breaking changes.
- Prefer small, reviewable commits and clear commit messages.

## Main branch protection

The `main` branch should be governed by a GitHub branch protection rule or repository ruleset. The required policy is:

- Changes land through pull requests; direct pushes to `main` are disabled.
- Required status checks include the CI build, CodeQL analysis, and Dependency Review where the event applies.
- Force pushes and branch deletion are disabled.
- Required pull-request conversation resolution is enabled.
- At least one approving review is required for changes from other contributors.

The repository automation verifies the code, dependency, container, packaging, and security invariants in CI, but those GitHub repository settings must still be enforced at the repository level.

## Code quality

Use Java 21 features where they improve clarity, but avoid unnecessary framework or dependency additions. Keep networking, business logic, persistence, and configuration concerns separated.

## Security

If you discover a potential security issue, please follow the process in `SECURITY.md` rather than opening a public issue with exploit details.
