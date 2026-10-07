"""Select fast checks or the complete release gate without querying external APIs."""
import argparse
import json
import re
import subprocess
from pathlib import Path


def plan(event, ref, default_branch, published, full_requested=True):
    default = ref == "refs/heads/" + default_branch
    candidate = default and not published and event in ("push", "workflow_dispatch")
    full = (event == "workflow_dispatch" and full_requested) or (event == "push" and candidate)
    return dict(full=full, publish=candidate and full,
                platforms=["ubuntu-latest", "windows-latest", "macos-latest"] if full else ["ubuntu-latest"],
                postgres=[17, 18] if full else [18])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--event", required=True)
    parser.add_argument("--ref", required=True)
    parser.add_argument("--default-branch", required=True)
    parser.add_argument("--full", choices=("true", "false"), default="true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    match = re.search(r"^rivetVersion=(\d+\.\d+\.\d+)$", (root / "gradle.properties").read_text(), re.M)
    if not match:
        raise ValueError("Missing release version")
    tagged = subprocess.check_output(["git", "-C", str(root), "tag", "--list", "v" + match[1]], text=True).strip()
    for key, value in plan(args.event, args.ref, args.default_branch, bool(tagged), args.full == "true").items():
        print(key + "=" + json.dumps(value, separators=(",", ":")))


if __name__ == "__main__":
    main()
