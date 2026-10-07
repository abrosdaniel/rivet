import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("ci_plan", Path(__file__).resolve().parents[1] / "ci_plan.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class CiPlan(unittest.TestCase):
    def test_pr_and_branch_push_are_fast_and_cannot_publish(self):
        for event, ref in [("pull_request", "refs/pull/1/merge"), ("push", "refs/heads/fix"), ("push", "refs/tags/v1.0.0")]:
            result = m.plan(event, ref, "main", False)
            self.assertFalse(result["full"])
            self.assertFalse(result["publish"])
            self.assertEqual(["ubuntu-latest"], result["platforms"])
            self.assertEqual([18], result["postgres"])

    def test_new_default_branch_release_requires_complete_matrix(self):
        result = m.plan("push", "refs/heads/main", "main", False)
        self.assertTrue(result["full"])
        self.assertTrue(result["publish"])
        self.assertEqual(3, len(result["platforms"]))
        self.assertEqual([17, 18], result["postgres"])

    def test_published_version_needs_only_fast_checks(self):
        result = m.plan("push", "refs/heads/main", "main", True)
        self.assertFalse(result["full"])
        self.assertFalse(result["publish"])

    def test_manual_fast_check_cannot_publish(self):
        self.assertFalse(m.plan("workflow_dispatch", "refs/heads/main", "main", False, False)["publish"])

    def test_manual_full_checks_work_on_branches_without_publishing(self):
        result = m.plan("workflow_dispatch", "refs/heads/fix", "main", False)
        self.assertTrue(result["full"])
        self.assertFalse(result["publish"])
