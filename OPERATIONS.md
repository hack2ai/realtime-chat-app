# Production Operations Runbook

This runbook covers the operational basics for deploying, validating, backing up, and recovering the Real-Time Chat Application.

## Before deployment

Use JDK 21 and run the complete Maven verification suite:

```bash
mvn verify
```

For container deployment, provide the required secrets through the environment. Never commit them to the repository:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='replace-with-a-strong-root-secret'
export CHATAPP_DB_PASSWORD='replace-with-a-strong-app-secret'
```

The Compose stack is suitable for development/demo deployments. Production environments should use managed MySQL, durable attachment storage, an external secret manager, and application TLS.

## Start the Compose stack

```bash
docker compose up --build -d
```

Check service state:

```bash
docker compose ps
```

The server should become healthy only after the database is available and the application creates and refreshes its readiness heartbeat.

Inspect recent logs when startup fails:

```bash
docker compose logs --no-color --tail=200 db server
```

Container logs are configured with bounded rotation. Centralized production logging should still be used so operational history is not lost when rotated local files are discarded.

## Health verification

Confirm the database and server health checks are healthy:

```bash
docker compose ps
```

For a lightweight application-level check, the protocol supports a `PING` request and `PONG` response. CI exercises this framed TCP request against the running Compose deployment.

After deployment, also verify representative application flows:

- authentication with a non-privileged test account
- private messaging
- group operations when enabled
- attachment upload/download authorization
- TLS connectivity when TLS is enabled

Record the deployed application version and container image digest.

## Configuration and secrets

Runtime configuration can be supplied through JVM system properties, environment variables, or the local configuration file, with JVM properties taking precedence over environment variables and the configuration file.

Keep these values out of Git:

- database passwords
- TLS private keys and keystores
- trust-store passwords
- session or application secrets

Rotate database credentials and TLS material through the deployment environment rather than editing tracked files.

## TLS

Enable application TLS for deployments that cross an untrusted network. The server uses a PKCS12 keystore and the client can use the JVM default trust store or a configured private trust store.

The server validates the X.509 certificates present in its key entries at startup. It also warns when the earliest server certificate is within the configured renewal window:

```properties
tls.certificateExpiryWarningDays=30
```

The setting accepts `0` through `3650` days. Use a warning window that matches the organization's certificate renewal SLA.

Before enabling TLS in production, verify certificate validity, hostname expectations, trust-chain distribution, and an operational certificate renewal process.

After certificate rotation:

```bash
docker compose restart server
docker compose ps
docker compose logs --no-color --tail=100 server
```

Confirm that the new certificate is served and that existing trust-store configuration accepts it.

## Attachments

Attachments are stored separately from MySQL metadata. The containerized server uses `/app/data/attachments` as its writable application-data directory.

For production, use durable object storage with an appropriate backup and retention policy rather than relying on a single local container volume.

Do not expose attachment storage directly to the public internet. Access should continue to flow through the authenticated application path.

Before deleting old attachment data, confirm that the application database metadata and durable object-storage records are consistent.

## Database backup

Back up the MySQL database independently from the application container and attachment store. A logical backup example is:

```bash
mysqldump --single-transaction -u chatapp_user -p chatapp_db > chatapp_db.sql
```

Store backups outside the application host, protect them as sensitive data, and periodically test restoration into an isolated environment.

The application runtime account is intentionally restricted to CRUD operations on the application schema. Schema changes and privilege administration should use a separate administrative account.

## Restore sequence

A practical recovery sequence is:

1. Provision the database and restore the MySQL backup.
2. Restore attachment data or reconnect the configured durable object store.
3. Supply the required secrets and TLS material.
4. Start the server deployment.
5. Confirm database and server health checks.
6. Exercise the application `PING` path and review logs for startup errors.
7. Verify representative authentication, messaging, group, search, and attachment operations.
8. Record the recovered application version and container digest.

A backup should not be considered recoverable until a restoration test has succeeded.

## Release verification

Tagged releases use the `vMAJOR.MINOR.PATCH` format. The release workflow verifies that the tag points to a commit contained in `main` and that the tag version matches the Maven project version.

Published release assets include the server JAR, SHA-256 checksum, Java dependency SBOM, and container SBOM. The release pipeline also performs container vulnerability and hardening checks and publishes build provenance attestations.

Verify a release JAR before distributing it:

```bash
sha256sum --check chatapp-server-v1.1.0.jar.sha256
```

For container deployments, prefer immutable version or commit-SHA image references. Capture the published digest as part of the deployment record.

## Rollback

For an application rollback:

1. Identify the last known-good release and its published container digest.
2. Confirm database schema compatibility with the target application version.
3. Redeploy the immutable image reference or server artifact for that release.
4. Do not delete database or attachment volumes during an application rollback.
5. Re-run health and protocol smoke checks.
6. Verify authentication, messaging, group, and attachment flows.
7. Record the rollback reason, source version, target version, and resulting image digest.

Do not treat `latest` as the rollback identifier when an immutable release or commit-SHA reference is available.

## Operational monitoring

Watch for:

- repeated server restarts or failed health checks
- database connectivity failures
- authentication failures or unusual login-rate limiting
- connection-capacity rejections
- protocol-error spikes
- attachment-storage failures
- out-of-memory or process-limit failures
- TLS certificate-expiry warnings

The server periodically logs operational metrics. Centralize these logs in production and retain enough history to investigate capacity, availability, and security incidents.

## Incident response

For suspected application compromise or credential exposure:

1. Isolate the affected deployment from external traffic.
2. Rotate database credentials and other exposed secrets.
3. Preserve relevant application, database, and host logs before destructive cleanup.
4. Revoke or replace compromised TLS material.
5. Rebuild and redeploy from a verified Git commit.
6. Review authentication and attachment-access activity for unauthorized use.
7. Restore from known-good backups only after validating their integrity.

Do not delete evidence until the incident has been assessed.

## Shutdown

For a controlled Compose shutdown:

```bash
docker compose down
```

To remove the Compose volumes as well, use this only when data destruction is intentional:

```bash
docker compose down --volumes
```

Named volumes contain database and attachment data and should be treated as persistent production data.
