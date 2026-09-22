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

The server should become healthy only after the database is available and the application creates its readiness marker.

Inspect recent logs when startup fails:

```bash
docker compose logs --no-color --tail=200 db server
```

## Health verification

Confirm the server is listening on TCP port `5050` and that both Compose health checks are healthy:

```bash
docker compose ps
```

For a lightweight application-level check, the protocol supports a `PING` request and `PONG` response. The CI smoke test exercises this framed TCP request against the running Compose deployment.

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

The server validates the certificate chain presented in its key entries at startup. It also warns when the earliest server certificate is within the configured renewal window:

```properties
tls.certificateExpiryWarningDays=30
```

The setting accepts `0` through `3650` days. Use a warning window that matches the organization's certificate renewal SLA; the default is 30 days.

Before enabling TLS in production, verify certificate validity, hostname expectations, trust-chain distribution, and an operational certificate renewal process.

## Attachments

Attachments are stored separately from MySQL metadata. The containerized server uses `/app/data/attachments` as its writable application-data directory.

For production, use durable object storage with an appropriate backup and retention policy rather than relying on a single local container volume.

Do not expose attachment storage directly to the public internet. Access should continue to flow through the authenticated application path.

## Database backup

Back up the MySQL database independently from the application container and attachment store. A logical backup example is:

```bash
mysqldump --single-transaction -u chatapp_user -p chatapp_db > chatapp_db.sql
```

Store backups outside the application host, protect them as sensitive data, and periodically test restoration into an isolated environment.

## Restore sequence

A practical recovery sequence is:

1. Provision the database and restore the MySQL backup.
2. Restore the attachment data or reconnect the configured durable object store.
3. Supply the required secrets and TLS material.
4. Start the server deployment.
5. Confirm database and server health checks.
6. Exercise the application `PING` path and review logs for startup errors.
7. Verify representative authentication, messaging, group, and attachment operations.

## Release verification

Tagged releases publish a server JAR, checksum, Java dependency SBOM, and a container image. Verify the checksum before distributing a release JAR:

```bash
sha256sum --check chatapp-server-v1.1.0.jar.sha256
```

The CI and release workflows also validate the runnable server manifest, image metadata, image hardening properties, and container vulnerabilities before publication.

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
