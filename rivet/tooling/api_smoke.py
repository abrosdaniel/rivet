"""Dedicated-server Java API discovery/lifecycle regression, with an isolated world and PostgreSQL."""
import argparse
import os
from pathlib import Path
import subprocess
import socket
import re
import shutil
import json
import struct
import zlib
from map_network import prepare, command, verify


def material_fixture(work):
    """Original tiny assets: verifies headless resource loading without Minecraft client assets."""
    assets = work / "server/rivet/terrain-resources/99-harness/assets/minecraft"
    for directory in ("blockstates", "models/block", "textures/block"):
        (assets / directory).mkdir(parents=True, exist_ok=True)
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
    for name, rgb in (("gold_block", (240, 190, 20)), ("diamond_block", (20, 190, 240))):
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 2, 2, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress((b"\0" + bytes((*rgb, 255)) * 2) * 2)) + chunk(b"IEND", b"")
        (assets / "textures/block" / (name + ".png")).write_bytes(png)
        model = {"textures": {"all": "minecraft:block/" + name}, "elements": [{"from": [0, 0, 0], "to": [16, 8, 16], "faces": {side: {"texture": "#all"} for side in ("up", "east", "south")}}]}
        (assets / "models/block" / (name + ".json")).write_text(json.dumps(model))
        (assets / "blockstates" / (name + ".json")).write_text(json.dumps({"variants": {"": {"model": "minecraft:block/" + name}}}))

    # Pin fluid texels as well: development runtimes may expose vanilla client assets.
    for name in ("water_still", "water_flow"):
        png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">2I5B", 2, 2, 8, 6, 0, 0, 0))
        png += chunk(b"IDAT", zlib.compress((b"\0" + bytes((255, 255, 255, 255)) * 2) * 2)) + chunk(b"IEND", b"")
        (assets / "textures/block" / (name + ".png")).write_bytes(png)
        (assets / "textures/block" / (name + ".png.mcmeta")).write_text('{"animation":{"frames":[0]}}')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("work", type=Path)
    parser.add_argument("--database-port", type=int, default=5432)
    parser.add_argument("--game-port", type=int, default=25587)
    parser.add_argument("--http-port", type=int, default=24465)
    parser.add_argument("--without-http", action="store_true")
    parser.add_argument("--luckperms", action="store_true")
    parser.add_argument("--saved-terrain", action="store_true")
    parser.add_argument("--scheduler-phase", choices=("1", "2"))
    args = parser.parse_args()
    if (args.saved_terrain or args.scheduler_phase) and args.without_http: parser.error("saved terrain requires enabled services")
    root = Path(__file__).resolve().parents[2]
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    init = prepare(root, work, args.database_port, "rivet_test", args.game_port)
    material_fixture(work)
    if args.luckperms:
        from map_network import download_lp
        mods = work / "server/mods"
        mods.mkdir(exist_ok=True)
        download_lp(mods / "luckperms.jar")
    config_path = work / "server/config/rivet-server.toml"
    config = config_path.read_text()
    before, http = config.split("[api.http]", 1)
    http = http.replace("enabled = false", "enabled = " + ("false" if args.without_http else "true"), 1)
    http = http.replace("origins = []", 'origins = ["https://native.example"]', 1)
    http = http.replace("port = 24464", "port = " + str(args.http_port), 1)
    config = before + "[api.http]" + http
    before, websocket = config.split("[api.websocket]", 1)
    websocket = websocket.replace("enabled = false", "enabled = " + ("false" if args.without_http else "true"), 1)
    websocket = websocket.replace("port = 24465", "port = " + str(args.http_port + 1), 1)
    config = before + "[api.websocket]" + websocket
    before, terrain = config.split("[api.terrain]", 1)
    terrain = terrain.replace("isometric = false", "isometric = " + ("false" if args.without_http else "true"), 1)
    terrain = terrain.replace("enabled = false", "enabled = " + ("false" if args.without_http else "true"), 1)
    if not args.saved_terrain and not args.scheduler_phase:
        terrain = terrain.replace('profiles = []', 'profiles = [{id="staff", projection="surface", hidden=["1,0,1,0"]}, {id="vault", projection="surface", hidden=[]}, {id="caves", projection="cave", minY=16, maxY=31, hidden=["1,0,1,0"]}]', 1)
    if args.saved_terrain:
        terrain = terrain.replace('profiles = []', 'profiles = [{id="caves", projection="cave", minY=64, maxY=79, hidden=["1025,1025,1025,1025"]}]', 1)
    if args.saved_terrain or args.scheduler_phase:
        terrain = terrain.replace('dimensions = ["minecraft:overworld"]', 'dimensions = ["minecraft:overworld", "minecraft:the_nether"]')
        for old, new in [("minX = -2048", "minX = -1040"), ("minZ = -2048", "minZ = 1024"), ("maxX = 2047", "maxX = 1055"), ("maxZ = 2047", "maxZ = 1039"), ("hidden = []", 'hidden = ["1024,1024,1024,1024"]')]:
            terrain = terrain.replace(old, new)
    else:
        # Keep the transport scenario independent of spawn generation backlog.
        for old, new in [("minX = -2048", "minX = 0"), ("minZ = -2048", "minZ = 0"), ("maxZ = 2047", "maxZ = 15")]:
            terrain = terrain.replace(old, new)
    config_path.write_text(before + "[api.terrain]" + terrain)
    with (work / "prepare.log").open("w") as log:
        subprocess.run([str(root / "gradlew"), "-I", str(init), "--project-cache-dir", str(work / "gradle"),
                        "-PrivetUiHarness", ":mod:classes", ":mod:compileUiHarnessJava", ":mod:prepareServerRun",
                        "--no-daemon", "--console=plain"], cwd=root, stdout=log, stderr=subprocess.STDOUT,
                       check=True, timeout=900)
    env = dict(os.environ, RIVET_API_SMOKE="false" if args.saved_terrain or args.scheduler_phase else "true", RIVET_SAVED_TERRAIN_SMOKE="true" if args.saved_terrain else "false", RIVET_TERRAIN_SCHEDULE_PHASE=args.scheduler_phase or "")
    env["MOD_CLASSES"] = os.pathsep.join("rivet%%" + str(work / "build" / part) for part in
        ("mod/classes/java/main", "mod/resources/main", "mod/classes/java/uiHarness",
         "core/classes/java/main", "core/resources/main", "api/classes/java/main"))
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if "JAVA_HOME" in env else "java"
    with (work / "server.log").open("w") as log:
        subprocess.run(command(work, java, "server"), cwd=work / "server", env=env,
                       stdout=log, stderr=subprocess.STDOUT, check=True, timeout=180)
    if args.scheduler_phase:
        verify(work / "server.log", "RIVET_TERRAIN_CRASH_CHECKPOINT_OK" if args.scheduler_phase == "1" else "RIVET_TERRAIN_RECOVERY_OK")
        if args.scheduler_phase == "2": verify(work / "server.log", "RIVET_TERRAIN_JOURNAL_GUARD_OK")
    elif args.saved_terrain:
        verify(work / "server.log", "RIVET_SAVED_TERRAIN_NATIVE_OK")
        verify(work / "server.log", "RIVET_SAVED_TERRAIN_WORKER_OK")
        verify(work / "server.log", "RIVET_ISOMETRIC_SAVED_OK")
        verify(work / "server.log", "RIVET_MATERIALS_SAVED_OK")
        verify(work / "server.log", "RIVET_CAVE_SAVED_OK")
    else:
        verify(work / "server.log", "RIVET_API_SMOKE_OK")
        verify(work / "server.log", "RIVET_API_KEYS_NATIVE_OK")
        verify(work / "server.log", "RIVET_EXTENDED_DOMAINS_NATIVE_OK")
        if args.luckperms:
            verify(work / "server.log", "RIVET_OFFLINE_STAFF_NATIVE_OK")
            verify(work / "server.log", "RIVET_SERVER_CONTROL_NATIVE_OK")
            verify(work / "server.log", "RIVET_COMMUNITY_DETAILS_NATIVE_OK")
            verify(work / "server.log", "RIVET_LIVE_MAP_OFFLINE_DENIED_OK")
            verify(work / "server.log", "RIVET_LIVE_MAP_NATIVE_OK")
            verify(work / "server.log", "RIVET_MAP_LAYERS_NATIVE_OK")
        if not args.without_http:
            verify(work / "server.log", "RIVET_REST_NATIVE_OK")
            verify(work / "server.log", "RIVET_WEBSOCKET_NATIVE_OK")
            verify(work / "server.log", "RIVET_BROWSER_TICKET_NATIVE_OK")
            verify(work / "server.log", "RIVET_PRIVATE_TERRAIN_NATIVE_OK")
            verify(work / "server.log", "RIVET_CAVE_NATIVE_OK")
            verify(work / "server.log", "RIVET_GEOMETRY_NATIVE_OK")
            verify(work / "server.log", "RIVET_TERRAIN_NATIVE_OK")
    try:
        connection = socket.create_connection(("127.0.0.1", args.http_port), timeout=1)
    except ConnectionRefusedError:
        pass
    else:
        connection.close()
        raise RuntimeError("REST listener remained open after server stop")
    try:
        connection = socket.create_connection(("127.0.0.1", args.http_port + 1), timeout=1)
    except ConnectionRefusedError:
        pass
    else:
        connection.close()
        raise RuntimeError("WebSocket listener remained open after server stop")
    if re.search(r"rvk_[a-f0-9]{32}\.[A-Za-z0-9_-]{43}", (work / "server.log").read_text(errors="replace")):
        raise RuntimeError("API secret leaked into server log")
    if args.scheduler_phase:
        shutil.copyfile(work / "server.log", work / ("server-scheduler-" + args.scheduler_phase + ".log"))
    print("RIVET_SAVED_TERRAIN_OK" if args.saved_terrain else "RIVET_API_NATIVE_OK", flush=True)


if __name__ == "__main__":
    main()
