"""Real native-container exchange with two identities, offline replies and a restart.

Requires a prepared isolated work directory (map_network.prepare) and compiled
uiHarness classes. Never points at an installed client or a production world.
"""
import argparse
from ci_runtime import build_or_restore
import os
import json
import hashlib
import uuid
import re
from pathlib import Path
import subprocess
import time
from map_network import command, prepare, stop, verify

STAGES = [('seed', 'RivetTradeB'), ('offer', 'RivetTradeA'),
          ('counter', 'RivetTradeB'), ('draftLeave', 'RivetTradeA'), ('draftObserver', 'RivetTradeB'), ('draftReturn', 'RivetTradeA'), ('edit', 'RivetTradeA'),
          ('complete', 'RivetTradeB'), ('claimA', 'RivetTradeA'),
          ('claimB', 'RivetTradeB'), ('resume', 'RivetTradeB'), ('cancel', 'RivetTradeA'), ('components', 'RivetTradeA'), ('gift', 'RivetTradeA'), ('giftAccept', 'RivetTradeB'), ('fullResult', 'RivetTradeB'), ('rapid', 'RivetTradeA'), ('pages', 'RivetTradeA'), ('pageControls', 'RivetTradeA'), ('counts', 'RivetTradeA'), ('cursorResult', 'RivetTradeA'), ('drops', 'RivetTradeA'), ('cursorDisconnect', 'RivetTradeA'), ('cursorReconnect', 'RivetTradeA'), ('notices', 'RivetTradeA'), ('cards', 'RivetTradeA'), ('admin', 'RivetTradeStaff'), ('frozenOwner', 'RivetTradeA'), ('adminResume', 'RivetTradeStaff'), ('adminCancel', 'RivetTradeStaff'), ('adminReturn', 'RivetTradeA'), ('adminReturnB', 'RivetTradeB'), ('adminDenied', 'RivetTradeGuest')]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('work', type=Path)
    parser.add_argument('--port', type=int, default=25589)
    parser.add_argument('--stage', choices=[s[0] for s in STAGES], action='append')
    parser.add_argument('--review', action='store_true')
    parser.add_argument('--review-admin', action='store_true')
    parser.add_argument('--review-capes', action='store_true')
    parser.add_argument('--review-hd', action='store_true')
    parser.add_argument('--prepare', action='store_true', help='Create an isolated fixture and compile it')
    parser.add_argument('--database-port', type=int, default=5432)
    parser.add_argument('--database', default='rivet_test')
    parser.add_argument('--hd-check', action='store_true', help='Verify HD upload and rendering after trades')
    args = parser.parse_args()
    review = args.review or args.review_admin or args.review_capes or args.review_hd
    work = args.work.resolve()
    if args.prepare:
        root = Path(__file__).resolve().parents[2]
        if (work / 'server/server.properties').exists():
            raise ValueError('Preparation requires a new work directory')
        work.mkdir(parents=True, exist_ok=True)
        init = prepare(root, work, args.database_port, args.database, args.port)
        config = work / 'server/config/rivet-server.toml'
        text = config.read_text()
        for name, value in [('maxOutgoing', 100), ('maxPerPlayer', 8), ('maxCapesPerPlayer', 8), ('maxResolution', 1024), ('maxCapeResolution', 1024)]:
            text = re.sub(r'^' + name + r' = \d+$', name + ' = ' + str(value), text, flags=re.M)
        config.write_text(text)
        operators = []
        for name in ('RivetTradeA', 'RivetTradeStaff'):
            identity = uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + name).encode()).digest(), version=3)
            operators.append(dict(uuid=str(identity), name=name, level=4, bypassesPlayerLimit=True))
        (work / 'server/ops.json').write_text(json.dumps(operators))
        build_or_restore(root, work, init)
    env = dict(os.environ, RIVET_TRADE_FIXTURE='true')
    env['MOD_CLASSES'] = os.pathsep.join('rivet%%' + str(work / 'build' / part) for part in (
        'mod/classes/java/main', 'mod/resources/main', 'mod/classes/java/uiHarness',
        'core/classes/java/main', 'core/resources/main', 'api/classes/java/main'))
    java = str(Path(env['JAVA_HOME']) / 'bin/java') if 'JAVA_HOME' in env else 'java'
    server = None
    def start_server(suffix):
        log = work / ('server-' + suffix + '.log')
        with log.open('w') as stream:
            process = subprocess.Popen(command(work, java, 'server'), cwd=work / 'server', env=env, stdout=stream, stderr=subprocess.STDOUT)
        deadline = time.monotonic() + 150
        while 'Done (' not in log.read_text(errors='replace'):
            if process.poll() is not None or time.monotonic() > deadline:
                stop(process)
                raise RuntimeError('Trade server failed: ' + str(log))
            time.sleep(1)
        return process
    try:
        server = start_server('first')
        stages = [('hdReview', 'RivetTradeA')] if args.review_hd else [('capeReview', 'RivetTradeA')] if args.review_capes else [('adminReview', 'RivetTradeStaff')] if args.review_admin else [('review', 'RivetTradeA')] if args.review else [s for s in STAGES if not args.stage or s[0] in args.stage]
        if args.hd_check and not review:
            stages = stages + [('hdReview', 'RivetTradeA'), ('hdReload', 'RivetTradeA')]
        for stage, player in stages:
            if stage in ('claimA', 'resume', 'draftReturn', 'cursorReconnect', 'frozenOwner', 'hdReload'):
                if stage == 'resume':
                    server.kill()
                    server.wait(timeout=20)
                else:
                    stop(server)
                server = start_server('restart')
            print('Trade scenario: ' + stage, flush=True)
            client_env = dict(env, RIVET_TRADE_ADDRESS='127.0.0.1:' + str(args.port), RIVET_TRADE_SCENARIO=stage)
            if stage in ('hdReview', 'hdReload') and not review:
                client_env['RIVET_HD_EXIT'] = '1'
                if stage == 'hdReload':
                    client_env['RIVET_TRADE_SCENARIO'] = 'hdReview'
                    client_env['RIVET_HD_RELOAD'] = '1'
                client_env['RIVET_HD_CAPTURES'] = str(work / 'hd-captures')
            log = work / (stage + '.log')
            with log.open('w') as stream:
                client = subprocess.Popen(command(work, java, 'client') + ['--username', player, '--width', '1280', '--height', '1024'], cwd=work / 'client', env=client_env, stdout=stream, stderr=subprocess.STDOUT)
                try:
                    client.wait(timeout=None if review else 180)
                finally:
                    stop(client)
            if review:
                marker = {'review': 'RIVET_TRADE_REVIEW_READY', 'adminReview': 'RIVET_TRADE_ADMIN_REVIEW_READY', 'capeReview': 'RIVET_CAPE_REVIEW_READY', 'hdReview': 'RIVET_HD_APPEARANCE_OK'}[stage]
                verify(log, marker)
            else:
                verify(log, 'RIVET_HD_RELOAD_OK width=1024' if stage == 'hdReload' else 'RIVET_HD_APPEARANCE_OK width=1024 capeWidth=1024' if stage == 'hdReview' else 'RIVET_TRADE_OK ' + stage)
        print('RIVET_TRADE_NETWORK_OK', flush=True)
    finally:
        if server is not None:
            stop(server)

if __name__ == '__main__':
    main()
