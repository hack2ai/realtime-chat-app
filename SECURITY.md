# Security Policy

## Scope

This repository is a learning/portfolio real-time chat application. It is not a security-audited production service.

## Supported versions

Security fixes are targeted at the current `1.1.x` release line.

| Version | Supported |
| --- | --- |
| 1.1.x | Yes |
| < 1.1.0 | No |

Upgrade to the current release before reporting an issue that only affects an unsupported version.

## Reporting a vulnerability

Please do not publish credentials, exploit code, or sensitive details in a public issue. Contact the repository owner privately through the contact details on the GitHub profile and include:

- affected component or file
- impact and severity as you understand it
- reproduction steps or a minimal proof of concept
- any suggested mitigation

Please allow reasonable time for investigation and remediation before public disclosure.

## Security expectations

- Never commit `config.properties`, passwords, tokens, private keys, or other secrets.
- Never log passwords, session tokens, database credentials, private keys, or full authentication payloads.
- Use TLS before exposing the TCP server outside a trusted local network.
- Use a dedicated database account with the minimum required privileges.
- Rotate credentials if they are accidentally exposed.
- Keep dependencies and the JDK patched.
- Treat published container image tags as mutable references; use the release digest recorded with each release when immutable image identity is required.
- Verify release JAR and SBOM checksums before consuming published release assets.
- Preserve build provenance and SBOM artifacts when promoting a release into another environment.

## Automated security controls

Repository changes are continuously checked by GitHub Actions for dependency and code security issues, container policy violations, secret material, and supply-chain integrity. GitHub Actions references are pinned to immutable commit SHAs, Dependency Review rejects high-severity dependency changes, CodeQL runs the security-extended Java query set, container scans cover HIGH/CRITICAL vulnerabilities, misconfigurations, and secrets, and release artifacts receive build-provenance attestations. Container and Compose policy workflows also enforce non-root execution, dropped capabilities, bounded resources, read-only server filesystems, protected secrets, bounded logs, and local-only development port publishing.

These controls reduce common regression and supply-chain risks but do not replace independent security review or runtime monitoring.

## Release asset verification

For a downloaded release, verify both the server JAR and the SBOM against their published SHA-256 files before promotion or execution:

```bash
sha256sum --check chatapp-server-v1.1.0.jar.sha256
sha256sum --check chatapp-sbom-v1.1.0.json.sha256
```

A successful check means the downloaded bytes match the corresponding published checksum. For additional supply-chain assurance, retain the GitHub build-provenance attestations and use the published container digest rather than relying on a mutable tag such as `latest`.

## Container deployment

The supplied Docker and Compose configuration is hardened for development/demo deployment with a non-root server user, bounded resources, a read-only server filesystem, dropped Linux capabilities, `no-new-privileges`, internal database networking, and bounded container logs.

Production deployments should additionally provide:

- managed MySQL or an equivalently hardened database service
- database TLS where appropriate
- an external secret manager and credential rotation
- durable object storage for attachments
- certificate lifecycle automation
- centralized logs, metrics, alerting, and audit trails
- malware/content scanning for uploaded files
- independent security testing and threat modeling
