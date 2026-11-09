"""Release scheduling must not bypass a platform or a UI scenario."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[3]


class Workflow(unittest.TestCase):
    def test_every_ui_marker_is_verified_once_per_language(self):
        text = (ROOT / '.github/workflows/ci.yml').read_text()
        ui = text.split('  ui:\n', 1)[1].split('  modules:\n', 1)[0]
        self.assertIn('language: [ru_ru, en_us]', ui)
        self.assertIn('suite: [components, interaction, layout]', ui)
        markers = re.findall(r'verify_ui_snapshots.py[^\n]* (RIVET_\w+)', ui)
        self.assertCountEqual(markers, ['RIVET_RELEASE_CORRECTIONS_OK', 'RIVET_APPROVED_UI_OK',
            'RIVET_NESTED_UI_OK', 'RIVET_HUD_UI_OK', 'RIVET_NATIVE_OK', 'RIVET_ADAPTIVE_UI_OK',
            'RIVET_REORDER_UI_OK', 'RIVET_TAB_LAYOUT_OK', 'RIVET_AUDIT_FIXES_UI_OK'])
        # Each launch and its verifier must belong to the same shard.
        for name in ('pack', 'approved', 'nested', 'hud', 'tasks', 'adaptive', 'reorder', 'tab', 'settings'):
            steps = [step for step in ui.split('      - ') if 'visual-' + name + '.log' in step and 'upload-artifact' not in step]
            shards = [re.search(r"if: matrix.suite == '([^']+)'", step).group(1) for step in steps]
            self.assertEqual(1, len(set(shards)), name)

    def test_release_gate_still_requires_all_java_platforms(self):
        text = (ROOT / '.github/workflows/ci.yml').read_text()
        gate = text.split('  java:\n', 1)[1].split('  postgres:\n', 1)[0]
        self.assertIn('needs: [plan, java-linux, java-platforms]', gate)
        self.assertIn('test "$LINUX" = success', gate)
        self.assertIn('test "$PLATFORMS" = success', gate)
        self.assertIn('os: [windows-latest, macos-latest]', text)
        for name in ('ui', 'modules', 'map-network', 'builder', 'envelope', 'permissions', 'trade'):
            self.assertIn('  ' + name + ':\n    needs: [plan, java-linux]', text)
        self.assertIn('needs: [plan, java, contracts, postgres, ui, modules, map-network, builder, envelope, permissions, trade, api-proxy]', text)
