import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

spec = importlib.util.spec_from_file_location('snapshots', Path(__file__).resolve().parents[1] / 'verify_ui_snapshots.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

class SnapshotCounts(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.log = self.root / 'run.log'
        self.frames = self.root / 'screenshots'
        self.frames.mkdir()

    def frame(self, name):
        def chunk(kind, body):
            return struct.pack('>I', len(body)) + kind + body + struct.pack('>I', zlib.crc32(kind + body) & 0xffffffff)
        data = b'\x89PNG\r\n\x1a\n'
        data += chunk(b'IHDR', struct.pack('>IIBBBBB', 320, 200, 8, 2, 0, 0, 0))
        data += chunk(b'IDAT', zlib.compress((b'\0' + b'\0' * 960) * 200))
        data += chunk(b'IEND', b'')
        (self.frames / name).write_bytes(data)

    def test_auto_verifies_reported_count_and_png(self):
        self.log.write_text('RIVET_NATIVE_OK: frames=1, all themes\n')
        self.frame('native-001.png')
        m.validate(self.frames, 'auto', self.log, 'RIVET_NATIVE_OK')
        (self.frames / 'native-001.png').write_bytes(b'broken')
        with self.assertRaisesRegex(ValueError, 'Invalid screenshot'):
            m.validate(self.frames, 'auto', self.log, 'RIVET_NATIVE_OK')

    def test_missing_or_extra_frames_still_fail(self):
        self.log.write_text('RIVET_NATIVE_OK: frames=1, all themes\n')
        with self.assertRaisesRegex(ValueError, 'Expected 1 frames, got 0'):
            m.validate(self.frames, 'auto', self.log, 'RIVET_NATIVE_OK')
        self.frame('native-001.png')
        self.frame('stale.png')
        with self.assertRaisesRegex(ValueError, 'Expected 1 frames, got 2'):
            m.validate(self.frames, 'auto', self.log, 'RIVET_NATIVE_OK')

    def test_invalid_or_duplicate_reports_fail(self):
        for text in ['RIVET_NATIVE_OK', 'RIVET_NATIVE_OK: frames=0', 'RIVET_NATIVE_OK: frames=1\nRIVET_NATIVE_OK: frames=1', 'RIVET_NATIVE_OK: frames=1\nRIVET_NATIVE_FAILED']:
            with self.subTest(text=text):
                self.log.write_text(text)
                with self.assertRaises(ValueError):
                    m.validate(self.frames, 'auto', self.log, 'RIVET_NATIVE_OK')

    def test_fixed_counts_remain_supported(self):
        self.log.write_text('RIVET_HUD_UI_OK')
        self.frame('hud-001.png')
        m.validate(self.frames, '1', self.log, 'RIVET_HUD_UI_OK')

    def test_checks_need_completion_but_no_png_files(self):
        self.log.write_text("RIVET_REORDER_UI_OK: frames=135, drag checks\n")
        m.validate(self.frames, "checks", self.log, "RIVET_REORDER_UI_OK")
        self.assertEqual(list(self.frames.iterdir()), [])
        self.log.write_text("RIVET_REORDER_UI_FAILED: step=3\nRIVET_REORDER_UI_OK: frames=135\n")
        with self.assertRaisesRegex(ValueError, "RIVET_REORDER_UI_FAILED: step=3"):
            m.validate(self.frames, "checks", self.log, "RIVET_REORDER_UI_OK")

    def test_checks_reject_tab_failure_even_with_completion(self):
        self.log.write_text("RIVET_TAB_LAYOUT_FAILED frame=2\nRIVET_TAB_LAYOUT_OK: frames=126\n")
        with self.assertRaisesRegex(ValueError, "RIVET_TAB_LAYOUT_FAILED"):
            m.validate(self.frames, "checks", self.log, "RIVET_TAB_LAYOUT_OK")

    def test_checks_reject_missing_or_invalid_completion(self):
        for text in ["", "RIVET_REORDER_UI_PLAN: frames=135", "RIVET_REORDER_UI_OK", "RIVET_REORDER_UI_OK: frames=0", "RIVET_REORDER_UI_OK: frames=1\nRIVET_REORDER_UI_OK: frames=1"]:
            with self.subTest(text=text):
                self.log.write_text(text)
                with self.assertRaises(ValueError):
                    m.validate(self.frames, "checks", self.log, "RIVET_REORDER_UI_OK")
