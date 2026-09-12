# Release Runbook

Releases are created from semantic version tags in the form `vMAJOR.MINOR.PATCH`.

## Before tagging

Run the complete verification suite:

```bash
mvn verify
```

Confirm the working tree is clean and that the project version in `pom.xml` matches the tag you will create.

## Create a release

Create and push the version tag:

```bash
git tag v1.1.0
git push origin v1.1.0
```

The release workflow validates the tag-to-commit relationship, verifies that the Maven project version matches the tag, rebuilds the server and CycloneDX SBOM, checks their integrity, and validates the release container before publishing it.

## Published artifacts

The GitHub Release contains the runnable server JAR, its SHA-256 checksum, the CycloneDX SBOM, and its SHA-256 checksum.

Verify downloaded artifacts before use:

```bash
sha256sum --check chatapp-server-v1.1.0.jar.sha256
sha256sum --check chatapp-sbom-v1.1.0.json.sha256
```

The workflows also publish build-provenance attestations for the server artifact and SBOM.

## Container image

The release workflow publishes both the immutable version tag and `latest` to GHCR. It records the released image digest in the release assets and verifies that the pushed version and `latest` tag resolve to that digest.

Prefer the immutable digest reference for deployments:

```text
ghcr.io/hack2ai/realtime-chat-app@sha256:<verified-digest>
```

Do not use `latest` as an immutable deployment identifier.

## Rollback

For an application rollback, deploy a previously verified release digest rather than rebuilding from the historical tag. Keep the matching JAR, SBOM, checksums, and provenance records together with the deployment record.

## Security

Never commit application passwords, secret files, private keys, certificates, keystores, or trust stores. Use environment-backed or file-backed secrets for deployments, and rotate credentials when a secret may have been exposed.
