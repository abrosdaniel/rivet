import importlib.util
from pathlib import Path
import unittest
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

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

    def test_docs_do_not_start_game_or_database(self):
        result = m.plan('pull_request', 'refs/pull/1/merge', 'main', False, paths=['docs/server/index.md', 'README.md'])
        self.assertEqual([], result['checks'])
        self.assertFalse(result['native'])

    def test_fixture_changes_select_owner_and_shared_changes_select_all(self):
        result = m.plan('push', 'refs/heads/fix', 'main', False, paths=['rivet/mod/src/uiHarness/java/dev/abros/rivet/client/TradeHarness.java'])
        self.assertEqual(['java', 'trade'], result['checks'])
        for path in ['.github/workflows/ci.yml', 'rivet/tooling/ci_runtime.py', 'rivet/core/src/main/java/A.java']:
            self.assertEqual(list(m.CHECKS), m.affected([path]))

    def test_release_cannot_skip_checks_even_for_docs_only(self):
        result = m.plan('push', 'refs/heads/main', 'main', False, paths=['README.md'])
        self.assertTrue(result['publish'])
        self.assertEqual(list(m.CHECKS), result['checks'])

    def test_native_shards_are_disjoint_and_complete(self):
        import map_network
        flattened = [name for names in map_network.SUITES.values() for name in names]
        self.assertEqual(len(flattened), len(set(flattened)))
        self.assertEqual({s[0] for s in map_network.SCENARIOS}, set(flattened))
