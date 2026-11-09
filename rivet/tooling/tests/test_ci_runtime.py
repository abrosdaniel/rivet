import json
from pathlib import Path
import platform
import tempfile
import unittest
from unittest.mock import patch
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ci_runtime as m


class Runtime(unittest.TestCase):
    def test_relocation_preserves_suite_isolation_and_rejects_other_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source, work = root / 'artifact', root / 'suite with spaces'
            dev = source / 'build/mod/moddev'
            dev.mkdir(parents=True)
            (dev / 'clientRunVmArgs.txt').write_text('-DlegacyClassPath.file=@RIVET_RUNTIME@/build/mod/moddev/clientLegacyClasspath.txt')
            (dev / 'clientLegacyClasspath.txt').write_text('@RIVET_RUNTIME@/dependencies/a.jar\n')
            (source / 'runtime.json').write_text(json.dumps(dict(commit='abc', system=platform.system(), machine=platform.machine())))
            with patch.object(m, 'revision', return_value='abc'):
                m.restore(root, source, work)
            self.assertIn(str(work / 'build'), (work / 'build/mod/moddev/clientRunVmArgs.txt').read_text())
            self.assertTrue((work / 'build/mod/moddev/clientRunVmArgs.txt').read_text().startswith('"'))
            self.assertEqual(str(source / 'dependencies/a.jar') + '\n', (work / 'build/mod/moddev/clientLegacyClasspath.txt').read_text())
            self.assertFalse((work / 'server').exists())
            self.assertIn(m.TOKEN, (dev / 'clientLegacyClasspath.txt').read_text())
            with patch.object(m, 'revision', return_value='different'):
                with self.assertRaisesRegex(ValueError, 'commit/platform'):
                    m.restore(root, source, root / 'other')

    def test_invalid_prepared_runtime_never_falls_back_to_build(self):
        with tempfile.TemporaryDirectory() as tmp, patch.dict('os.environ', RIVET_TEST_RUNTIME=tmp):
            with patch.object(m.subprocess, 'run') as build:
                with self.assertRaises(FileNotFoundError):
                    m.build_or_restore(Path(tmp), Path(tmp), Path(tmp) / 'init.gradle')
                build.assert_not_called()
