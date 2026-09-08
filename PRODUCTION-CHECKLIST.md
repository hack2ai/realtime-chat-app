# Production Deployment Checklist

Use this checklist before exposing the chat server to real users. The repository's container and CI controls reduce common risks, but they do not replace infrastructure hardening, secret management, monitoring, or an external security review.

## Application and transport

- [ ] Run the server on a supported JDK 21 runtime.
- [ ] Enable application TLS for every untrusted network path.
- [ ] Use a certificate issued by a trusted CA and automate certificate renewal.
- [ ] Keep private keys and keystores outside Git and outside container build contexts.
- [ ] Configure database TLS where the database connection crosses a trust boundary.
- [ ] Keep database public-key retrieval disabled unless there is a documented reason to enable it.
- [ ] Keep the server bound to the intended private interface unless a public listener is explicitly required.

## Secrets and identity

- [ ] Store database passwords, TLS passwords, and other secrets in an external secret manager.
- [ ] Rotate application and database credentials on a defined schedule.
- [ ] Do not ship default application accounts or passwords.
- [ ] Use unique credentials for development, staging, and production.

## Database

- [ ] Prefer managed MySQL for production.
- [ ] Create a dedicated application database user with only the privileges required by the schema and application.
- [ ] Enable automated backups and test restoration regularly.
- [ ] Monitor storage, connections, slow queries, replication health, and failed authentication attempts.

## Attachments and data

- [ ] Replace local attachment storage with durable object storage for production deployments.
- [ ] Apply object-storage encryption, retention, and lifecycle rules.
- [ ] Keep attachment metadata and object access authorization consistent.
- [ ] Add malware/content scanning before making uploaded files available to recipients.
- [ ] Define retention and deletion requirements for messages, attachments, and account data.

## Container and host

- [ ] Run the server image as the non-root `chatapp` user.
- [ ] Keep the container root filesystem read-only and provide write access only to the attachment volume and required temporary storage.
- [ ] Keep Linux capabilities dropped unless a capability is explicitly required.
- [ ] Keep `no-new-privileges` enabled.
- [ ] Keep CPU, memory, process, and file-descriptor limits appropriate for the host capacity.
- [ ] Keep database and server services on private networks; never expose the database port publicly.
- [ ] Prefer immutable image references or verified registry digests for production rollouts.
- [ ] Restrict host firewall rules to the network paths the service actually needs.
- [ ] Patch the container base images and host OS on a defined maintenance schedule.

## Observability and operations

- [ ] Collect application logs centrally with retention and access controls.
- [ ] Monitor authentication failures, rate-limit rejections, connection churn, protocol errors, database health, and resource saturation.
- [ ] Alert on server health-check failures and unexpected restart loops.
- [ ] Test graceful shutdown and restart behavior during maintenance.
- [ ] Document incident-response, backup-restore, certificate-renewal, and credential-rotation procedures.

## Release integrity

- [ ] Require CI to pass before production releases.
- [ ] Review the generated SBOM for each release.
- [ ] Verify artifact checksums before distribution.
- [ ] Verify build provenance attestations in the release process.
- [ ] Verify the published container digest before rollout.
- [ ] Keep dependency automation enabled for Maven, GitHub Actions, and container base images.
- [ ] Run external security testing before treating the service as production-grade.

## Final gate

Production readiness is not complete until the operational owner has accepted the remaining risks, backup restoration has been tested, secret and certificate rotation has been exercised, and an external security assessment has been completed.
