"""Sign release metadata and publish static mirrors without exposing the signing key."""
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def execute(*args, **kwargs):
    return subprocess.check_output(args, stderr=subprocess.PIPE, **kwargs)


def key_file(directory):
    value = os.environ.get("RIVET_UPDATE_SIGNING_KEY", "")
    if not value:
        raise ValueError("Configure the RIVET_UPDATE_SIGNING_KEY repository secret before publishing")
    path = directory / "signing.pem"
    path.touch(mode=0o600)
    path.write_text(value)
    return path


def sign(value, key, directory):
    payload = json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode()
    source, signature = directory / "payload", directory / "signature"
    source.write_bytes(payload)
    execute("openssl", "pkeyutl", "-sign", "-rawin", "-inkey", str(key), "-in", str(source), "-out", str(signature))
    return {"payload": base64.b64encode(payload).decode(), "signature": base64.b64encode(signature.read_bytes()).decode()}


def verify(envelope, public, directory):
    payload = base64.b64decode(envelope["payload"], validate=True)
    source, signature = directory / "verify-payload", directory / "verify-signature"
    source.write_bytes(payload)
    signature.write_bytes(base64.b64decode(envelope["signature"], validate=True))
    execute("openssl", "pkeyutl", "-verify", "-pubin", "-keyform", "DER", "-inkey", str(public), "-rawin", "-in", str(source), "-sigfile", str(signature))
    return json.loads(payload)


def signed_release(root, output):
    descriptor = json.loads((output / "core.json").read_text())
    filename = Path(descriptor["artifacts"][0]["url"]).name
    mirrors = [f"https://raw.githubusercontent.com/abrosdaniel/rivet/updates/artifacts/{filename}",
               f"https://abrosdaniel.github.io/rivet/updates/artifacts/{filename}"]
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        key = key_file(directory)
        expected = base64.b64decode((root / "rivet/core/src/main/resources/rivet/update-key.txt").read_text().strip())
        actual = execute("openssl", "pkey", "-in", str(key), "-pubout", "-outform", "DER")
        if actual != expected:
            raise ValueError("Signing key does not match the client trust anchor")
        result = sign(dict(descriptor=descriptor, mirrors=mirrors), key, directory)
    target = output / "core.signed.json"
    target.write_text(json.dumps(result) + "\n")
    return target


def publish_static(root, output):
    """A new root commit bounds mirror history; the lease prevents concurrent replacement."""
    repository = os.environ.get("GITHUB_REPOSITORY", "abrosdaniel/rivet")
    if repository != "abrosdaniel/rivet":
        raise ValueError("Static source addresses belong to the official repository")
    with tempfile.TemporaryDirectory() as temporary:
        directory = Path(temporary)
        checkout = directory / "mirror"
        checkout.mkdir()
        execute("git", "init", "-q", str(checkout))
        def git(*args):
            return execute("git", "-C", str(checkout), *args).decode().strip()
        git("remote", "add", "origin", f"https://github.com/{repository}.git")
        refs = git("ls-remote", "origin", "refs/heads/updates")
        previous = refs.split()[0] if refs else ""
        if previous:
            git("fetch", "--depth=1", "origin", "updates")
            git("checkout", "--detach", "FETCH_HEAD")
        key = key_file(directory)
        public = directory / "public.der"
        public.write_bytes(base64.b64decode((root / "rivet/core/src/main/resources/rivet/update-key.txt").read_text().strip()))
        entries = []
        if (checkout / "catalog.json").exists():
            entries = verify(json.loads((checkout / "catalog.json").read_text()), public, directory)["releases"]
        current = json.loads((output / "core.signed.json").read_text())
        descriptor = verify(current, public, directory)["descriptor"]
        number = descriptor["version"]
        versions = {verify(entry, public, directory)["descriptor"]["version"]: entry for entry in entries}
        if number in versions and verify(versions[number], public, directory)["descriptor"] != descriptor:
            raise ValueError("Cannot replace an already signed release")
        versions[number] = current
        ordered = sorted(versions, key=lambda v: tuple(map(int, v.split("."))), reverse=True)[:100]
        (checkout / "catalog.json").write_text(json.dumps(sign(dict(schemaVersion=1, releases=[versions[v] for v in ordered]), key, directory)) + "\n")
        (checkout / "versions").mkdir(exist_ok=True)
        for version in ordered:
            (checkout / "versions" / f"{version}.json").write_text(json.dumps(versions[version]) + "\n")
        artifacts = checkout / "artifacts"
        artifacts.mkdir(exist_ok=True)
        name = Path(descriptor["artifacts"][0]["url"]).name
        shutil.copyfile(output / name, artifacts / name)
        keep = {Path(verify(versions[v], public, directory)["descriptor"]["artifacts"][0]["url"]).name for v in ordered[:10]}
        for path in artifacts.iterdir():
            if path.name not in keep:
                path.unlink()
        (checkout / ".nojekyll").touch()
        git("add", "--all")
        tree = git("write-tree")
        git("config", "user.name", "Rivet release")
        git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
        commit = git("commit-tree", tree, "-m", f"Official update catalog {number}")
        git("push", "origin", commit + ":refs/heads/updates", "--force-with-lease=refs/heads/updates:" + previous)


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    signed_release(Path.cwd(), args.output)
    if args.publish:
        publish_static(Path.cwd(), args.output)
