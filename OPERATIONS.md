# Operations Runbook

This runbook covers the containerized deployment of the Real-Time Chat Application. It is intended for operators running the demo stack and for use as a checklist when moving to managed production infrastructure.

## Runtime model

The Compose stack contains two services:

- `server` — Java 21 chat server, exposed on TCP port 5050.
- `db` — MySQL 8.4, reachable only on the internal Compose network.

The server container runs as UID 10001, uses a read-only root filesystem, drops Linux capabilities, limits CPU/memory/PIDs, and stores attachments in a named writable volume.

The default published address is `127.0.0.1`. Set `CHATAPP_PUBLISH_ADDRESS` explicitly when remote clients are intentionally required.

## Start and verify

Set the required secrets before starting the stack:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='change-this-root-secret'
export CHATAPP_DB_PASSWORD='change-this-app-secret'
```

Start the stack:

```bash
docker compose up --build -d
```

Check service state:

```bash
docker compose ps
```

Both services should report healthy before clients connect.

## Health and readiness

The database healthcheck verifies that MySQL is accepting connections.

The server healthcheck verifies the readiness marker created by the application after successful database validation and server startup:

```text
/tmp/chatapp.ready
```

A server container that restarts without becoming healthy should be investigated before sending application traffic to it.

## Logs

Follow server logs:

```bash
docker compose logs -f --no-color server
```

Follow database logs:

```bash
docker compose logs -f --no-color db
```

Compose rotates both service logs at 10 MiB per file with three files retained. For production, forward logs to an external logging platform rather than relying on local container storage alone.

## Basic incident checks

### Server is unhealthy

```bash
docker compose ps
docker compose logs --no-color server
```

Confirm that:

1. MySQL is healthy.
2. The configured DB credentials are correct.
3. The attachment volume is writable by the application UID.
4. Port 5050 is not blocked by the host firewall or another process.

### Database is unhealthy

```bash
docker compose logs --no-color db
```

Verify the database volume is mounted and the configured root/application secrets are still available.

Do not delete the `chatapp-mysql` volume during incident response unless data loss is explicitly intended.

## Configuration and secrets

Secrets must be supplied through environment variables or an external secret manager. Never commit:

- `.env` files
- `config.properties`
- private keys or certificates
- PKCS12/JKS keystores

The repository CI checks for tracked secret material and sensitive files in the Docker build context.

## TLS

Application TLS is optional in local development and should be enabled for deployments where traffic crosses an untrusted network.

The server expects a PKCS12 keystore. The desktop client can use the JVM trust store or a dedicated PKCS12 trust store for a private CA.

Production certificate operations should include issuance, renewal, revocation, expiry monitoring, and private-key rotation.

For MySQL connections, use TLS in production where the database traffic traverses a network boundary. The Compose demo stack intentionally keeps its database connection on the internal Docker network and does not expose MySQL to the host.

## Backups

The MySQL named volume contains application state and must be backed up independently of the application container lifecycle.

For production:

- use managed MySQL or an equivalent durable database service
- enable automated point-in-time recovery where available
- test restoration regularly
- keep backup credentials separate from application credentials
- store attachment data in durable object storage rather than only in the local Docker volume

## Safe shutdown

Stop the stack with:

```bash
docker compose down
```

Do not use `docker compose down -v` during normal operations because that removes named volumes and can delete database and attachment data.

## CI and dependency analysis

GitHub Dependency Review requires the repository **Dependency Graph** to be enabled under the repository's security settings. Keep Dependency Review enabled and configured to fail on high-severity dependency changes; do not bypass a failed review because the graph is unavailable.

After enabling Dependency Graph, rerun or update pending Dependabot pull requests so Dependency Review can analyze their dependency changes.

## Release verification

Tagged releases run automated verification before publishing:

- Maven build and tests
- runnable server JAR validation
- CycloneDX SBOM validation
- SHA-256 checksums
- Docker Compose policy validation
- container metadata and non-root checks
- container entrypoint, port, healthcheck, filesystem, and JVM safety checks
- published image digest verification
- build provenance attestations

Treat the immutable image digest published with the release as the preferred deployment reference. Tags such as `latest` are convenience aliases and should not be the sole deployment identifier.

## Production hardening still required

This repository is not a security-audited production service. Before a real internet-facing deployment, add centralized monitoring and alerting, managed secrets, database TLS and least-privilege database accounts, durable object storage, certificate lifecycle automation, upload malware/content scanning, vulnerability management, network ingress controls, and independent security testing.
