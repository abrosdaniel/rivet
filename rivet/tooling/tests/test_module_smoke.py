import importlib.util
from pathlib import Path
import tempfile
import tomllib
import unittest

spec = importlib.util.spec_from_file_location("module_smoke", Path(__file__).resolve().parents[1] / "module_smoke.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class ModuleSmoke(unittest.TestCase):
    def test_modes_keep_credentials_out_of_files(self):
        template = (Path(__file__).resolve().parents[3] / "rivet/core/src/main/resources/rivet-server.toml").read_text()
        for mode in ("default", "disabled", "base"):
            data = tomllib.loads(m.configuration(template, mode, 15440))
            self.assertEqual("", data["database"]["password"])
            self.assertEqual("RIVET_TEST_DB_PASSWORD", data["database"]["passwordEnv"])
            self.assertEqual(15440, data["database"]["port"])
            self.assertEqual("base" if mode == "base" else "false", data["auth"]["mode"])
            self.assertEqual(mode != "disabled", data["chat"]["enabled"])
            self.assertEqual(mode != "disabled", data["skins"]["enabled"])

    def test_both_lifecycle_markers_are_required(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "run.log"
            for text in ("", "RIVET_MODULE_START_OK", "RIVET_MODULE_STOP_OK", "RIVET_MODULE_START_OK\nRIVET_MODULE_STOP_OK\nRIVET_MODULE_FAILED"):
                log.write_text(text)
                with self.assertRaises(ValueError):
                    m.verify(log)
            log.write_text("RIVET_MODULE_START_OK\nRIVET_MODULE_STOP_OK")
            m.verify(log)
