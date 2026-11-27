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

## Code quality

Use Java 21 features where they improve clarity, but avoid unnecessary framework or dependency additions. Keep networking, business logic, persistence, and configuration concerns separated.

## Security

If you discover a potential security issue, please follow the process in `SECURITY.md` rather than opening a public issue with exploit details.
