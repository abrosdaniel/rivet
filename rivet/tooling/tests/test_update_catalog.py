import base64
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('update_catalog', Path(__file__).resolve().parents[1] / 'update_catalog.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class UpdateCatalog(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.openssl = '/opt/homebrew/bin/openssl' if Path('/opt/homebrew/bin/openssl').exists() else shutil.which('openssl')
        if not self.openssl:
            self.skipTest('OpenSSL is required for release signing')
        self.key = self.root / 'key.pem'
        subprocess.run([self.openssl, 'genpkey', '-algorithm', 'Ed25519', '-out', str(self.key)], check=True, capture_output=True)
        public = subprocess.check_output([self.openssl, 'pkey', '-in', str(self.key), '-pubout', '-outform', 'DER'])
        target = self.root / 'rivet/core/src/main/resources/rivet/update-key.txt'
        target.parent.mkdir(parents=True)
        target.write_text(base64.b64encode(public).decode())
        self.public = self.root / 'public.der'
        self.public.write_bytes(public)
        self.output = self.root / 'out'
        self.output.mkdir()
        self.bare = self.root / 'remote.git'
        subprocess.run(['git', 'init', '--bare', str(self.bare)], check=True, capture_output=True)
        original = m.execute
        def execute(*args, **kwargs):
            args = list(args)
            if args[0] == 'openssl':
                args[0] = self.openssl
            if args[0] == 'git' and 'remote' in args and 'add' in args:
                args[-1] = str(self.bare)
            return original(*args, **kwargs)
        self.addCleanup(patch.stopall)
        patch.object(m, 'execute', execute).start()
        patch.dict(os.environ, RIVET_UPDATE_SIGNING_KEY=self.key.read_text(), GITHUB_REPOSITORY='abrosdaniel/rivet').start()

    def release(self, version):
        name = f'rivet-{version}.jar'
        (self.output / name).write_bytes(version.encode())
        descriptor = dict(version=version, artifacts=[dict(url=f'https://github.com/abrosdaniel/rivet/releases/download/v{version}/{name}')])
        (self.output / 'core.json').write_text(json.dumps(descriptor))
        return m.signed_release(self.root, self.output)

    def test_signature_tamper_and_wrong_key(self):
        proof = json.loads(self.release('2.10.0').read_text())
        self.assertEqual('2.10.0', m.verify(proof, self.public, self.root)['descriptor']['version'])
        proof['payload'] = base64.b64encode(b'{}').decode()
        with self.assertRaises(subprocess.CalledProcessError):
            m.verify(proof, self.public, self.root)
        (self.root / 'rivet/core/src/main/resources/rivet/update-key.txt').write_text(base64.b64encode(b'wrong').decode())
        with self.assertRaisesRegex(ValueError, 'trust anchor'):
            self.release('2.10.0')

    def test_publish_retry_and_immutable_version(self):
        self.release('2.10.0')
        m.publish_static(self.root, self.output)
        m.publish_static(self.root, self.output)
        self.release('2.11.0')
        m.publish_static(self.root, self.output)
        def git(*args):
            return subprocess.check_output(['git', '--git-dir', str(self.bare), *args])
        self.assertEqual(b'1', git('rev-list', '--count', 'updates').strip())
        catalog = m.verify(json.loads(git('show', 'updates:catalog.json')), self.public, self.root)
        self.assertEqual(['2.11.0', '2.10.0'], [m.verify(e, self.public, self.root)['descriptor']['version'] for e in catalog['releases']])
        descriptor = json.loads((self.output / 'core.json').read_text())
        descriptor['changed'] = True
        (self.output / 'core.json').write_text(json.dumps(descriptor))
        m.signed_release(self.root, self.output)
        with self.assertRaisesRegex(ValueError, 'already signed'):
            m.publish_static(self.root, self.output)
