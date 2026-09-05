# Security Policy

## Scope

This repository is a learning/portfolio real-time chat application. It is not a security-audited production service.

## Reporting a vulnerability

Please do not publish credentials, exploit code, or sensitive details in a public issue. Contact the repository owner privately through the contact details on the GitHub profile and include:

- affected component or file
- impact and severity as you understand it
- reproduction steps or a minimal proof of concept
- any suggested mitigation

Please allow reasonable time for investigation and remediation before public disclosure.

## Security expectations

- Never commit `config.properties`, passwords, tokens, private keys, or other secrets.
- Use TLS before exposing the TCP server outside a trusted local network.
- Use a dedicated database account with the minimum required privileges.
- Rotate credentials if they are accidentally exposed.
- Keep dependencies and the JDK patched.
- Treat published container image tags as mutable references; use the release digest recorded with each release when immutable image identity is required.
- Verify release JAR and SBOM checksums before consuming published release assets.
- Preserve build provenance and SBOM artifacts when promoting a release into another environment.

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
