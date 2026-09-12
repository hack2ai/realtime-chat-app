# Deployment Security

This project treats the container and deployment configuration as part of the security boundary. The checks below describe the controls currently enforced by the repository and the expectations for a production deployment.

## Container runtime

The server image runs as the dedicated `chatapp` user with UID/GID `10001`. The application JAR is owned by root and mounted read-only inside the image. The image uses a dedicated attachment volume at `/app/data/attachments`, exposes TCP port `5050`, sends `SIGTERM` on shutdown, and includes a Java-based healthcheck.

Docker Compose reinforces the image defaults with a read-only root filesystem, a `noexec`/`nosuid` `/tmp` tmpfs, dropped Linux capabilities, `no-new-privileges`, PID limits, CPU and memory limits, file-descriptor limits, graceful stop windows, and bounded JSON-file log rotation.

## Secrets

Database credentials are supplied to Compose through Docker secrets backed by environment variables. Do not commit passwords, keystores, private keys, `.env` files, or local `config.properties` files.

The server reads the database password from its configured secret file in the Compose deployment. Keep application TLS private keys and trust material outside Git and outside the container image.

## Network exposure

The Compose backend network is internal, and MySQL has no published host port. The chat server is published on the configured host address and port. For a production deployment, use application TLS for remote clients rather than relying on network isolation alone.

The development/demo Compose stack is not a substitute for a managed production database. Production environments should use encrypted database transport where supported, restricted database credentials, durable backups, monitored storage, and an external secret-management system.

## CI enforcement

GitHub Actions validates the deployment boundary before artifacts are accepted. Current checks include immutable action references, Docker/Compose security policy validation, container vulnerability/misconfiguration/secret scanning, non-root execution, Java runtime validation, entrypoint and exposed-port validation, image healthcheck validation, filesystem policy checks, and verification that release artifacts contain no local secret material.

The server JAR and SBOM are checksum-verified, and tagged releases also receive build-provenance attestations.

## Production checklist

Before exposing the service to the Internet, configure application TLS with a managed certificate lifecycle, rotate all database and application secrets, use a managed MySQL deployment or equivalent hardened database, move attachment storage to durable object storage, enable centralized monitoring and alerting, and perform an external security assessment.

Never treat the demo Compose credentials or development TLS material as production secrets.
