# Operations Runbook

This runbook covers the deployment and operational checks for the Real-Time Chat Application.

## Deployment preflight

Before starting a deployment, verify:

```bash
java -version
mvn --version
docker version
docker compose version
```

Use JDK 21 for the application build. For container deployments, supply secrets through the environment or an external secret manager rather than committing them to the repository.

Required Compose secrets:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='use-a-strong-secret'
export CHATAPP_DB_PASSWORD='use-a-different-strong-secret'
```

Review the intended configuration before startup:

```bash
docker compose config
```

## Start and health checks

Start the stack with:

```bash
docker compose up --build -d
```

Check service state:

```bash
docker compose ps
```

The database must become healthy before the application server starts. The server readiness marker is used by the container healthcheck and is only created after startup has completed its database readiness validation.

Check recent logs without following indefinitely:

```bash
docker compose logs --no-color --tail=200 db server
```

The Compose configuration limits container log growth. Do not disable the configured log rotation on a long-running host without replacing it with an equivalent retention policy.

## TLS

Application TLS is configurable through the server keystore and client trust-store settings described in the main README. For non-local deployments, use certificates managed by an appropriate certificate authority and keep private keys outside Git.

After a certificate change, verify both sides use compatible trust configuration before directing users to the new endpoint.

## Database operations

The application schema is initialized from:

```text
src/main/resources/sql/schema.sql
```

Production deployments should use managed MySQL or an equivalently hardened database service with encrypted transport where appropriate, restricted application credentials, backups, and tested restore procedures.

Before schema changes, take a verified backup. After a restore, confirm that users, group memberships, messages, and attachment metadata are present before reopening application traffic.

## Attachment storage

The application stores attachment bytes separately from message metadata. Treat the attachment volume as production data.

Back up both:

1. MySQL data, including attachment metadata.
2. The attachment storage volume or its replacement object storage.

A database-only backup is not sufficient to reconstruct uploaded files.

For production-scale deployments, prefer durable object storage and retain only the required metadata in MySQL.

## Release verification

Tagged releases are built and validated by GitHub Actions. Release artifacts include the runnable server JAR, SHA-256 checksum, and SBOM.

After a release is published, verify the checksums before distributing an artifact:

```bash
sha256sum --check chatapp-server-vX.Y.Z.jar.sha256
```

Use the image digest, rather than a mutable tag, when an external deployment system supports immutable image references.

## Incident response

When investigating a suspected application or infrastructure incident:

1. Preserve relevant application and container logs before restarting services.
2. Record the affected release tag and container image digest.
3. Rotate exposed application/database credentials and revoke compromised credentials.
4. Preserve database and attachment backups needed for investigation.
5. Disable or isolate affected endpoints when necessary.
6. Rebuild from a known-good commit and redeploy only after the incident scope is understood.

Do not delete evidence, overwrite backups, or rotate away credentials needed for investigation until the response process has preserved the required records.

## Recovery checklist

After a recovery deployment, confirm:

```bash
docker compose ps
docker compose logs --no-color --tail=100 db server
```

Then verify that:

- the database is healthy;
- the server is healthy and listening on the expected port;
- authentication works;
- private messaging works;
- group membership and messaging work;
- attachment upload/download works and integrity verification succeeds;
- recent message history is available;
- no unexpected secret files were introduced into the container image.

## Security reporting

Do not publish credentials, private keys, tokens, or sensitive logs in GitHub issues or pull requests. Follow the repository guidance in [SECURITY.md](../SECURITY.md) for vulnerability reporting.
