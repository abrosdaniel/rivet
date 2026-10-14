import importlib.util
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
spec = importlib.util.spec_from_file_location('map_network', TOOLS / 'map_network.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class MapNetwork(unittest.TestCase):
    def test_failure_cannot_be_hidden_by_success_marker(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'run.log'
            for text in ('', 'RIVET_MAP_PERMISSION_FAILED\nRIVET_MAP_PERMISSION_OK'):
                log.write_text(text)
                with self.assertRaises(RuntimeError):
                    m.verify(log, 'RIVET_MAP_PERMISSION_OK')
            log.write_text('RIVET_MAP_PERMISSION_OK')
            m.verify(log, 'RIVET_MAP_PERMISSION_OK')

    def test_isolated_configuration_and_non_operator_guest(self):
        import json
        root = TOOLS.parents[1]
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            m.prepare(root, work, 15439, 'rivet_isolated', 25580)
            config = tomllib.loads((work / 'server/config/rivet-server.toml').read_text())
            self.assertEqual('rivet_isolated', config['database']['database'])
            self.assertEqual(15439, config['database']['port'])
            self.assertEqual('', config['database']['password'])
            ops = json.loads((work / 'server/ops.json').read_text())
            self.assertEqual(['RivetMapTest'], [p['name'] for p in ops])
            self.assertNotIn('RivetMapGuest', (work / 'server/ops.json').read_text())
