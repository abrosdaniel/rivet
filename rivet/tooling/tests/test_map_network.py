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

    def test_failure_reports_original_exception(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / 'run.log'
            log.write_text('RIVET_ROLE_NETWORK_FAILED phase=20\nAssertionError: Missing entry\n' + 'shutdown\n' * 100)
            with self.assertRaisesRegex(RuntimeError, 'AssertionError: Missing entry'):
                m.verify(log, 'RIVET_ROLE_NETWORK_OK')
            log.write_text('Startup failed: missing library')
            with self.assertRaisesRegex(RuntimeError, 'Startup failed: missing library'):
                m.verify(log, 'RIVET_ROLE_NETWORK_OK')

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
            # Realtime has a separate identity so its notices cannot contaminate
            # the following map overlay/pixel checks. Only these fixtures are OP.
            self.assertEqual(['RivetMapTest', 'RivetDataTest'], [p['name'] for p in ops])
            import hashlib
            import uuid
            for operator in ops:
                identity = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + operator['name']).encode()).digest(), version=3)
                self.assertEqual(str(identity), operator['uuid'])
                self.assertEqual(4, operator['level'])
            realtime = next(s for s in m.SCENARIOS if s[0] == 'realtime')
            self.assertEqual('RivetDataTest', realtime[2])
            self.assertNotIn(realtime[2], [s[2] for s in m.SCENARIOS if s[0] != 'realtime'])
            for scenario in m.SCENARIOS:
                self.assertRegex(scenario[2], r'^[A-Za-z0-9_]{1,16}$')
            self.assertNotIn('RivetMapGuest', (work / 'server/ops.json').read_text())
