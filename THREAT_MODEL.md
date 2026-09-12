# Application Threat Model

## Purpose

This document records the primary security assumptions, assets, trust boundaries, threats, and residual risks for the Real-Time Chat Application. It complements [SECURITY.md](SECURITY.md) and is intentionally scoped to the supplied application and deployment configuration.

## Assets

| Asset | Security property | Primary protection |
|---|---|---|
| User credentials | Confidentiality | BCrypt hashing; password validation; no default credentials |
| Session tokens | Confidentiality and integrity | Cryptographically random tokens; hashed in-memory token lookup; expiry |
| Private messages | Confidentiality and integrity | Authentication; participant authorization; prepared SQL |
| Group messages and membership | Authorization and integrity | Authenticated membership checks; owner/admin controls |
| Attachment bytes | Confidentiality and integrity | Participant authorization; size/type validation; SHA-256 verification; isolated storage |
| Database contents | Confidentiality, integrity, availability | Dedicated account; parameterized SQL; container/network controls |
| TLS private keys | Confidentiality | External/private configuration; never committed to Git |
| Release artifacts | Integrity and provenance | SHA-256 checksums; SBOM; build provenance attestations; immutable image digest |

## Trust Boundaries

1. **Internet or local network ↔ TCP server**: all client input is untrusted until protocol, authentication, authorization, and validation checks succeed.
2. **Client handler ↔ application services**: handlers are transport-facing and must not bypass service-level authorization.
3. **Application ↔ MySQL**: credentials and query parameters cross a database boundary; SQL must remain parameterized.
4. **Application ↔ attachment storage**: filenames, IDs, and file contents are untrusted and must not permit path traversal or unauthorized access.
5. **Build runner ↔ repository/dependencies**: source, third-party dependencies, GitHub Actions, and container bases are part of the software supply chain.
6. **Release workflow ↔ registries/GitHub Releases**: publishing credentials and generated artifacts must be limited to the release job and verified before publication.

## Threats and Mitigations

### Network abuse

**Threats:** connection floods, request floods, oversized frames, malformed protocol messages, and slow clients can exhaust threads, memory, or sockets.

**Mitigations:** bounded handler execution, maximum frame size, socket read timeouts, connection/request rate limiting, protocol-error caps, and client-count limits.

**Residual risk:** a large distributed attack can still exhaust the host or upstream network. Production deployments need network-layer rate limiting and DDoS protection.

### Credential attacks

**Threats:** password guessing, account enumeration, credential leakage, and offline cracking after database compromise.

**Mitigations:** BCrypt password hashing, generic authentication failures, login/registration throttling, UTF-8-aware bcrypt length enforcement, secret-file support, and no seeded application credentials.

**Residual risk:** passwords remain a user-managed secret. Production systems should consider MFA, breached-password detection, and stronger account recovery controls.

### Session abuse

**Threats:** token guessing, replay of stolen tokens, concurrent-session confusion, and stale sessions.

**Mitigations:** 256-bit cryptographically random tokens, hashed in-memory token lookup, token expiry, and one active session per account.

**Residual risk:** theft of a live session token can enable impersonation until expiry or explicit logout. Production deployments should use secure credential storage and consider token rotation/revocation infrastructure.

### Authorization bypass

**Threats:** reading or modifying another user's private messages, accessing group data without membership, or downloading attachments without participation in the conversation.

**Mitigations:** server-side authorization checks at the service/DAO boundary, authenticated recipient validation, group membership checks, and attachment participant checks.

**Residual risk:** authorization correctness depends on every new feature following the same service-layer pattern. Security regression tests and review are required for future protocol additions.

### Malicious uploads

**Threats:** path traversal, executable uploads, deceptive file types, malformed archives, storage exhaustion, and malicious content.

**Mitigations:** generated attachment IDs, sanitized filenames, safe path resolution, size limits, MIME/magic validation, blocked executable extensions, archive entry-name checks, SHA-256 integrity validation, and isolated attachment storage.

**Residual risk:** file type validation is not malware detection. Production deployments need malware/content scanning and preferably object-storage isolation before files are made available to clients.

### Database compromise

**Threats:** SQL injection, excessive database privileges, exposed database listener, and credential disclosure.

**Mitigations:** prepared statements, dedicated application credentials, Docker Compose internal networking, no direct database port publishing, and secret-file support.

**Residual risk:** the supplied demo Compose database is not a managed production database. Production deployments should enforce least-privilege database grants, database TLS where appropriate, backups, monitoring, and credential rotation.

### Transport interception

**Threats:** plaintext TCP interception or active network modification when TLS is disabled.

**Mitigations:** configurable TLS with constrained protocol versions and client endpoint identification; documentation instructs operators to enable TLS outside trusted local development.

**Residual risk:** certificate issuance, rotation, trust-store management, and revocation remain deployment responsibilities.

### Supply-chain compromise

**Threats:** mutable GitHub Actions references, compromised dependencies, malicious container bases, or tampered release artifacts/images.

**Mitigations:** immutable action commit pins, Dependabot updates, dependency review, Docker policy checks, SBOM generation, SHA-256 checksums, build provenance attestations, and release image digest verification.

**Residual risk:** pinned dependencies can still contain vulnerabilities or become compromised upstream. Independent dependency review and timely upgrades remain necessary.

### Container escape or excessive privileges

**Threats:** a compromised server process attempts to gain host-level access or consume excessive resources.

**Mitigations:** non-root runtime user, fixed UID, dropped capabilities, `no-new-privileges`, read-only server filesystem, bounded PIDs/CPU/memory, restricted temporary storage, and bounded container logs.

**Residual risk:** container isolation is not a complete security boundary. Production environments should use hardened runtimes, host patching, restrictive network policy, and runtime monitoring.

## Out of Scope

This model does not claim to cover:

- physical security of hosts
- cloud-provider account compromise
- malicious maintainers with legitimate repository write access
- full TLS PKI lifecycle design
- formal cryptographic protocol verification
- client endpoint malware or compromised operating systems
- complete MySQL hardening for a specific production platform

## Security Review Guidance

Any new protocol message, externally reachable service, stored field, attachment behavior, authentication flow, or deployment privilege should be reviewed against this document and [SECURITY.md](SECURITY.md). Security-sensitive changes should include regression tests and should preserve the existing CI, dependency-review, container-policy, and provenance checks.