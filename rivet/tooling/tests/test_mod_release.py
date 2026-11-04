import hashlib
import io
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location("mod_release", Path(__file__).resolve().parents[1] / "release.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class ModRelease(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "gradle.properties").write_text("rivetVersion=1.0.0\nneoVersion=21.1.250\n")
        subprocess.run(["git", "init", str(self.root)], check=True, capture_output=True)
        self.artifact = self.root / "tested"
        self.artifact.mkdir()
        with zipfile.ZipFile(self.artifact / "rivet-1.0.0-mc1.21.1-neoforge.jar", "w") as jar:
            game_data = io.BytesIO()
            with zipfile.ZipFile(game_data, "w") as game:
                game.writestr("rivet/protocols.json", json.dumps(dict(pack=1, auth=3, menu=2, helper=1)))
            jar.writestr("rivet/game.jar", game_data.getvalue())

    def test_protocol_change_requires_new_major(self):
        previous = dict(version="1.2.3", protocols=dict(pack=1, auth=3, menu=2, helper=1))
        current = dict(version="1.3.0", protocols=dict(previous["protocols"]))
        m.validate_protocol_change(current, previous)
        current["protocols"]["auth"] = 4
        with self.assertRaisesRegex(ValueError, "major"):
            m.validate_protocol_change(current, previous)
        current["version"] = "2.0.0"
        m.validate_protocol_change(current, previous)

    def test_artifact_hashes_are_reproducible(self):
        files = m.package(self.root, self.artifact, self.root / "out")
        self.assertFalse((self.root / "out/template.zip").exists())
        first = files[1].read_bytes()
        m.package(self.root, self.artifact, self.root / "out")
        self.assertEqual(first, files[1].read_bytes())
        for line in files[2].read_text().splitlines():
            digest, name = line.split("  ")
            self.assertEqual(digest, hashlib.sha256((files[2].parent / name).read_bytes()).hexdigest())

    def test_missing_bundle_is_rejected(self):
        with self.assertRaises(ValueError):
            m.package(self.root, self.root / "absent", self.root / "out")

    def test_published_version_is_never_overwritten(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo", GITHUB_SHA="a"*40), patch.object(m, "run", side_effect=["a"*40, json.dumps([[dict(tag_name="v1.0.0", draft=False)]])]) as command:
            m.publish(self.root, self.artifact, self.root / "out")
            self.assertEqual(command.call_count, 2)
            self.assertFalse((self.root / "out").exists())

    def test_tag_for_another_commit_is_rejected(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="owner/repo", GITHUB_SHA="a"*40), patch.object(m, "run", side_effect=["a"*40, "[[]]", "tag", "", "other"]):
            with self.assertRaisesRegex(ValueError, "another commit"):
                m.publish(self.root, self.artifact, self.root / "out")

    def test_invalid_version_is_rejected(self):
        (self.root / "gradle.properties").write_text("rivetVersion=../unsafe\n")
        with self.assertRaises(ValueError):
            m.version(self.root)


class Publishing(unittest.TestCase):
    def setUp(self):
        ModRelease.setUp(self)
        notes = self.root / "rivet/RELEASE_NOTES.md"
        notes.parent.mkdir()
        notes.write_text("Release text")
        self.existing = []
        self.uploaded = {}
        self.commands = []
        self.corrupt = False
        self.fail_upload = False
        sleep = patch.object(m.time, "sleep")
        sleep.start()
        self.addCleanup(sleep.stop)

    def command(self, *args):
        self.commands.append(args)
        if args[:2] == ("git", "rev-parse"): return "a"*40
        if args[:2] == ("git", "ls-remote"): return ""
        if args[:2] == ("gh", "api"): return json.dumps([self.existing])
        if args[:3] == ("gh", "release", "create"):
            self.existing = [dict(tag_name="v1.0.0", draft=True, target_commitish="a"*40)]
            self.assertEqual((self.root / "rivet/RELEASE_NOTES.md").read_text(), "Release text")
            self.assertIn("--notes-file", args)
        elif args[:3] == ("gh", "release", "upload"):
            if self.fail_upload: raise RuntimeError("interrupted upload")
            for path in args[4:args.index("--repo")]:
                p = Path(path); self.uploaded[p.name] = p.read_bytes()
        elif args[:3] == ("gh", "release", "download"):
            name = args[args.index("--pattern") + 1]
            target = Path(args[args.index("--dir") + 1]) / name
            target.write_bytes(b"damaged" if self.corrupt else self.uploaded[name])
        elif args[:3] == ("gh", "release", "edit"):
            self.existing[0]["draft"] = False
        return ""

    def publish(self):
        with patch.dict(os.environ, GITHUB_REPOSITORY="abrosdaniel/rivet", GITHUB_SHA="a"*40), patch.object(m, "run", side_effect=self.command):
            m.publish(self.root, self.artifact, self.root / "out")

    def test_publishes_only_after_all_assets_verified(self):
        self.publish()
        self.assertEqual(len(self.uploaded), 3)
        self.assertEqual(self.commands[-1][:3], ("gh", "release", "edit"))
        core = json.loads(self.uploaded["core.json"])
        from jsonschema import Draft202012Validator
        schema = Path(__file__).resolve().parents[2] / "core/src/main/resources/rivet/schemas/v1/core-release.schema.json"
        Draft202012Validator(json.loads(schema.read_text())).validate(core)
        artifact = core["artifacts"][0]
        jar = self.uploaded["rivet-1.0.0-mc1.21.1-neoforge.jar"]
        self.assertEqual(artifact["sha256"], hashlib.sha256(jar).hexdigest())
        self.assertEqual(artifact["size"], len(jar))

    def test_interrupted_upload_resumes_existing_draft(self):
        self.fail_upload = True
        with self.assertRaises(RuntimeError): self.publish()
        self.assertFalse(any(c[:3] == ("gh", "release", "edit") for c in self.commands))
        self.fail_upload = False
        self.publish()
        self.assertEqual(sum(c[:3] == ("gh", "release", "create") for c in self.commands), 1)
        self.assertEqual(self.commands[-1][:3], ("gh", "release", "edit"))

    def test_bad_download_never_publishes(self):
        self.corrupt = True
        with self.assertRaises(ValueError): self.publish()
        self.assertFalse(any(c[:3] == ("gh", "release", "edit") for c in self.commands))

    def fail_once(self, operation, applied=False):
        original = self.command
        failed = False
        def command(*args):
            nonlocal failed
            if args[:3] == ("gh", "release", operation) and not failed:
                failed = True
                if applied:
                    original(*args)
                else:
                    self.commands.append(args)
                raise subprocess.CalledProcessError(1, args, stderr="HTTP 502: Server Error")
            return original(*args)
        self.command = command

    def test_502_before_create_retries_then_verifies_assets(self):
        self.fail_once("create")
        self.publish()
        self.assertEqual(2, sum(c[:3] == ("gh", "release", "create") for c in self.commands))
        self.assertEqual(3, len(self.uploaded))
        self.assertFalse(self.existing[0]["draft"])

    def test_lost_create_response_resumes_without_duplicate(self):
        self.fail_once("create", applied=True)
        self.publish()
        self.assertEqual(1, sum(c[:3] == ("gh", "release", "create") for c in self.commands))
        self.assertFalse(self.existing[0]["draft"])

    def test_interrupted_asset_upload_retries_matching_draft(self):
        self.fail_once("upload", applied=True)
        self.publish()
        self.assertEqual(2, sum(c[:3] == ("gh", "release", "upload") for c in self.commands))
        self.assertTrue(all("--clobber" in c for c in self.commands if c[:3] == ("gh", "release", "download")))

    def test_lost_publish_response_is_success_without_more_writes(self):
        self.fail_once("edit", applied=True)
        self.publish()
        self.assertEqual(1, sum(c[:3] == ("gh", "release", "edit") for c in self.commands))
        self.assertFalse(self.existing[0]["draft"])

    def test_unknown_commit_and_published_assets_are_not_overwritten(self):
        for different, published in [(True, False), (False, True)]:
            with self.subTest(different=different, published=published):
                release = dict(tag_name="v1.0.0", target_commitish=("b" if different else "a")*40, draft=not published)
                error = subprocess.CalledProcessError(1, (), stderr="HTTP 502: Server Error")
                with patch.object(m, "run", side_effect=[error, json.dumps([[release]])]) as command:
                    with self.assertRaises(ValueError):
                        m.change_release("owner/repo", "v1.0.0", "a"*40, "gh", "release", "upload", "v1.0.0")
                    self.assertEqual(2, command.call_count)

    def test_persistent_create_failure_is_bounded(self):
        error = subprocess.CalledProcessError(1, (), stderr="HTTP 503: unavailable")
        with patch.object(m, "run", side_effect=[error, "[[]]"] * m.ATTEMPTS) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                m.change_release("owner/repo", "v1.0.0", "a"*40, "gh", "release", "create", "v1.0.0")
            self.assertEqual(2*m.ATTEMPTS, command.call_count)


class ReadRetries(unittest.TestCase):
    def test_transient_read_recovers(self):
        error = subprocess.CalledProcessError(1, (), stderr="HTTP 502: Server Error")
        with patch.object(m.subprocess, "check_output", side_effect=[error, "[[]]"]) as command, patch.object(m.time, "sleep"):
            self.assertEqual("[[]]", m.run("gh", "api", "repos/owner/repo/releases"))
            self.assertEqual(2, command.call_count)

    def test_permission_and_validation_errors_do_not_retry(self):
        for status in [401, 403, 404, 422]:
            error = subprocess.CalledProcessError(1, (), stderr=f"HTTP {status}: permanent")
            with self.subTest(status=status), patch.object(m.subprocess, "check_output", side_effect=error) as command:
                with self.assertRaises(subprocess.CalledProcessError):
                    m.run("gh", "api", "repos/owner/repo/releases")
                self.assertEqual(1, command.call_count)

    def test_create_is_not_blindly_retried(self):
        error = subprocess.CalledProcessError(1, (), stderr="HTTP 502: Server Error")
        with patch.object(m.subprocess, "check_output", side_effect=error) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                m.run("gh", "release", "create", "v1.0.0")
            self.assertEqual(1, command.call_count)

    def test_mutating_api_is_not_blindly_retried(self):
        error = subprocess.CalledProcessError(1, (), stderr="HTTP 502: Server Error")
        with patch.object(m.subprocess, "check_output", side_effect=error) as command:
            with self.assertRaises(subprocess.CalledProcessError):
                m.run("gh", "api", "repos/owner/repo/releases", "--method=POST")
            self.assertEqual(1, command.call_count)

    def test_persistent_read_failure_is_bounded(self):
        error = subprocess.CalledProcessError(1, (), stderr="HTTP 502: Server Error")
        with patch.object(m.subprocess, "check_output", side_effect=error) as command, patch.object(m.time, "sleep") as sleep:
            with self.assertRaises(subprocess.CalledProcessError):
                m.run("gh", "api", "repos/owner/repo/releases")
            self.assertEqual(m.ATTEMPTS, command.call_count)
            self.assertEqual([5, 10, 20], [c.args[0] for c in sleep.call_args_list])
