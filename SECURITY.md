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
