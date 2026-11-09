import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import trade_network


class TradeNetwork(unittest.TestCase):
    def test_prepare_refuses_existing_world_before_writes_or_processes(self):
        with tempfile.TemporaryDirectory() as directory:
            work = Path(directory)
            config = work / 'server/server.properties'
            config.parent.mkdir()
            config.write_text('server-port=25565\n')
            with patch.object(sys, 'argv', ['trade_network.py', str(work), '--prepare']), patch.object(trade_network.subprocess, 'run') as run, patch.object(trade_network.subprocess, 'Popen') as popen:
                with self.assertRaisesRegex(ValueError, 'new work directory'):
                    trade_network.main()
            self.assertEqual('server-port=25565\n', config.read_text())
            run.assert_not_called()
            popen.assert_not_called()

    def test_fixture_player_names_and_recovery_stages(self):
        names = [stage for stage, _ in trade_network.STAGES]
        self.assertEqual(len(names), len(set(names)))
        for _, player in trade_network.STAGES:
            self.assertRegex(player, r'^[A-Za-z0-9_]{1,16}$')
        for stage in ('resume', 'cursorDisconnect', 'cursorReconnect', 'adminDenied', 'adminReturn', 'adminReturnB'):
            self.assertIn(stage, names)
