# Deployment Guide

This document describes the supported container deployment path for the Real-Time Chat Application.

## Docker Compose

The repository includes a server + MySQL Compose stack intended for development and demo deployments.

Required environment variables:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='use-a-strong-random-secret'
export CHATAPP_DB_PASSWORD='use-a-different-strong-random-secret'
```

Start the stack:

```bash
docker compose up --build -d
```

The server is published on TCP port `5050`. The database is not published to the host; it is reachable only on the internal Compose network.

Check service state:

```bash
docker compose ps
docker compose logs --tail=200 server
docker compose logs --tail=200 db
```

Stop the stack:

```bash
docker compose down
```

Do not use `docker compose down -v` on a deployment unless deleting the MySQL and attachment volumes is intentional.

## Persistent data

Two named volumes are used:

- `chatapp-mysql` stores MySQL data.
- `chatapp-attachments` stores uploaded attachment bytes.

Back up both volumes according to your recovery requirements. Attachment files are separate from message metadata, so restoring the database without the matching attachment volume can leave attachment records without their underlying files.

## Server container security

The server image is designed to run as the non-root `chatapp` user (UID `10001`). The Compose service additionally uses a read-only root filesystem, drops Linux capabilities, enables `no-new-privileges`, limits processes and file descriptors, and places temporary files in a bounded `/tmp` tmpfs.

The attachment directory is the writable application-data location. Keep it on the named volume and do not mount the host root filesystem into the container.

Container logs use bounded JSON-file rotation. Review logs centrally in a production environment rather than relying on indefinite local retention.

## Database

The Compose database account is the application runtime account. The CI deployment checks verify that the account is limited to the application schema and does not receive administrative privileges.

For production, prefer a managed MySQL service with:

- TLS for database connections where appropriate
- automated backups and point-in-time recovery
- credential rotation through an external secret manager
- monitoring and alerting

Do not expose MySQL directly to the public internet.

## TLS

Application TCP transport supports optional TLS. Production deployments should enable TLS and use a properly managed PKCS12 server keystore.

Configure the server with:

```properties
tls.enabled=true
tls.keyStorePath=/path/to/server-keystore.p12
tls.keyStorePassword=<secret>
```

Configure clients to trust the server certificate through the JVM CA store or an appropriate private trust store.

Never commit keystores, private keys, certificates containing private material, or passwords to Git.

## Releases

Semantic version tags use the `vMAJOR.MINOR.PATCH` format, for example:

```bash
git tag v1.1.0
git push origin v1.1.0
```

The release workflow verifies that the tag points to a commit contained in `main`, verifies the Maven build and server package, produces CycloneDX SBOMs, scans the container image for high/critical vulnerabilities with unfixed findings ignored, verifies image hardening, and publishes:

- versioned server JAR
- JAR SHA-256 checksum
- server SBOM and checksum
- container SBOM and checksum
- versioned, `latest`, and commit-addressed container tags
- build-provenance attestations

The workflow also verifies the digest of the published container tags and re-downloads release assets to validate their checksums.

## Upgrade procedure

Before upgrading:

1. Confirm the target release tag and review its release notes.
2. Back up the MySQL and attachment volumes.
3. Pull the new image or rebuild from the release commit.
4. Restart the stack with the new image.
5. Confirm the server healthcheck becomes healthy.
6. Verify application connectivity and attachment downloads.
7. Retain the previous image/tag until the upgrade is confirmed.

For rollback, restore the previously known-good server image/tag and application artifacts. Restore database or attachment backups only when the data itself must be reverted; an application rollback does not automatically require a database rollback.

## Production considerations

The included Compose deployment is not a substitute for production infrastructure. A production environment should add external secret management, managed MySQL, durable object storage for attachments, TLS certificate lifecycle automation, centralized logs and metrics, alerting, regular backups with restore tests, threat modeling, malware/content scanning for uploads, and independent security testing.
