"""Packaged UI -> helper -> packaged reconnect. Never targets an existing game profile.

The source fixture is the candidate with older metadata, NOT a historical release.
Unpublished core bytes are supplied through the verified download cache; pack bytes
travel through the real server transport. Production URL policy stays unchanged.
"""
import argparse
import io
import itertools
import json
import os
from pathlib import Path
import shutil
import socket
import threading
import subprocess
import time
import zipfile
from map_network import command, prepare, stop, verify


class TcpRelay:
    """Real byte-transparent tunnel; the client still authenticates the origin TLS key."""
    def __init__(self, target):
        self.target = target
        ipv4 = socket.create_server(('127.0.0.1', 0))
        self.port = ipv4.getsockname()[1]
        self.listeners = [ipv4]
        # Minecraft may resolve localhost to ::1 on Linux. Listen on both
        # loopback families, without exposing the test relay on LAN interfaces.
        try:
            families = {entry[0] for entry in socket.getaddrinfo('localhost', self.port, type=socket.SOCK_STREAM)}
            if socket.AF_INET6 in families:
                self.listeners.append(socket.create_server(('::1', self.port), family=socket.AF_INET6))
        except Exception:
            for listener in self.listeners:
                listener.close()
            raise
        self.bytes = 0
        self.closed = threading.Event()
        self.connections = set()
        self.lock = threading.Lock()
        for listener in self.listeners:
            listener.settimeout(0.5)
            threading.Thread(target=self.accept, args=(listener,), daemon=True).start()

    def accept(self, listener):
        while not self.closed.is_set():
            try:
                client, _ = listener.accept()
            except socket.timeout:
                continue
            except OSError:
                return
            threading.Thread(target=self.forward, args=(client,), daemon=True).start()

    def forward(self, client):
        upstream = None
        try:
            upstream = socket.create_connection(self.target, timeout=5)
            upstream.settimeout(None)
            with self.lock:
                if self.closed.is_set():
                    return
                self.connections.update((client, upstream))
            def copy(source, destination):
                try:
                    while data := source.recv(65536):
                        destination.sendall(data)
                        with self.lock:
                            self.bytes += len(data)
                except OSError:
                    pass
                finally:
                    try:
                        destination.shutdown(socket.SHUT_WR)
                    except OSError:
                        pass
            reverse = threading.Thread(target=copy, args=(upstream, client), daemon=True)
            reverse.start()
            copy(client, upstream)
            reverse.join(timeout=5)
        except OSError:
            pass
        finally:
            for connection in (client, upstream):
                if connection is not None:
                    with self.lock:
                        self.connections.discard(connection)
                    connection.close()

    def close(self):
        self.closed.set()
        for listener in self.listeners:
            listener.close()
        with self.lock:
            for connection in self.connections:
                try:
                    connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                connection.close()


def old_fixture(bundle, version):
    def rewrite(data, nested=False):
        output = io.BytesIO()
        with zipfile.ZipFile(io.BytesIO(data)) as source, zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED) as dest:
            for item in source.infolist():
                value = source.read(item.filename)
                if item.filename == 'META-INF/neoforge.mods.toml':
                    value = value.replace(('version="' + version + '"').encode(), b'version="2.5.99"')
                elif item.filename == 'rivet/game.jar' and not nested:
                    value = rewrite(value, True)
                dest.writestr(item, value)
        return output.getvalue()
    return rewrite(bundle.read_bytes())


def wait_for(path, marker, process, seconds=120):
    deadline = time.monotonic() + seconds
    while marker not in path.read_text(errors='replace'):
        if process.poll() is not None or time.monotonic() > deadline:
            raise RuntimeError('Missing ' + marker + '\n' + path.read_text(errors='replace')[-8000:])
        time.sleep(1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('work', type=Path)
    parser.add_argument('--bundle', type=Path, required=True)
    parser.add_argument('--runtime', type=Path, required=True, help='Prepared native runtime containing build/mod/moddev')
    parser.add_argument('--fixtures', type=Path, required=True)
    parser.add_argument('--version', required=True)
    parser.add_argument('--signed-release', type=Path, help='Verified signed candidate metadata; download core through Builder instead of preloading cache')
    parser.add_argument('--database-port', type=int, default=5432)
    parser.add_argument('--database', default='rivet_test')
    parser.add_argument('--game-port', type=int, default=25589)
    parser.add_argument('--pack-port', type=int, default=25588)
    parser.add_argument('--scales', type=int, nargs='+', default=[1, 2, 3])
    parser.add_argument('--profiles', nargs='+', choices=['default', 'light', 'contrast'], default=['default', 'light', 'contrast'])
    parser.add_argument('--prefer-ipv6', action='store_true', help='Prefer IPv6 in client JVMs to cover localhost resolution independently of host defaults')
    parser.add_argument('--alias-reconnect', action='store_true', help='Also reconnect through the other game address without reinstalling')
    parser.add_argument('--routes', nargs='+', choices=['direct', 'proxy', 'fallback'], default=['direct'])
    args = parser.parse_args()
    if 'fallback' in args.routes and args.routes[-1] != 'fallback':
        parser.error('fallback must be the last route (it closes the pack proxy)')
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    # Refuse existing profiles, including accidental Prism destinations.
    work.mkdir(parents=True, exist_ok=True)
    if any(work.iterdir()):
        raise ValueError('Use a new empty test directory')
    prepare(root, work, args.database_port, args.database, args.game_port)
    source_dev = args.runtime.resolve() / 'build/mod/moddev'
    dev = work / 'build/mod/moddev'
    dev.mkdir(parents=True)
    (dev / 'artifacts').symlink_to(source_dev / 'artifacts', target_is_directory=True)
    # Dev classpaths add Rivet's libraries explicitly. Packaged runs must obtain
    # those libraries only from JarJar, just like a player's installation.
    bundled = ('httpclient5-', 'httpcore5-', 'HikariCP-', 'postgresql-',
               'bcprov-', 'bcutil-', 'bcpkix-', 'Java-WebSocket-', 'skija-shared-', 'types-')
    for side in ('client', 'server'):
        for suffix in ('LegacyClasspath.txt', 'RunVmArgs.txt', 'RunProgramArgs.txt', 'Log4j2.xml'):
            source = source_dev / (side + suffix)
            text = source.read_text().replace(str(source_dev), str(dev))
            if suffix == 'LegacyClasspath.txt':
                text = '\n'.join(p for p in text.splitlines() if not Path(p).name.startswith(bundled)) + '\n'
            (dev / source.name).write_text(text)
    server = work / 'server'
    bundle = args.bundle.resolve()
    shutil.copy2(bundle, server / 'mods' / bundle.name)
    if args.signed_release:
        relay = server / 'rivet/update-relay'
        relay.mkdir(parents=True)
        shutil.copyfile(args.signed_release, relay / (args.version + '.json'))
    game_proxy = TcpRelay(('127.0.0.1', args.game_port))
    pack_proxy = TcpRelay(('127.0.0.1', args.pack_port))
    config = server / 'config/rivet-server.toml'
    config.write_text(config.read_text().replace('enabled = false\n# Требовать', 'enabled = true\n# Требовать', 1).replace('port = 25567', f'port = {args.pack_port}').replace('publicPort = 25567', f'publicPort = {args.pack_port}'))
    config.write_text(config.read_text().replace('routes = []', f'routes = [{{ host = "127.0.0.1", port = {pack_proxy.port}, gameHost = "localhost" }}]'))
    pack = server / 'rivet/pack'
    (pack / 'mods').mkdir(parents=True)
    (pack / 'config').mkdir()
    with zipfile.ZipFile(pack / 'mods/combined-addon.jar', 'w') as jar:
        jar.writestr('META-INF/neoforge.mods.toml', 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="combined_addon"\nversion="1.0.0"\ndisplayName="Combined update fixture"\n')
    (pack / 'config/preview.toml').write_text('personal=false\n')
    (pack / 'pack.toml').write_text('[[configs]]\npath="config/preview.toml"\nupdate="missing"\n')
    env = os.environ.copy()
    env.pop('MOD_CLASSES', None)
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in env else 'java'
    with (work / 'server.log').open('w') as output:
        process = subprocess.Popen(command(work, java, 'server'), cwd=server, env=env, stdin=subprocess.PIPE, stdout=output, stderr=subprocess.STDOUT, text=True)
        try:
            wait_for(work / 'server.log', 'Done (', process)
            process.stdin.write('rivet pack publish\n'); process.stdin.flush()
            wait_for(work / 'server.log', 'Сборка опубликована', process)
            for route, profile, scale in itertools.product(args.routes, args.profiles, args.scales):
                if route == "fallback":
                    pack_proxy.close()
                proxy_bytes = pack_proxy.bytes
                address = f"127.0.0.1:{args.game_port}" if route == "direct" else f"localhost:{game_proxy.port}"
                game = work / f'client-{route}-{profile}-{scale}'
                (game / 'mods').mkdir(parents=True)
                (game / 'config').mkdir()
                (game / 'mods/rivet-old.jar').write_bytes(old_fixture(bundle, args.version))
                shutil.copy2(args.fixtures, game / 'mods')
                (game / 'config/preview.toml').write_text('personal=true\n')
                (game / 'options.txt').write_text(f'onboardAccessibility:false\nlang:ru_ru\nguiScale:{scale}\npauseOnLostFocus:false\n')
                (game / 'rivet').mkdir(exist_ok=True)
                (game / 'rivet/ui-theme.json').write_text(json.dumps({'theme': 'ivory' if profile == 'light' else 'golden-dark'}))
                (game / 'rivet/accessibility.json').write_text(json.dumps({'contrast': profile == 'contrast'}))
                client_env = dict(env, RIVET_COMBINED_SIGNED=str(bool(args.signed_release)).lower(), RIVET_COMBINED_VERSION=args.version, RIVET_COMBINED_ARTIFACT=str(bundle), RIVET_COMBINED_ADDRESS=address, RIVET_COMBINED_SCALE=str(scale))
                phases = [('install', 'RIVET_COMBINED_INSTALL_OK'), ('connect', 'RIVET_COMBINED_RECONNECT_OK')]
                if args.alias_reconnect:
                    phases.append(('alias', 'RIVET_COMBINED_RECONNECT_OK'))
                for phase, marker in phases:
                    if phase == 'alias':
                        client_env['RIVET_COMBINED_ADDRESS'] = f'localhost:{game_proxy.port}' if route == 'direct' else f'127.0.0.1:{args.game_port}'
                    print(f'Running {route} {profile} GUI {scale}: {phase}', flush=True)
                    log = work / f'client-{route}-{profile}-{scale}-{phase}.log'
                    with log.open('w') as client_log:
                        client_command = command(work, java, 'client')
                        if args.prefer_ipv6:
                            # After VM args (which set 'system'), before the main-class args.
                            client_command.insert(-1, '-Djava.net.preferIPv6Addresses=true')
                        client = subprocess.Popen(client_command + ['--username', 'RivetMapTest', '--width', '1280', '--height', '1024'], cwd=game, env=dict(client_env, RIVET_COMBINED_FLOW=phase), stdout=client_log, stderr=subprocess.STDOUT)
                        try:
                            result = client.wait(timeout=180)
                            verify(log, marker)
                            if result:
                                raise RuntimeError('Client exit ' + str(result))
                        finally:
                            stop(client)
                    if phase == 'install':
                        deadline = time.monotonic() + 60
                        while (game / 'rivet/pending.json').exists():
                            if time.monotonic() > deadline:
                                raise RuntimeError('Helper did not finish: ' + (game / 'rivet/runtime/helper.log').read_text())
                            time.sleep(1)
                        installed = game / 'mods' / bundle.name
                        assert installed.read_bytes() == bundle.read_bytes(), 'Wrong installed core bytes'
                        assert not (game / 'mods/rivet-old.jar').exists(), 'Old core remains'
                        assert (game / 'mods/combined-addon.jar').read_bytes() == (pack / 'mods/combined-addon.jar').read_bytes()
                        journals = list((game / 'rivet/transactions').glob('*/journal.json'))
                        assert len(journals) == 1, 'Combined update must use one transaction'
                        assert json.loads(journals[0].read_text())['status'] == 'COMMITTED'
                        state = json.loads((game / 'rivet/state.json').read_text())
                        assert state['coreVersion'] == args.version and state['packHash']
                        assert 'mods/' + bundle.name not in state['ownership']
                        print(f'{profile} GUI {scale}: one committed transaction; exact bundle and pack hashes verified', flush=True)
                assert len(list((game / 'rivet/transactions').glob('*/journal.json'))) == 1, 'Reconnect created another transaction'
                if route == 'proxy':
                    assert pack_proxy.bytes > proxy_bytes, 'Pack proxy was not used'
                print(f'RIVET_ROUTE_OK route={route} proxyBytes={pack_proxy.bytes - proxy_bytes}', flush=True)
            print('RIVET_COMBINED_UPDATE_OK', flush=True)
        finally:
            stop(process)
            game_proxy.close()
            pack_proxy.close()


if __name__ == '__main__':
    main()
