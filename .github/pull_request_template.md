## Summary

<!-- What changed and why? -->

## Verification

- [ ] `mvn verify` passes locally.
- [ ] Relevant automated tests were added or updated.
- [ ] Docker/Compose changes were validated when applicable.
- [ ] Build/package output was checked when packaging changed.
- [ ] Documentation/config examples were updated when behavior changed.

## Security impact

- [ ] I reviewed authentication, authorization, input validation, persistence, protocol, and rate-limiting impact when applicable.
- [ ] I reviewed container/deployment security impact when applicable.
- [ ] I reviewed any new dependency, GitHub Action, permission, or external service for least privilege.
- [ ] All new externally supplied sizes, counts, timeouts, and resource usage are bounded.
- [ ] Database access uses parameterized queries; no new string-built SQL was introduced.
- [ ] File/path handling cannot escape its intended storage directory.
- [ ] I confirmed passwords, session tokens, database credentials, private file contents, and sensitive exception details are not written to logs.
- [ ] No secrets, credentials, private keys, certificates, keystores, or generated runtime data were added.
- [ ] I updated `SECURITY.md` or documentation when security behavior changed.

## Deployment / compatibility

- [ ] Protocol or database compatibility was preserved, or the change is documented.
- [ ] Database schema/init changes were reviewed for fresh and existing deployments.
- [ ] Docker image/runtime hardening was preserved when container files changed.
- [ ] TLS behavior and certificate/trust-store handling were reviewed when transport code changed.
- [ ] README/docs were updated when user-visible behavior or configuration changed.

## Reviewer notes

<!-- Add migration steps, rollout notes, threat-model considerations, or known limitations here. -->
