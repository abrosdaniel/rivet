"""Isolated real-server map regressions; completion markers are mandatory, not exit code alone."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import uuid
from module_smoke import configuration

LP_URL = "https://cdn.modrinth.com/data/Vebnzrzj/versions/dKAPoPGd/LuckPerms-NeoForge-5.4.150.jar"
LP_SHA = "26de0e74d87b91acf3b71050ed1880a50ffd1809b3a1309a2e24bee5413193f27a5056e13b69d8f39085e28711244c02bc5fc329be13174abb520008a0ca9682"
SCENARIOS = (
    ("integrations", "RIVET_MAP_INTEGRATION_ADDRESS", "RivetMapTest", "RIVET_MAP_INTEGRATION_OK", 180),
    ("notifications", "RIVET_NOTIFICATION_ADDRESS", "RivetMapTest", "RIVET_NOTIFICATIONS_OK", 240),
    ("terrain", "RIVET_MAP_TERRAIN_ADDRESS", "RivetMapTest", "RIVET_TERRAIN_OK", 300),
    ("ingress", "RIVET_INGRESS_TEST_ADDRESS", "RivetMapGuest", "RIVET_INGRESS_OK", 180),
    ("publication", "RIVET_MAP_PUBLICATION_ADDRESS", "RivetMapTest", "RIVET_MAP_PUBLICATIONS_OK", 600),
    ("light", "RIVET_MAP_LIGHT_ADDRESS", "RivetMapTest", "RIVET_MAP_LIGHT_OK", 240),
    ("permissions", "RIVET_MAP_PERMISSION_TEST_ADDRESS", "RivetMapGuest", "RIVET_MAP_PERMISSION_OK", 240),
    ("teleport", "RIVET_MAP_TELEPORT_TEST_ADDRESS", "RivetMapTest", "RIVET_MAP_TELEPORT_OK", 240),
)

def verify(path, marker):
    text = Path(path).read_text(errors="replace")
    if marker not in text or re.search(r"RIVET_[A-Z0-9_]*FAILED\b", text):
        raise RuntimeError(f"Native scenario incomplete: {marker}; see {path}")

def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)

def download_lp(path):
    if path.exists() and hashlib.sha512(path.read_bytes()).hexdigest() == LP_SHA:
        return
    for attempt in range(4):
        try:
            with urllib.request.urlopen(LP_URL, timeout=45) as response:
                data = response.read(4 * 1024 * 1024 + 1)
            if hashlib.sha512(data).hexdigest() != LP_SHA:
                raise ValueError("LuckPerms fixture checksum mismatch")
            path.write_bytes(data)
            return
        except (OSError, TimeoutError):
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)

def prepare(root, work, db_port, db_name, game_port):
    server, client = work / "server", work / "client"
    for game in (server, client):
        (game / "config").mkdir(parents=True, exist_ok=True)
    (server / "mods").mkdir(exist_ok=True)
    template = (root / "rivet/core/src/main/resources/rivet-server.toml").read_text()
    config = configuration(template, "default", db_port).replace('database = "rivet_test"', 'database = ' + json.dumps(db_name))
    (server / "config/rivet-server.toml").write_text(config)
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(f"server-ip=127.0.0.1\nserver-port={game_port}\nonline-mode=false\ngamemode=creative\nlevel-seed=424242\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\n")
    player = "RivetMapTest"
    identity = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode()).digest(), version=3)
    (server / "ops.json").write_text(json.dumps([dict(uuid=str(identity), name=player, level=4, bypassesPlayerLimit=True)]))
    (client / "options.txt").write_text("onboardAccessibility:false\nlang:ru_ru\nguiScale:2\npauseOnLostFocus:false\n")
    base = str(work / "build").replace("\\", "\\\\").replace("'", "\\'")
    init = work / "runs.init.gradle"
    init.write_text("allprojects { layout.buildDirectory = file('" + base + "' + '/' + project.name) }\n" + """
gradle.projectsEvaluated {
 def mod = gradle.rootProject.project(':mod')
 mod.neoForge.runs.configureEach { run ->
  ['org.java-websocket:Java-WebSocket:1.6.0','org.apache.httpcomponents.client5:httpclient5:5.6.4','org.apache.httpcomponents.core5:httpcore5:5.4.3','org.apache.httpcomponents.core5:httpcore5-h2:5.4.3','com.zaxxer:HikariCP:6.3.3','org.postgresql:postgresql:42.7.14','org.bouncycastle:bcprov-jdk18on:1.85','org.bouncycastle:bcutil-jdk18on:1.85','org.bouncycastle:bcpkix-jdk18on:1.85'].each { dep -> mod.dependencies.add(run.additionalRuntimeClasspathConfiguration.name,dep) }
 }
}
""")
    return init

def command(work, java, side):
    dev = work / "build/mod/moddev"
    paths = (dev / (side + "LegacyClasspath.txt")).read_text().splitlines()
    paths += [str(dev / "artifacts/neoforge-21.1.250.jar")]
    cp = work / (side + "-classpath.args")
    cp.write_text('-cp\n"' + os.pathsep.join(paths).replace('\\', '\\\\').replace('"', '\\"') + '"\n')
    return [java, "@" + str(cp), "@" + str(dev / (side + "RunVmArgs.txt")), "@" + str(dev / (side + "RunProgramArgs.txt"))]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("work", type=Path)
    parser.add_argument("--database-port", type=int, default=5432)
    parser.add_argument("--database", default="rivet_test")
    parser.add_argument("--game-port", type=int, default=25579)
    parser.add_argument("--scenario", choices=[s[0] for s in SCENARIOS], action="append")
    parser.add_argument("--ingress-cycles", type=int, default=1)
    args = parser.parse_args()
    if not 1 <= args.ingress_cycles <= 50:
        parser.error("--ingress-cycles must be between 1 and 50")
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    init = prepare(root, work, args.database_port, args.database, args.game_port)
    with (work / "prepare.log").open("w") as log:
        subprocess.run([str(root / "gradlew"), "-I", str(init), "--project-cache-dir", str(work / "gradle"), "-PrivetUiHarness", ":mod:classes", ":mod:compileUiHarnessJava", ":mod:prepareClientRun", ":mod:prepareServerRun", "--no-daemon", "--console=plain"], cwd=root, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=900)
    download_lp(work / "server/mods/LuckPerms-NeoForge-5.4.150.jar")
    env = os.environ.copy()
    env["MOD_CLASSES"] = os.pathsep.join("rivet%%" + str(work / "build" / part) for part in ("mod/classes/java/main", "mod/resources/main", "mod/classes/java/uiHarness", "core/classes/java/main", "core/resources/main", "api/classes/java/main"))
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in env else "java"
    address = "127.0.0.1:" + str(args.game_port)
    with (work / "server.log").open("w") as log:
        server = subprocess.Popen(command(work, java, "server"), cwd=work / "server", env=dict(env, RIVET_MAP_PERMISSION_FIXTURE="true", RIVET_MAP_INTEGRATION_FIXTURE="true" if not args.scenario or "integrations" in args.scenario else "false"), stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 120
            while "Done (" not in (work / "server.log").read_text(errors="replace"):
                if server.poll() is not None or time.monotonic() > deadline:
                    raise RuntimeError("Test server did not start; see server.log")
                time.sleep(1)
            for name, variable, player, marker, limit in SCENARIOS:
                if args.scenario and name not in args.scenario:
                    continue
                print("Running " + name, flush=True)
                client_env = dict(env, RIVET_MAP_NETWORK_SMOKE="true", RIVET_UI_SCREENSHOTS="false")
                client_env[variable] = address
                if name == "ingress":
                    client_env["RIVET_INGRESS_CYCLES"] = str(args.ingress_cycles)
                    limit += 15 * (args.ingress_cycles - 1)
                with (work / (name + ".log")).open("w") as output:
                    client = subprocess.Popen(command(work, java, "client") + ["--username", player, "--width", "1280", "--height", "1024"], cwd=work / "client", env=client_env, stdout=output, stderr=subprocess.STDOUT)
                    try:
                        if client.wait(timeout=limit) != 0:
                            raise RuntimeError("Client failed: " + name)
                    finally:
                        stop(client)
                verify(work / (name + ".log"), marker)
            if not args.scenario or "permissions" in args.scenario:
                verify(work / "server.log", "RIVET_MAP_PERMISSION_SERVER_OK revoke")
            print("RIVET_MAP_NETWORK_OK", flush=True)
        finally:
            stop(server)

if __name__ == "__main__":
    main()
