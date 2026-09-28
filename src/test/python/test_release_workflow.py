"""Regression checks for the REL-005/REL-006 release workflow."""

from pathlib import Path
import re
from unittest import TestCase


WORKFLOW = (
    Path(__file__).resolve().parents[3]
    / ".github"
    / "workflows"
    / "release.yml"
)


class ReleaseWorkflowTest(TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    def test_apple_silicon_job_uses_routable_runner_label(self):
        job = re.search(
            r"(?ms)^  macos-arm:\n(?P<body>.*?)(?=^  [a-zA-Z0-9_-]+:)",
            self.workflow,
        )

        self.assertIsNotNone(job, "macos-arm release job is missing")
        self.assertRegex(job.group("body"), r"(?m)^    runs-on: macos-15$")
        self.assertNotIn("runs-on: macos-15-arm64", job.group("body"))

    def test_release_rerun_removes_assets_not_in_current_build(self):
        upload = self.workflow.find("gh release upload")
        list_assets = self.workflow.find("--json assets")
        delete_asset = self.workflow.find("gh release delete-asset")

        self.assertGreaterEqual(upload, 0, "existing release is not updated")
        self.assertGreater(
            list_assets,
            upload,
            "release assets must be inspected after current artifacts upload",
        )
        self.assertGreater(
            delete_asset,
            list_assets,
            "assets absent from dist must be removed after a successful upload",
        )
        self.assertIn('if [ ! -f "dist/$asset" ]; then', self.workflow)

    def test_release_tests_trigger_when_their_sources_change(self):
        self.assertIn("- src/test/python/**", self.workflow)
