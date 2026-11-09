"""Select fast checks or the complete release gate without querying external APIs."""
import argparse
import json
import re
import subprocess
from pathlib import Path


CHECKS = ('java', 'postgres', 'ui', 'modules', 'map-network', 'permissions', 'trade', 'api-proxy', 'builder', 'envelope')


def affected(paths):
    if paths is None:
        return list(CHECKS)  # Missing diff must never skip verification.
    selected = set()
    for path in paths:
        if path.startswith(('docs/', 'ai/')) or path.endswith('.md') or path == 'mkdocs.yml':
            continue
        if path.startswith('rivet/tooling/tests/'):
            continue  # contracts always runs
        tooling = {'combined_update.py': 'builder',
                   'trade_network.py': 'trade', 'role_network.py': 'permissions',
                   'envelope_smoke.py': 'envelope', 'api_proxy_smoke.py': 'api-proxy'}
        name = Path(path).name
        if path.startswith('rivet/tooling/') and name in tooling:
            selected.update(('java', tooling[name]))
            continue
        # Shared infrastructure, unknown files and production changes conservatively
        # exercise every domain. Only isolated fixtures have narrower ownership.
        if '/uiHarness/' in path or '/compatibilityTest/' in path:
            domain = next((value for prefix, value in [('Trade', 'trade'), ('MapTerrain', 'map-network'),
                ('CombinedUpdate', 'builder'), ('ChatCompatibility', 'map-network')]
                if name.startswith(prefix)), None)
            if domain:
                selected.update(('java', domain))
                continue
        selected.update(CHECKS)
    return [name for name in CHECKS if name in selected]


def plan(event, ref, default_branch, published, full_requested=True, paths=None):
    default = ref == "refs/heads/" + default_branch
    candidate = default and not published and event in ("push", "workflow_dispatch")
    full = (event == "workflow_dispatch" and full_requested) or (event == "push" and candidate)
    checks = list(CHECKS) if full else affected(paths)
    return dict(full=full, publish=candidate and full, checks=checks,
                native=any(c in checks for c in ("ui", "modules", "map-network", "permissions", "trade", "builder", "envelope")),
                platforms=["ubuntu-latest", "windows-latest", "macos-latest"] if full else ["ubuntu-latest"],
                postgres=[17, 18] if full else [18])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--default-branch", required=True)
    parser.add_argument("--full", choices=("true", "false"), default="true")
    parser.add_argument("--base", help="Diff base from the event, never shell-interpolated")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    match = re.search(r"^rivetVersion=(\d+\.\d+\.\d+)$", (root / "gradle.properties").read_text(), re.M)
    if not match:
        raise ValueError("Missing release version")
    tagged = subprocess.check_output(["git", "-C", str(root), "tag", "--list", "v" + match[1]], text=True).strip()
    paths = None
    if args.base and re.fullmatch(r"[0-9a-f]{40}", args.base) and set(args.base) != {'0'}:
        try:
            paths = subprocess.check_output(['git', '-C', str(root), 'diff', '--name-only',
                args.base, 'HEAD', '--'], text=True).splitlines()
        except subprocess.CalledProcessError:
            pass
    for key, value in plan(args.event, args.ref, args.default_branch, bool(tagged), args.full == "true", paths).items():
        print(key + "=" + json.dumps(value, separators=(",", ":")))


if __name__ == "__main__":
    main()
