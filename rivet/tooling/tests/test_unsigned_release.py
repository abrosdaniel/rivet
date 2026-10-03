import unittest,tempfile,argparse,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[3]/'rivet/tooling/seed'))
import rivet
class MainReleaseTest(unittest.TestCase):
 def test_build_and_verify_without_keys(self):
  with tempfile.TemporaryDirectory() as folder:
   out=Path(folder)/'release';args=argparse.Namespace(project=Path(__file__).resolve().parents[3]/'template',output=out,repository='https://github.com/example/project',commit='a'*40,policy='recommended')
   rivet.build(args);rivet.verify_release(out)
   self.assertFalse(list(out.glob('*.sig.json')));self.assertFalse((out/'stable.json').exists());self.assertEqual(rivet.sha((out/'rivet.lock.json').read_bytes()),(out/'rivet.lock.sha256').read_text().strip())
   path=out/'rivet.lock.json';path.write_bytes(path.read_bytes()+b' ')
   with self.assertRaisesRegex(ValueError,'hash mismatch'):rivet.verify_release(out)
