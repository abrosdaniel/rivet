"""Run a UI scenario using the same compiled runtime as the network regressions."""
import os
from pathlib import Path
import subprocess
from ci_runtime import restore
from map_network import command, prepare

root = Path(__file__).resolve().parents[2]
work = root / '.local/ci-ui'
prepare(root, work, 5432, 'rivet_test', 25579)
restore(root, Path(os.environ['RIVET_TEST_RUNTIME']), work)
(work / 'client/options.txt').write_text('onboardAccessibility:false\nlang:' + os.environ['RIVET_UI_LANGUAGE'] + '\n')
env = dict(os.environ)
env['MOD_CLASSES'] = os.pathsep.join('rivet%%' + str(work / 'build' / part) for part in (
    'mod/classes/java/main', 'mod/resources/main', 'mod/classes/java/uiHarness',
    'core/classes/java/main', 'core/resources/main', 'api/classes/java/main'))
java = str(Path(env['JAVA_HOME']) / 'bin/java')
result = subprocess.run(command(work, java, 'client') + ['--width', '1280', '--height', '1024'],
                        cwd=work / 'client', env=env)
raise SystemExit(result.returncode)
