# Release Security Checklist

Use this checklist when changing the release pipeline or preparing a version tag.

## Source and workflow integrity

- [ ] GitHub Actions are pinned to immutable 40-character commit SHAs.
- [ ] The release checkout keeps persisted Git credentials disabled.
- [ ] The release tag resolves to the exact commit being built.
- [ ] The Maven project version matches the `vX.Y.Z` Git tag.

## Build artifacts

- [ ] Maven verification completes successfully.
- [ ] The server JAR is non-empty and declares `com.chatapp.server.ChatServer` as `Main-Class`.
- [ ] The CycloneDX SBOM exists and identifies the expected project and version.
- [ ] SHA-256 checksums are generated and verified before publication.
- [ ] Server JAR and SBOM receive build provenance attestations.

## Container release

- [ ] Docker Compose configuration passes validation and its security policy checks.
- [ ] The release image contains the expected OCI metadata and source revision.
- [ ] The image runs as the dedicated non-root `chatapp` user (UID 10001).
- [ ] The image entrypoint, exposed port, healthcheck, stop signal, filesystem layout, and JVM safety options are validated.
- [ ] The image contains no local secret material.
- [ ] The release image is pushed by immutable version tag and `latest`.
- [ ] The published registry digest is captured and verified against the locally built image.
- [ ] The `latest` tag is verified to resolve to the same digest as the versioned release.

## After release

- [ ] Confirm the GitHub Release contains the server JAR, checksum, SBOM, and SBOM checksum.
- [ ] Confirm provenance attestations are visible for the published release artifacts.
- [ ] Confirm the published container digest is recorded for deployment/change-management purposes.

This checklist complements the automated controls in `.github/workflows/release.yml`; it does not replace them.