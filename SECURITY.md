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

## Release integrity

Tagged releases publish the runnable server JAR together with a SHA-256 checksum and CycloneDX SBOM. GitHub Actions also generates build provenance attestations for the server artifact and the release container image.

Before deploying a downloaded JAR, verify the published checksum from the same GitHub Release:

```bash
sha256sum --check chatapp-server-v1.1.0.jar.sha256
```

For a higher-assurance deployment, verify the release provenance attestation through GitHub's artifact attestation tooling and review the published SBOM for unexpected dependencies before promotion.

Release images are published to GHCR with a semantic-version tag, `latest`, and a commit-SHA tag. The release workflow verifies image metadata, non-root execution, filesystem permissions, entrypoint, exposed port, healthcheck, and the published registry digest before creating the GitHub Release.

## Security expectations

- Never commit `config.properties`, passwords, tokens, private keys, or other secrets.
- Use TLS before exposing the TCP server outside a trusted local network.
- Use a dedicated database account with the minimum required privileges.
- Rotate credentials if they are accidentally exposed.
- Keep dependencies and the JDK patched.
- Verify release checksums before deployment.
- Review release SBOMs and provenance attestations for production deployments.
