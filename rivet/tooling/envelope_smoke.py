"""Envelope UUID routing, rename and persisted mail across a real server restart."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request

from map_network import prepare, command, verify

URL = "https://cdn.modrinth.com/data/bINSbhYK/versions/E5ItQJuD/envelope-neoforge-1.21.1-0.8.1.jar"
SHA512 = "a3e6dd05e40dbf2e5e00b3bc9a43bfee9305e35de04396c8f2494f2d93ead785be18c4f81382c1fd84b16205b5dd0167cb9413ed00f7c7a45abfed2bfe185fd2"


def download(path):
    if path.exists() and hashlib.sha512(path.read_bytes()).hexdigest() == SHA512:
        return
    for attempt in range(4):
        try:
            with urllib.request.urlopen(URL, timeout=45) as response:
                data = response.read(16 * 1024 * 1024 + 1)
            if hashlib.sha512(data).hexdigest() != SHA512:
                raise ValueError("Envelope fixture checksum mismatch")
            path.write_bytes(data)
            return
        except (OSError, TimeoutError):
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("work", type=Path)
    parser.add_argument("--database-port", type=int, default=5432)
    parser.add_argument("--database", default="rivet_test")
    parser.add_argument("--game-port", type=int, default=25589)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    init = prepare(root, work, args.database_port, args.database, args.game_port)
    config = work / "server/config/rivet-server.toml"
    config.write_text(re.sub(r'^mode = "false"$', 'mode = "base"', config.read_text(), flags=re.M))
    download(work / "server/mods/envelope.jar")
    with (work / "prepare.log").open("w") as log:
        subprocess.run([str(root / "gradlew"), "-I", str(init), "--project-cache-dir", str(work / "gradle"), "-PrivetUiHarness", ":mod:classes", ":mod:compileUiHarnessJava", ":mod:prepareServerRun", "--no-daemon", "--console=plain"], cwd=root, stdout=log, stderr=subprocess.STDOUT, timeout=900, check=True)
    env = os.environ.copy()
    env["MOD_CLASSES"] = os.pathsep.join("rivet%%" + str(work / "build" / part) for part in ("mod/classes/java/main", "mod/resources/main", "mod/classes/java/uiHarness", "core/classes/java/main", "core/resources/main", "api/classes/java/main"))
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in env else "java"
    for auth, scenario in (("base", "true"), ("base", "resume"), ("false", "true"), ("false", "resume")):
        config.write_text(re.sub(r'^mode = "(?:false|base)"$', 'mode = "' + auth + '"', config.read_text(), flags=re.M))
        path = work / ("envelope-" + auth + "-" + scenario + ".log")
        with path.open("w") as log:
            result = subprocess.run(command(work, java, "server"), cwd=work / "server", env=dict(env, RIVET_ENVELOPE_TEST=scenario), stdout=log, stderr=subprocess.STDOUT, timeout=120)
        verify(path, "RIVET_ENVELOPE_OK" + (" restart=true" if scenario == "resume" else ""))
        result.check_returncode()
    print("RIVET_ENVELOPE_NATIVE_OK", flush=True)


if __name__ == "__main__":
    main()
