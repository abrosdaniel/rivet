"""Exercise wrapper retry boundaries without downloading or executing build tasks."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "prepare_gradle.sh"


class GradleBootstrapTest(unittest.TestCase):
    def run_wrapper(self, failures):
        with tempfile.TemporaryDirectory(prefix="gradle bootstrap ") as directory:
            root = Path(directory)
            wrapper = root / "gradlew"
            wrapper.write_text("#!/usr/bin/env bash\n"
                               "echo \"$*\" >> calls\n"
                               "n=$(wc -l < calls)\n"
                               f"if [ \"$n\" -le {failures} ]; then echo 'HTTP 500' >&2; exit 1; fi\n"
                               "echo 'Gradle ready'\n")
            wrapper.chmod(0o755)
            sleep = root / "sleep"
            sleep.write_text('#!/usr/bin/env bash\necho "$*" >> delays\n')
            sleep.chmod(0o755)
            env = dict(os.environ, PATH=str(root) + os.pathsep + os.environ["PATH"])
            result = subprocess.run(["bash", str(SCRIPT)], cwd=root, env=env,
                                    capture_output=True, text=True, timeout=10)
            calls = (root / "calls").read_text().splitlines()
            delays = (root / "delays").read_text().splitlines() if (root / "delays").exists() else []
            self.assertTrue(all(call == "--version --no-daemon" for call in calls))
            return result, calls, delays

    def test_cached_wrapper_runs_once(self):
        result, calls, delays = self.run_wrapper(0)
        self.assertEqual(0, result.returncode)
        self.assertEqual(1, len(calls))
        self.assertEqual([], delays)

    def test_temporary_outage_recovers(self):
        result, calls, delays = self.run_wrapper(2)
        self.assertEqual(0, result.returncode)
        self.assertEqual(3, len(calls))
        self.assertEqual(["5", "10"], delays)

    def test_persistent_outage_fails_without_building(self):
        result, calls, delays = self.run_wrapper(10)
        self.assertEqual(1, result.returncode)
        self.assertEqual(4, len(calls))
        self.assertEqual(["5", "10", "20"], delays)
        self.assertIn("build tasks were not started", result.stderr)
