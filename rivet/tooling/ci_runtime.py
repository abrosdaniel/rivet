"""Portable, commit-bound native test runtime. Never includes game worlds or secrets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess

TOKEN = '@RIVET_RUNTIME@'
PARTS = ('mod/classes/java/main', 'mod/resources/main', 'mod/classes/java/uiHarness',
         'core/classes/java/main', 'core/resources/main', 'api/classes/java/main',
         'mod/compatibility-tests')


def revision(root):
    return subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()


def export(root, work, target):
    if target.exists():
        raise ValueError('Runtime export requires a new directory')
    target.mkdir(parents=True)
    for part in PARTS:
        source = work / 'build' / part
        if source.exists():
            shutil.copytree(source, target / 'build' / part)
    dev = work / 'build/mod/moddev'
    out = target / 'build/mod/moddev'
    shutil.copytree(dev / 'artifacts', out / 'artifacts')
    files = [dev / (side + suffix) for side in ('client', 'server')
             for suffix in ('LegacyClasspath.txt', 'RunVmArgs.txt', 'RunProgramArgs.txt', 'Log4j2.xml')]
    replacements = {str(work): TOKEN}
    dependencies = set()
    for side in ('client', 'server'):
        dependencies.update((dev / (side + 'LegacyClasspath.txt')).read_text().splitlines())
        lines = (dev / (side + 'RunVmArgs.txt')).read_text().splitlines()
        for i, line in enumerate(lines):
            if line in ('-p', '--module-path'):
                dependencies.update(lines[i + 1].split(os.pathsep))
    for value in sorted(dependencies):
        source = Path(value)
        if not source.is_absolute():
            raise ValueError('Unexpected relative dependency: ' + value)
        if source.is_relative_to(work):
            if not (target / source.relative_to(work)).exists():
                raise ValueError('Missing exported runtime dependency: ' + value)
            continue
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        relative = Path('dependencies') / digest / source.name
        destination = target / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, destination)
        replacements[value] = TOKEN + '/' + relative.as_posix()
    args = (dev / 'clientRunProgramArgs.txt').read_text().splitlines()
    assets = Path(args[args.index('--assetsDir') + 1])
    index = args[args.index('--assetIndex') + 1]
    index_path = assets / 'indexes' / (index + '.json')
    (target / 'assets/indexes').mkdir(parents=True)
    shutil.copy2(index_path, target / 'assets/indexes' / index_path.name)
    for item in json.loads(index_path.read_text())['objects'].values():
        digest = item['hash']
        relative = Path('objects') / digest[:2] / digest
        destination = target / 'assets' / relative
        if not destination.exists():
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(assets / relative, destination)
    replacements[str(assets)] = TOKEN + '/assets'
    for source in files:
        text = source.read_text()
        for old, new in sorted(replacements.items(), key=lambda pair: -len(pair[0])):
            text = text.replace(old, new)
        (out / source.name).write_text(text)
    (target / 'runtime.json').write_text(json.dumps(dict(commit=revision(root), system=platform.system(), machine=platform.machine())))


def restore(root, source, work):
    manifest = json.loads((source / 'runtime.json').read_text())
    expected = dict(commit=revision(root), system=platform.system(), machine=platform.machine())
    if manifest != expected:
        raise ValueError('Prepared runtime does not match this commit/platform')
    # Per-suite classes and run arguments, shared immutable libraries/assets.
    shutil.copytree(source / 'build', work / 'build', dirs_exist_ok=True)
    for path in (work / 'build/mod/moddev').iterdir():
        if path.suffix in ('.txt', '.xml'):
            text = path.read_text().replace(TOKEN + '/build', str(work / 'build'))
            text = text.replace(TOKEN, str(source))
            if path.name.endswith(('RunVmArgs.txt', 'RunProgramArgs.txt')):
                # Java @argfiles split unquoted whitespace, including in relocated paths.
                text = '\n'.join('"' + line.replace('\\', '\\\\').replace('"', '\\"') + '"'
                    if any(c.isspace() for c in line) and not line.startswith(('#', '"')) else line
                    for line in text.splitlines()) + '\n'
            path.write_text(text)


def build_or_restore(root, work, init, server_only=False):
    prepared = os.environ.get('RIVET_TEST_RUNTIME')
    if prepared:
        restore(root, Path(prepared).resolve(), work)
        (work / 'prepare.log').write_text('Using commit-bound prepared runtime\n')
        return
    tasks = [':mod:classes', ':mod:compileUiHarnessJava', ':mod:prepareServerRun']
    if not server_only:
        tasks.append(':mod:prepareClientRun')
    with (work / 'prepare.log').open('w') as log:
        subprocess.run([str(root / 'gradlew'), '-I', str(init), '--project-cache-dir', str(work / 'gradle'),
                        '-PrivetUiHarness', *tasks, '--no-daemon', '--console=plain'], cwd=root,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['prepare', 'export', 'restore'])
    parser.add_argument('work', type=Path)
    parser.add_argument('runtime', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work, runtime = args.work.resolve(), args.runtime.resolve()
    if args.action == 'restore':
        restore(root, runtime, work)
        return
    if args.action == 'prepare':
        from map_network import prepare
        init = prepare(root, work, 5432, 'rivet_test', 25579)
        build_or_restore(root, work, init)
        subprocess.run([str(root / 'gradlew'), '-I', str(init), '-PrivetUiHarness',
                        ':mod:compatibilityTestJar', '--no-daemon'], cwd=root, check=True)
    export(root, work, runtime)


if __name__ == '__main__':
    main()
