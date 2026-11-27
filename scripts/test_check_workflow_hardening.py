#!/usr/bin/env python3
"""Regression tests for GitHub Actions workflow hardening policy."""

from __future__ import annotations

import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("check_workflow_hardening.py")
SPEC = importlib.util.spec_from_file_location("check_workflow_hardening", MODULE_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Unable to load workflow hardening checker.")
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


GITHUB_WORKFLOW = "$" + "{ github.workflow }"
GITHUB_REF = "$" + "{ github.ref }"
WORKFLOW_RUN_HEAD_SHA = "$" + "{ github.event.workflow_run.head_sha }"

SECURE_WORKFLOW = """name: Example

on:
  push:
    branches: [main]

permissions: {}

concurrency:
  group: example-""" + GITHUB_WORKFLOW + "-" + GITHUB_REF + """
  cancel-in-progress: true

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    permissions:
      contents: read
    steps:
      - name: Checkout
        uses: actions/checkout@0123456789abcdef0123456789abcdef01234567
        with:
          persist-credentials: false
      - name: Build
        run: echo ok
"""


class WorkflowHardeningTests(unittest.TestCase):
    def validate(self, content: str) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "workflow.yml"
            path.write_text(content, encoding="utf-8")
            return MODULE.validate_workflow(path)

    def test_secure_workflow_passes(self) -> None:
        self.assertEqual(self.validate(SECURE_WORKFLOW), [])

    def test_workflow_run_head_sha_is_valid_concurrency_identity(self) -> None:
        workflow = SECURE_WORKFLOW.replace(GITHUB_REF, WORKFLOW_RUN_HEAD_SHA)
        self.assertEqual(self.validate(workflow), [])

    def test_static_concurrency_group_is_valid_when_runs_are_serialized(self) -> None:
        workflow = SECURE_WORKFLOW.replace(
            "  group: example-" + GITHUB_WORKFLOW + "-" + GITHUB_REF + "\n",
            "  group: release-publish\n",
        ).replace(
            "  cancel-in-progress: true\n",
            "  cancel-in-progress: false\n",
        )
        self.assertEqual(self.validate(workflow), [])

    def test_missing_top_level_controls_is_rejected(self) -> None:
        workflow = """jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@0123456789abcdef0123456789abcdef01234567
        with:
          persist-credentials: false
"""
        errors = self.validate(workflow)
        self.assertIn("missing top-level permissions: {}", errors)
        self.assertIn("missing top-level concurrency policy", errors)
        self.assertIn("job 'build' is missing timeout-minutes", errors)
        self.assertIn("job 'build' is missing job-level permissions", errors)

    def test_incomplete_concurrency_policy_is_rejected(self) -> None:
        missing_cancel = SECURE_WORKFLOW.replace("  cancel-in-progress: true\n", "")
        errors = self.validate(missing_cancel)
        self.assertIn("concurrency policy is missing cancel-in-progress", errors)

        weak_group = SECURE_WORKFLOW.replace(
            "  group: example-" + GITHUB_WORKFLOW + "-" + GITHUB_REF + "\n",
            "  group: example\n",
        )
        errors = self.validate(weak_group)
        self.assertIn("concurrency group must include a GitHub ref or run identity", errors)

    def test_action_references_must_be_immutable(self) -> None:
        workflow = SECURE_WORKFLOW.replace(
            "actions/checkout@0123456789abcdef0123456789abcdef01234567",
            "actions/checkout@v4",
        )
        errors = self.validate(workflow)
        self.assertIn(
            "action actions/checkout must use a 40-character immutable commit SHA; found v4",
            errors,
        )

    def test_pull_request_target_is_rejected(self) -> None:
        workflow = SECURE_WORKFLOW.replace(
            "  push:",
            "  pull_request_target:\n    branches: [main]\n  push:",
        )
        self.assertIn("pull_request_target is not allowed", self.validate(workflow))

    def test_checkout_persist_credentials_must_be_disabled(self) -> None:
        workflow = SECURE_WORKFLOW.replace("          persist-credentials: false\n", "")
        self.assertIn(
            "actions/checkout must set persist-credentials: false",
            self.validate(workflow),
        )

    def test_timeout_limits_are_enforced(self) -> None:
        excessive = SECURE_WORKFLOW.replace("timeout-minutes: 15", "timeout-minutes: 31")
        self.assertIn(
            "job 'build' timeout-minutes must be <= 30; found 31",
            self.validate(excessive),
        )

        zero = SECURE_WORKFLOW.replace("timeout-minutes: 15", "timeout-minutes: 0")
        self.assertIn(
            "job 'build' timeout-minutes must be greater than 0",
            self.validate(zero),
        )


if __name__ == "__main__":
    unittest.main()
