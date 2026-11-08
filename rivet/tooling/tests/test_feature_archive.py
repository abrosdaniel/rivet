import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from feature_archive import export, validate


def content(*rows, complete=True):
    records = [{'format': 'rivet-feature-archive', 'version': 1}, *rows]
    if complete:
        records.append({'complete': True})
    return ''.join(json.dumps(row, ensure_ascii=False) + '\n' for row in records).encode()


class FeatureArchive(unittest.TestCase):
    def test_export_preserves_unicode_and_multiline_data(self):
        row = {'table': 'documents', 'row': {'id': 'event', 'body': {'title': 'Праздник\nвстреча'}}}
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'archive.jsonl'
            def run(*args, stdin, stdout):
                self.assertIn('ON_ERROR_STOP=1', args)
                self.assertIn('--no-psqlrc', args)
                self.assertIn(b'REPEATABLE READ READ ONLY', stdin.read())
                stdout.write(content(row))
            self.assertEqual(1, export(path, run, 'isolated')['documents'])
            self.assertEqual(row, json.loads(path.read_text(encoding='utf-8').splitlines()[1]))
            self.assertEqual(hashlib.sha256(path.read_bytes()).hexdigest(),
                             path.with_suffix('.jsonl.sha256').read_text().strip())
            if os.name != 'nt':
                self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(2, len(list(Path(directory).iterdir())))

    def test_failed_or_truncated_export_is_not_published(self):
        for fail in (False, True):
            with self.subTest(fail=fail), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'archive.jsonl'
                def run(*args, **kwargs):
                    kwargs['stdout'].write(content(complete=False))
                    if fail:
                        raise subprocess.CalledProcessError(1, 'psql')
                with self.assertRaises((ValueError, subprocess.CalledProcessError)):
                    export(path, run, 'isolated')
                self.assertEqual([], list(Path(directory).iterdir()))

    def test_never_overwrites_existing_archive_or_checksum(self):
        for suffix in ('', '.sha256'):
            with self.subTest(suffix=suffix), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'archive.jsonl'
                existing = Path(str(path) + suffix)
                existing.write_text('keep')
                with self.assertRaises(FileExistsError):
                    export(path, lambda *a, **k: self.fail('Must not connect'), 'isolated')
                self.assertEqual('keep', existing.read_text())

    def test_rejects_secrets_unexpected_tables_and_trailing_data(self):
        for data in (content({'table': 'auth_accounts', 'row': {}}),
                     content() + b'{}\n', content({'table': 'documents', 'row': []}),
                     b'{}\n', b'not-json\n'):
            with self.subTest(data=data), tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / 'archive.jsonl'
                path.write_bytes(data)
                with self.assertRaises(ValueError):
                    validate(path)
