# Deployment Guide

This guide covers the supported deployment baseline for the Real-Time Chat Application and the security controls that should remain enabled.

## Recommended production architecture

Use the Java server as the only application-facing component. Put it behind a TLS-capable reverse proxy or load balancer when public exposure is required, keep MySQL private, and store attachment data on durable storage.

```text
Internet
   |
   | TLS
   v
Reverse proxy / load balancer
   |
   | TCP/TLS
   v
Chat server
   |
   +---- MySQL (private network)
   |
   +---- Durable attachment storage
```

The repository's Docker Compose stack is intended for development and demo environments. It binds the chat server to localhost by default, keeps the database off the host network, uses named volumes, and applies container resource and privilege restrictions.

## Secrets

Do not commit passwords, private keys, Java keystores, trust stores, `.env` files, or local configuration.

The Compose deployment reads these two required secrets from the host environment:

```bash
export CHATAPP_MYSQL_ROOT_PASSWORD='replace-me'
export CHATAPP_DB_PASSWORD='replace-me'
```

Start the stack with:

```bash
docker compose up --build -d
```

For production, use an external secret manager rather than shell-exported credentials.

## Transport security

For trusted local development, the server may run with plaintext TCP. Do not expose plaintext application traffic to an untrusted network.

For remote deployments, enable application TLS and use a certificate whose private key is stored outside Git. The Java client supports a configurable trust store for private certificate authorities.

Keep database TLS enabled whenever MySQL traffic can leave a trusted local host or private network. The demo Compose file intentionally uses the local development setting for its database connection.

## Container baseline

The server container is expected to retain these controls:

- Run as UID `10001` (`chatapp`), not root.
- Use a read-only root filesystem.
- Drop all Linux capabilities.
- Enable `no-new-privileges`.
- Use an init process for child reaping.
- Limit processes, memory, CPU, and open file descriptors.
- Mount only the attachment directory as writable application storage.
- Keep `/tmp` in a bounded, non-executable temporary filesystem.
- Use a healthcheck that validates the server's readiness.
- Rotate container logs so application output cannot consume unbounded disk space.
- Keep the MySQL service on the private Compose network without a host port publication.

Changes that weaken these controls should be treated as security-sensitive changes and reviewed accordingly.

## Database

For production:

1. Prefer managed MySQL or a separately hardened database host.
2. Give the application a dedicated database account with only the permissions it needs.
3. Enable encrypted database transport where appropriate.
4. Back up the database and regularly test restoration.
5. Monitor connection exhaustion, storage growth, and failed authentication attempts.
6. Never seed a default application password in the schema.

The schema is designed to be initialized without publishing an application credential.

## Attachments

The local attachment volume is appropriate for development and small demonstrations. Production deployments should use durable object storage with:

- server-side encryption;
- lifecycle and retention policies;
- access logging;
- malware/content scanning where required;
- independent backup and recovery controls.

Keep only attachment metadata in MySQL when an external object store is used.

## Health and shutdown behavior

The server exposes its health through the configured application healthcheck. A deployment should only route traffic to an instance after the healthcheck is passing.

Allow the server its configured graceful shutdown window during deployments. Do not use forced container termination as the normal rollout mechanism.

## Observability

Collect at least:

- server start/stop and readiness events;
- authentication failures and rate-limit events;
- active connection counts;
- database pool health;
- attachment upload/download failures;
- server error counts and latency;
- container health and restart counts;
- CPU, memory, and disk usage.

Do not put passwords, session tokens, private keys, or complete attachment contents into logs or telemetry.

## Release verification

Tagged releases should only be published after the repository's automated verification succeeds. The release workflow validates the Maven package, verifies the runnable server JAR and embedded revision, verifies the generated SBOM and checksums, validates Compose security policy, builds the release image, scans it for high/critical vulnerabilities and misconfigurations, and verifies image metadata and runtime hardening before pushing the image.

Use the immutable release tag as the deployment reference. Avoid deploying an unreviewed mutable `latest` tag directly to production.

## Operational checklist

Before exposing the service to the internet, verify that:

- TLS is enabled for application traffic.
- Database traffic is appropriately encrypted.
- Production secrets come from a managed secret store.
- MySQL is not publicly reachable.
- Attachment storage is durable and backed up.
- Monitoring and alerting are active.
- Log retention is configured.
- Database backups have been restore-tested.
- A rollback procedure exists for both the application image and database schema changes.
- Security testing has been performed against the deployment environment.

The project is not a security-audited production service. These controls are a baseline, not a substitute for deployment-specific threat modeling, penetration testing, compliance review, and incident response planning.
