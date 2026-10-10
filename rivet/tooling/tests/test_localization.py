"""Catalog consistency and literal UI coverage, without launching Minecraft."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "mod/src/main/resources/assets/rivet/lang"
LEXER = re.compile(r'''//[^\n]*|/\*[\s\S]*?\*/|'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*"''')


def catalog(path):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise AssertionError(f"Duplicate key: {key}")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


class LocalizationTest(unittest.TestCase):
    def test_language_keys_and_parameters_match(self):
        ru = catalog(ASSETS / "ru_ru.json")
        en = catalog(ASSETS / "en_us.json")
        self.assertEqual(ru.keys(), en.keys())
        pattern = r"%(?:\d+\$)?[sdif]"
        for key, value in ru.items():
            with self.subTest(key=key):
                self.assertEqual(re.findall(pattern,value),re.findall(pattern,en[key]))
                self.assertEqual(bool(value[:1].isspace()),bool(en[key][:1].isspace()))
                self.assertEqual(bool(value[-1:].isspace()),bool(en[key][-1:].isspace()))
                self.assertNotRegex(en[key],r"[А-Яа-яЁё]")

    def test_client_literal_references_exist_and_no_russian_ui_literals(self):
        entries=catalog(ASSETS / "en_us.json")
        for path in (ROOT / "mod/src/main/java/dev/abros/rivet/client").glob("*.java"):
            source=path.read_text(encoding="utf-8")
            for key in re.findall(r'Client\.(?:text|tr)\("([^"]+)"',source):
                # Prefixes used for dynamically composed keys are checked at their call sites.
                if key.endswith("."):
                    continue
                self.assertTrue("rivet."+key in entries,f"Missing {key} in {path.name}")
            for match in LEXER.finditer(source):
                if match.group().startswith('"'):
                    self.assertNotRegex(match.group(),r"[А-Яа-яЁё]",str(path))

    def test_shared_messages_match_minecraft_resources(self):
        for locale in ("ru_ru","en_us"):
            shared=catalog(ROOT / f"core/src/main/resources/rivet/i18n/{locale}.json")
            minecraft=catalog(ASSETS / f"{locale}.json")
            for key,value in shared.items():
                self.assertEqual(value,minecraft[key],key)
        shared=catalog(ROOT / "core/src/main/resources/rivet/i18n/en_us.json")
        for path in ROOT.glob("*/src/main/java/**/*.java"):
            for key in re.findall(r'(?:Messages.text|LocalizedText.key)\("([^"]+)"\)',path.read_text(encoding="utf-8")):
                self.assertTrue(key in shared,f"Missing {key} in {path.name}")

    def test_ci_harness_selectors_use_localized_keys(self):
        entries=catalog(ASSETS / "en_us.json")
        names=("ApprovedUiHarness","HudUiHarness","NativeUiHarness","UiFlowHarness",
               "AdaptiveUiHarness","ReorderUiHarness","TabLayoutHarness","AuditFixesUiHarness",
               "ReleaseCorrectionsHarness")
        for name in names:
            path=ROOT / f"mod/src/uiHarness/java/dev/abros/rivet/client/{name}.java"
            source=path.read_text(encoding="utf-8")
            for key in re.findall(r'Client\.(?:text|tr)\("([^"\n]+)"',source):
                self.assertIn("rivet."+key,entries,f"Missing {key} in {name}")
            # Fixture content may be Russian. Actions selecting actual UI must not be.
            for selector in re.findall(r'(?:press|adminTab)\([^;\n]*?\)',source):
                self.assertNotRegex(selector,r'[А-Яа-яЁё]',name+": "+selector)
            for comparison in re.findall(r'getMessage\(\)\.getString\(\)\.(?:equals|contains|startsWith)\("[^"\n]*"\)',source):
                self.assertNotRegex(comparison,r'[А-Яа-яЁё]',name+": "+comparison)
