"""Live authenticated roles and in-game rename. Requires an isolated, empty PostgreSQL database."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
from map_network import command, download_lp, prepare, stop, verify


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("work", type=Path)
    parser.add_argument("--database-port", type=int, default=5432)
    parser.add_argument("--database", default="rivet_test")
    parser.add_argument("--game-port", type=int, default=25585)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    init = prepare(root, work, args.database_port, args.database, args.game_port)
    config = work / "server/config/rivet-server.toml"
    config.write_text(re.sub(r'^mode = "false"$', 'mode = "base"', config.read_text(), flags=re.M))
    (work / "server/ops.json").write_text("[]")
    with (work / "prepare.log").open("w") as log:
        subprocess.run([str(root / "gradlew"), "-I", str(init), "--project-cache-dir", str(work / "gradle"), "-PrivetUiHarness", ":mod:classes", ":mod:compileUiHarnessJava", ":mod:prepareClientRun", ":mod:prepareServerRun", "--no-daemon", "--console=plain"], cwd=root, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
    download_lp(work / "server/mods/LuckPerms-NeoForge-5.4.150.jar")
    env = os.environ.copy()
    env["RIVET_ROLE_NETWORK_FIXTURE"] = "true"
    env["MOD_CLASSES"] = os.pathsep.join("rivet%%" + str(work / "build" / p) for p in ("mod/classes/java/main", "mod/resources/main", "mod/classes/java/uiHarness", "core/classes/java/main", "core/resources/main", "api/classes/java/main"))
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in env else "java"
    with (work / "server.log").open("w") as log:
        server = subprocess.Popen(command(work, java, "server"), cwd=work / "server", env=env, stdin=subprocess.PIPE, text=True, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 120
            while "Done (" not in (work / "server.log").read_text(errors="replace"):
                if server.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError("Role server did not start; see server.log")
                time.sleep(1)
            with (work / "roles.log").open("w") as output:
                client = subprocess.Popen(command(work, java, "client") + ["--username", "RivetMapTest", "--width", "1280", "--height", "1024"], cwd=work / "client", env=dict(env, RIVET_ROLE_NETWORK_ADDRESS=f"127.0.0.1:{args.game_port}"), stdout=output, stderr=subprocess.STDOUT)
                try:
                    if client.wait(timeout=600) != 0:
                        raise RuntimeError("Role client failed; see roles.log")
                finally:
                    stop(client)
            verify(work / "roles.log", "RIVET_ROLE_NETWORK_OK")
            verify(work / "server.log", "RIVET_AUTH_RENAME_PLAYER_OK")
            server.stdin.write("stop\n")
            server.stdin.flush()
            if server.wait(timeout=60) != 0:
                raise RuntimeError("Role server shutdown failed; see server.log")
            server_log = (work / "server.log").read_text(errors="replace")
            if "Exception in thread" in server_log or "NoClassDefFoundError" in server_log:
                raise RuntimeError("Role server shutdown exception; see server.log")
            print("RIVET_ROLE_NETWORK_COMPLETE", flush=True)
        finally:
            if server.poll() is None:
                try:
                    server.stdin.write("stop\n")
                    server.stdin.flush()
                    server.wait(timeout=30)
                except (BrokenPipeError, OSError, subprocess.TimeoutExpired):
                    stop(server)


if __name__ == "__main__":
    main()
