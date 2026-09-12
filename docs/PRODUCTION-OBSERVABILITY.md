# Production Observability Runbook

This checklist covers the operational controls that should be in place before treating the chat server as a production service.

## Logs

- Centralize server and database logs in a managed log platform.
- Keep structured fields such as timestamp, level, event type, user identifier when safe, connection outcome, and request category.
- Never export passwords, session tokens, database credentials, private keys, or full authentication payloads.
- Retain logs according to the deployment's privacy and compliance requirements.
- Preserve enough context to investigate authentication failures, rate-limit events, attachment operations, database failures, and server lifecycle events.

## Metrics

Track at minimum:

| Area | Signals |
| --- | --- |
| Availability | readiness state, uptime, restart count, failed startups |
| Connections | active connections, accepted connections, rejected/rate-limited connections |
| Authentication | login attempts, authentication failures, registrations, duplicate-session rejections |
| Messaging | private/group messages accepted, delivery/read events, failed sends |
| Rate limiting | throttled login, registration, search, upload, download, PING, and connection attempts |
| Attachments | upload/download counts, rejected uploads, storage failures, bytes transferred |
| Database | connection-pool exhaustion, failed queries, validation failures, latency |
| JVM | heap usage, garbage-collection pressure, CPU, thread/virtual-thread pressure |

Prefer counters, gauges, histograms, and rates over high-cardinality labels. Do not use message text, email addresses, IP addresses, or session tokens as metric labels.

## Alerts

Create actionable alerts for:

- readiness or availability failures
- repeated startup failures
- sustained authentication-failure spikes
- abnormal connection churn
- sustained rate-limit spikes
- database pool exhaustion or persistent database errors
- attachment-storage failures
- JVM memory pressure or repeated out-of-memory exits
- unusual error-rate increases

Every alert should have an owner, severity, runbook link, and a defined escalation path.

## Audit trail

Record security-relevant events in a tamper-resistant destination where practical, including account registration, successful/failed authentication, session replacement, authorization failures, attachment access, administrative group changes, and security-control changes.

Avoid storing message content or other unnecessary personal data in audit events.

## Deployment checks

Before promotion, verify:

- application TLS is enabled outside trusted local development
- database TLS is enabled where required
- secrets come from an external secret manager
- database credentials are rotated on a defined schedule
- attachment storage is durable and backed up
- backups have a tested restore procedure
- container image identity is pinned by digest
- release JAR and SBOM checksums have been verified
- build-provenance attestations are retained
- dependency and security scans are passing

## Incident response

For a suspected security incident:

1. Preserve relevant logs, audit events, release metadata, and container digests.
2. Rotate potentially exposed application and database credentials.
3. Revoke affected sessions where the incident could expose account access.
4. Isolate compromised workloads or storage locations as appropriate.
5. Record the affected release, commit SHA, image digest, and deployment environment.
6. Run post-incident review and add regression tests or monitoring for the failure mode.

## Phase 7 completion criteria

Phase 7 is complete when centralized logs, metrics, alerts, managed attachment storage, certificate lifecycle automation, malware/content scanning, and independent security testing are implemented and exercised in the target deployment environment.
