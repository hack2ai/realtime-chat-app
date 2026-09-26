#!/usr/bin/env python3
"""Validate baseline security controls for GitHub Actions workflows."""

from __future__ import annotations

import re
import sys
from pathlib import Path

WORKFLOW_DIR = Path(".github/workflows")
JOB_RE = re.compile(r"^  ([A-Za-z0-9_.-]+):\s*$")
TIMEOUT_RE = re.compile(r"^    timeout-minutes:\s*([0-9]+)\s*$")
PERMISSIONS_RE = re.compile(r"^    permissions:\s*$")
CHECKOUT_RE = re.compile(r"^        uses:\s+actions/checkout@[0-9a-fA-F]{40}(?:\s+#.*)?$")
STEP_RE = re.compile(r"^      - name:\s+")
PULL_REQUEST_TARGET_RE = re.compile(r"^\s*pull_request_target:\s*$")


def validate_workflow(path: Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    text = "\n".join(lines)
    errors: list[str] = []

    if not re.search(r"^permissions:\s*\{\}\s*$", text, re.MULTILINE):
        errors.append("missing top-level permissions: {}")

    if not re.search(r"^concurrency:\s*$", text, re.MULTILINE):
        errors.append("missing top-level concurrency policy")

    if PULL_REQUEST_TARGET_RE.search(text, re.MULTILINE):
        errors.append("pull_request_target is not allowed")

    in_jobs = False
    current_job: str | None = None
    current_has_timeout = False
    current_has_permissions = False

    def finish_job() -> None:
        nonlocal current_job, current_has_timeout, current_has_permissions
        if current_job is not None:
            if not current_has_timeout:
                errors.append(f"job '{current_job}' is missing timeout-minutes")
            if not current_has_permissions:
                errors.append(f"job '{current_job}' is missing job-level permissions")
        current_job = None
        current_has_timeout = False
        current_has_permissions = False

    def validate_checkout(start_index: int) -> None:
        index = start_index + 1
        while index < len(lines) and not STEP_RE.match(lines[index]):
            index += 1
        block = lines[start_index:index]
        if not any(line.strip() == "persist-credentials: false" for line in block):
            errors.append("actions/checkout must set persist-credentials: false")

    for index, line in enumerate(lines):
        if line == "jobs:":
            in_jobs = True
            continue

        if in_jobs and line and not line.startswith((" ", "\t")):
            break

        if not in_jobs:
            if CHECKOUT_RE.match(line):
                validate_checkout(index)
            continue

        job_match = JOB_RE.match(line)
        if job_match:
            finish_job()
            current_job = job_match.group(1)
            continue

        if current_job is not None:
            if TIMEOUT_RE.match(line):
                current_has_timeout = True
            if PERMISSIONS_RE.match(line):
                current_has_permissions = True

        if CHECKOUT_RE.match(line):
            validate_checkout(index)

    finish_job()
    return errors


def main() -> int:
    workflows = sorted(WORKFLOW_DIR.glob("*.yml"))
    if not workflows:
        print("No workflow files found.")
        return 1

    failed = False
    for workflow in workflows:
        errors = validate_workflow(workflow)
        if errors:
            failed = True
            print(f"{workflow}:")
            for error in errors:
                print(f"  - {error}")

    if failed:
        return 1

    print(f"Validated workflow hardening policy for {len(workflows)} workflows.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
