"""Published REST schemas and their JSON reference graph."""
import json
from pathlib import Path
import unittest
from jsonschema import Draft202012Validator
from referencing import Registry, Resource

CONTRACT = Path(__file__).resolve().parents[2] / "core/src/main/resources/rivet/api/openapi.json"


class RestContractTest(unittest.TestCase):
    def test_schema_references_and_response_contracts(self):
        document = json.loads(CONTRACT.read_text(encoding="utf-8"))
        self.assertEqual("3.1.0", document["openapi"])

        def walk(node):
            if isinstance(node, dict):
                if "$ref" in node:
                    reference = node["$ref"]
                    self.assertTrue(reference.startswith("#/"))
                    resolved = document
                    for part in reference[2:].split("/"):
                        resolved = resolved[part]
                for value in node.values():
                    walk(value)
            elif isinstance(node, list):
                for value in node:
                    walk(value)
        walk(document)
        for schema in document["components"]["schemas"].values():
            Draft202012Validator.check_schema(schema)
        root = dict(document, **{"$schema": "https://json-schema.org/draft/2020-12/schema"})
        uri = "urn:rivet:rest:v1"
        registry = Registry().with_resource(uri, Resource.from_contents(root))
        snapshot = {
            "cursor": {"stream": "8db36bb9-2a4c-49f7-9b59-3f731191ab77", "sequence": 0},
            "capturedAtEpochMillis": 1000,
            "status": {"onlinePlayers": 0, "maximumPlayers": 100, "maintenance": False,
                       "shutdownAtEpochMillis": 0, "capabilities": ["server.status"]},
        }
        validator = Draft202012Validator({"$ref": uri + "#/components/schemas/StatusSnapshot"}, registry=registry)
        validator.validate(snapshot)
        invalid = dict(snapshot, status=dict(snapshot["status"], onlinePlayers=-1))
        self.assertFalse(validator.is_valid(invalid))
        for route in document["paths"].values():
            self.assertTrue(set(route) <= {"get", "options", "post", "put", "delete"})
            for method, operation in route.items():
                with self.subTest(method=method, operation=operation.get("operationId")):
                    self.assertIn("204" if method == "options" else "200", operation["responses"])
                    self.assertIn("403" if method == "options" else "401", operation["responses"])
        operation_ids = [operation["operationId"] for route in document["paths"].values()
                         for operation in route.values() if "operationId" in operation]
        self.assertEqual(len(operation_ids), len(set(operation_ids)), "Duplicate generated client operation")
