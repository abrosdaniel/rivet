"""Prepare an isolated packaged-server lifecycle check, then verify its log."""
import argparse
import re
import shutil
from pathlib import Path


def configuration(template, mode, port):
    changes = {"database": {"port": str(port), "database": '"rivet_test"', "username": '"rivet_test"',
                             "password": '""', "passwordEnv": '"RIVET_TEST_DB_PASSWORD"', "sslMode": '"disable"'},
               "auth": {"mode": '"base"' if mode == "base" else '"false"'}}
    if mode == "disabled":
        changes.update(skins={"enabled": "false"}, chat={"enabled": "false"})
    lines = template.splitlines()
    section = ""
    for n, line in enumerate(lines):
        if line.startswith("["):
            section = line[1:-1]
        elif "=" in line and not line.lstrip().startswith("#"):
            key = line.split("=", 1)[0].strip()
            if key in changes.get(section, {}):
                lines[n] = key + " = " + changes[section][key]
    return "\n".join(lines) + "\n"


def verify(log):
    text = Path(log).read_text(errors="replace")
    if "RIVET_MODULE_START_OK" not in text or "RIVET_MODULE_STOP_OK" not in text:
        raise ValueError("Module lifecycle did not complete; see " + str(log))
    if re.search(r"RIVET_[A-Z0-9_]*FAILED\b", text):
        raise ValueError("Module lifecycle failed; see " + str(log))
    print("Verified packaged-server module startup and shutdown")


def prepare(root, game, mode, port):
    version = re.search(r"^rivetVersion=(.+)$", (root / "gradle.properties").read_text(), re.M)[1]
    game.mkdir(parents=True, exist_ok=True)
    (game / "mods").mkdir(exist_ok=True)
    (game / "config").mkdir(exist_ok=True)
    shutil.copyfile(root / f"rivet/mod/build/libs/rivet-{version}-mc1.21.1-neoforge.jar", game / "mods/rivet.jar")
    shutil.copyfile(root / "rivet/mod/build/compatibility-tests/rivet-compatibility-tests.jar", game / "mods/rivet-tests.jar")
    template = (root / "rivet/core/src/main/resources/rivet-server.toml").read_text()
    (game / "config/rivet-server.toml").write_text(configuration(template, mode, port))
    (game / "eula.txt").write_text("eula=true\n")
    (game / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=25579\nonline-mode=false\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\n")
    # Gradle strings use escaped backslashes on Windows; these runs use Unix runners.
    escaped = str(game.resolve()).replace("\\", "\\\\").replace("'", "\\'")
    (game / "server.init.gradle").write_text("gradle.projectsEvaluated { gradle.rootProject.project(':mod').neoForge.runs.named('server') { gameDirectory = new File('" + escaped + "') } }\n")


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="action", required=True)
    setup = commands.add_parser("prepare")
    setup.add_argument("mode", choices=("default", "disabled", "base"))
    setup.add_argument("game", type=Path)
    setup.add_argument("--port", type=int, default=5432)
    check = commands.add_parser("verify")
    check.add_argument("log", type=Path)
    args = parser.parse_args()
    if args.action == "verify":
        verify(args.log)
    else:
        prepare(Path(__file__).resolve().parents[2], args.game, args.mode, args.port)


if __name__ == "__main__":
    main()
