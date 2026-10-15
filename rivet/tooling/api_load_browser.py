"""Opt-in isolated 100-identity API load + manually opened real browser fixture.
Requires Docker PostgreSQL18 and Java21. No production credentials/profile used.
"""
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('work', type=Path)
    parser.add_argument('--skip-mixed', action='store_true', help='Skip separately verified mixed module test')
    parser.add_argument('--load-only', action='store_true', help='Do not wait for the browser fixture')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    # A new disposable database prevents accidentally truncating production tables.
    name = 'rivet-api-load-' + secrets.token_hex(4)
    env = os.environ.copy()
    env['POSTGRES_PASSWORD'] = secrets.token_hex(24)
    subprocess.run(['docker', 'run', '-d', '--name', name, '-p', '127.0.0.1:55449:5432',
                    '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER=rivet_test',
                    '-e', 'POSTGRES_DB=rivet_test', 'postgres:18'], env=env, check=True,
                   stdout=subprocess.DEVNULL)
    try:
        for _ in range(60):
            if subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'rivet_test'],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError('Test PostgreSQL not ready')
        env['RIVET_TEST_DB_PASSWORD'] = env.pop('POSTGRES_PASSWORD')
        env['RIVET_TEST_DB_PORT'] = '55449'
        env['RIVET_TEST_DB_HOST'] = '127.0.0.1'
        env['RIVET_MIXED_LOAD'] = 'true'
        env['RIVET_MIXED_PLAYERS'] = '100'
        init = work / 'load.init.gradle'
        def quote(value):
            return "'" + str(value).replace('\\', '\\\\').replace("'", "\\'") + "'"
        init.write_text('allprojects { layout.buildDirectory = file(' + quote(work / 'build') + ' + "/" + project.name) }\n' +
                        'gradle.projectsEvaluated { rootProject.project(":core") { tasks.register("apiLoadBrowser", JavaExec) {\n' +
                        'dependsOn testClasses; classpath = sourceSets.test.runtimeClasspath; maxHeapSize = "512m";\n' +
                        'mainClass = "dev.abros.rivet.core.api.ApiLoadBrowserHarness"; args ' + quote(work / 'results') + ', ' + quote(root / 'rivet/tooling/api_browser.html') + (', \'load-only\'' if args.load_only else '') + '\n} } }\n')
        with (work / 'run.log').open('w') as log:
            subprocess.run([str(root / 'gradlew'), '-I', str(init)] + ([] if args.skip_mixed else [':core:postgresTest', '--tests', '*MixedModuleLoadTest']) + [':core:apiLoadBrowser',
                            '--no-daemon', '--console=plain', '--rerun-tasks'], cwd=root, env=env,
                           stdout=log, stderr=subprocess.STDOUT, check=True, timeout=1200)
        print('RIVET_API_LOAD_ONLY_OK' if args.load_only else 'RIVET_API_LOAD_BROWSER_OK')
    finally:
        subprocess.run(['docker', 'rm', '-f', '-v', name], stdout=subprocess.DEVNULL, check=True)


if __name__ == '__main__':
    main()
