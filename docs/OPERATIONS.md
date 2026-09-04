# Operations Runbook

This runbook covers the containerized deployment of the Real-Time Chat Application.

## Deployment prerequisites

Use JDK/Maven directly for local development, or Docker Compose for the packaged server + MySQL stack.

Before starting the Compose stack, provide secrets through the environment:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='strong-root-secret'
export CHATAPP_DB_PASSWORD='strong-app-secret'
```

Do not commit these values, local config files, keystores, certificates, or private keys.

## Start and stop

Build and start the stack:

```bash
docker compose up --build -d
```

Check service state:

```bash
docker compose ps
```

Stop the services without deleting named volumes:

```bash
docker compose down
```

The named volumes contain MySQL data and attachment bytes. `docker compose down -v` deletes those volumes and must be treated as destructive.

## Health and readiness

The server writes `/tmp/chatapp.ready` only after its database connection has been validated and the TCP listener has started. The container healthcheck uses this readiness marker.

Inspect health details:

```bash
docker inspect "$(docker compose ps -q server)" \
  | jq '.[0].State.Health'
```

The MySQL service has its own healthcheck. The server waits for the database service to become healthy before startup.

## Logs

Follow server logs:

```bash
docker compose logs -f server
```

Follow database logs:

```bash
docker compose logs -f db
```

Compose limits both services to three JSON log files of 10 MB each to prevent unbounded host disk usage.

## Configuration

The recommended deployment mechanism is environment or JVM overrides rather than committing a configuration file.

Common server settings include:

```text
CHATAPP_DB_HOST
CHATAPP_DB_PORT
CHATAPP_DB_NAME
CHATAPP_DB_USER
CHATAPP_DB_PASSWORD
CHATAPP_DB_USESSL
CHATAPP_DB_ALLOWPUBLICKEYRETRIEVAL
CHATAPP_SERVER_PORT
CHATAPP_SERVER_BINDADDRESS
CHATAPP_SERVER_MAXCLIENTS
CHATAPP_SERVER_SOCKETREADTIMEOUTMS
CHATAPP_AUTH_BCRYPT_STRENGTH
CHATAPP_AUTH_SESSION_EXPIRYHOURS
CHATAPP_ATTACHMENTS_STORAGEPATH
```

Configuration precedence is JVM system property, then environment variable, then config file.

## TLS

For deployments outside a trusted local network, enable application TLS and configure the server keystore and client trust settings. Keep private keys and keystore passwords outside Git.

At the database layer, use TLS where the database is remote or the network is not otherwise trusted. The development Compose stack intentionally uses an internal Docker network and is not a substitute for production database hardening.

## Storage and backups

MySQL data is stored in the `chatapp-mysql` named volume in the Compose deployment. Attachment bytes are stored separately in `chatapp-attachments`.

A production deployment should back up both application metadata and attachment data, test restores, and use durable external storage for attachments rather than relying on a single local Docker volume.

Do not delete volumes as part of routine troubleshooting. Confirm backup/restore coverage before destructive maintenance.

## Upgrade procedure

1. Review the release notes and compatibility impact.
2. Ensure database and attachment backups are current.
3. Pull the intended source/tag or release artifact.
4. Rebuild and start the stack with `docker compose up --build -d`.
5. Confirm both services are healthy with `docker compose ps`.
6. Confirm the server TCP port is reachable and inspect server logs for startup errors.
7. Validate a representative login and private-message flow.

## Rollback procedure

For a bad application release, redeploy the previous known-good Git tag and rebuild the server image from that exact revision. Preserve the database and attachment volumes unless the release explicitly includes an incompatible schema migration with a documented rollback plan.

Never use `docker compose down -v` as a generic rollback step.

## Incident checks

When the server is unhealthy, inspect:

```bash
docker compose ps
docker compose logs --no-color server db
```

Then verify:

```bash
docker inspect "$(docker compose ps -q server)" \
  | jq '.[0].HostConfig.ReadonlyRootfs, .[0].HostConfig.CapDrop, .[0].HostConfig.SecurityOpt'
```

The server container is designed to run as the non-root `chatapp` user, use a read-only root filesystem, drop Linux capabilities, and retain only the writable attachment volume.

## Production gaps

This project is hardened for a portfolio/learning deployment but is not a security-audited managed service. Production operations still require certificate lifecycle management, centralized secrets, monitoring/alerting, durable storage, database backup automation, malware/content scanning for uploaded files, threat modeling, and external security testing.
